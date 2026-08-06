package com.portiq.service;

import com.portiq.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks an uploaded file before anything reads it.
 *
 * <p>Both import endpoints previously took whatever arrived: {@code importCsv} branched on the
 * filename alone, and {@code importImage} base64-encoded the bytes straight into a request to an
 * external model. Neither looked at what the file actually was.
 *
 * <p>Three checks, because each catches what the others miss. The extension is what the routing
 * logic keys on, so it has to be constrained or a {@code .csv} name can steer an arbitrary payload
 * down the spreadsheet path. The declared content type is trivially spoofed, so it is checked but
 * never trusted alone. And the leading bytes are what the parser will actually see, so a file
 * claiming to be a spreadsheet has to look like one - which is the check that stops a renamed
 * archive or executable reaching Apache POI's parser, historically a rich source of CVEs.
 */
@Service
public class UploadValidator {

    /** The kinds of upload this application accepts, each with its own allowlist. */
    public enum Kind {
        SPREADSHEET("a CSV or Excel file"),
        IMAGE("a PNG, JPEG, GIF or WebP image");

        private final String description;

        Kind(String description) {
            this.description = description;
        }
    }

    private static final Set<String> SPREADSHEET_EXTENSIONS = Set.of("csv", "txt", "xlsx", "xls");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private static final Set<String> SPREADSHEET_CONTENT_TYPES = Set.of(
            "text/csv", "text/plain", "application/csv", "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/octet-stream");
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp");

    /** ZIP local file header - the container format of .xlsx. */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    /** OLE2 compound document header - the container format of legacy .xls. */
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF_MAGIC = {0x47, 0x49, 0x46, 0x38};
    private static final byte[] RIFF_MAGIC = {0x52, 0x49, 0x46, 0x46};

    @Value("${app.security.upload.max-bytes:10485760}")
    private long maxBytes;

    public void validate(MultipartFile file, Kind kind) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("No file was uploaded.");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidRequestException(
                    "That file is too large. The limit is " + (maxBytes / (1024 * 1024)) + "MB.");
        }

        String filename = safeFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        Set<String> allowedExtensions = kind == Kind.IMAGE ? IMAGE_EXTENSIONS : SPREADSHEET_EXTENSIONS;
        if (!allowedExtensions.contains(extension)) {
            throw new InvalidRequestException("Upload " + kind.description + ". '"
                    + (extension.isEmpty() ? filename : "." + extension) + "' is not accepted.");
        }

        String contentType = file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        Set<String> allowedTypes = kind == Kind.IMAGE ? IMAGE_CONTENT_TYPES : SPREADSHEET_CONTENT_TYPES;
        if (!contentType.isEmpty() && !allowedTypes.contains(contentType)) {
            throw new InvalidRequestException("Upload " + kind.description
                    + ". The file was sent as '" + contentType + "'.");
        }

        checkContent(file, kind, extension);
    }

    /**
     * Verifies the bytes match the claimed kind.
     *
     * <p>Binary formats are checked by magic number. Plain CSV has none, so it is checked for the
     * absence of a NUL byte instead - text does not contain one, and every binary format that
     * matters does within the first few hundred bytes.
     */
    private void checkContent(MultipartFile file, Kind kind, String extension) {
        byte[] head = readHead(file);
        if (head.length == 0) {
            throw new InvalidRequestException("That file is empty.");
        }

        if (kind == Kind.IMAGE) {
            boolean recognised = startsWith(head, PNG_MAGIC)
                    || startsWith(head, JPEG_MAGIC)
                    || startsWith(head, GIF_MAGIC)
                    || startsWith(head, RIFF_MAGIC); // WebP is RIFF-framed
            if (!recognised) {
                throw new InvalidRequestException("That file is not a readable image.");
            }
            return;
        }

        if (extension.equals("xlsx")) {
            if (!startsWith(head, ZIP_MAGIC)) {
                throw new InvalidRequestException("That file is named .xlsx but is not an Excel workbook.");
            }
            return;
        }
        if (extension.equals("xls")) {
            if (!startsWith(head, OLE2_MAGIC) && !startsWith(head, ZIP_MAGIC)) {
                throw new InvalidRequestException("That file is named .xls but is not an Excel workbook.");
            }
            return;
        }

        for (byte b : head) {
            if (b == 0) {
                throw new InvalidRequestException("That file is not readable text. Upload a CSV or Excel file.");
            }
        }
    }

    private byte[] readHead(MultipartFile file) {
        try (var stream = file.getInputStream()) {
            return stream.readNBytes(512);
        } catch (IOException e) {
            throw new InvalidRequestException("That file could not be read.");
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reduces a client-supplied filename to its last path segment.
     *
     * <p>Nothing here writes the upload to disk today, so this is not currently a traversal fix -
     * it is insurance for the day something does, and it keeps a name like
     * {@code ../../etc/passwd.csv} out of error messages and logs.
     */
    public static String safeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "upload";
        }
        String name = original.trim();
        for (String separator : List.of("/", "\\")) {
            int index = name.lastIndexOf(separator);
            if (index >= 0) {
                name = name.substring(index + 1);
            }
        }
        name = name.replaceAll("[\\p{Cntrl}]", "");
        return name.isBlank() ? "upload" : name;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1
                ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
