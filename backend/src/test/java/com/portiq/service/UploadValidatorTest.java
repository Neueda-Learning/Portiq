package com.portiq.service;

import com.portiq.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidatorTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};
    private static final byte[] ZIP_BYTES = {'P', 'K', 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
    private static final byte[] ELF_BYTES = {0x7F, 'E', 'L', 'F', 0x02, 0x01, 0x01, 0x00};

    private UploadValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UploadValidator();
        ReflectionTestUtils.setField(validator, "maxBytes", 1024L * 1024);
    }

    @Test
    void acceptsAPlainCsv() {
        MockMultipartFile file = new MockMultipartFile("file", "holdings.csv", "text/csv",
                "ticker,name,type,quantity,price\nTCS.NS,TCS,STOCK,5,3500".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> validator.validate(file, UploadValidator.Kind.SPREADSHEET))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsARealXlsx() {
        MockMultipartFile file = new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ZIP_BYTES);

        assertThatCode(() -> validator.validate(file, UploadValidator.Kind.SPREADSHEET))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnExecutableRenamedToLookLikeASpreadsheet() {
        // The whole point of the magic-byte check: the name and the declared type both say
        // "spreadsheet", and the bytes are a Linux binary that would reach Apache POI's parser.
        MockMultipartFile file = new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ELF_BYTES);

        assertThatThrownBy(() -> validator.validate(file, UploadValidator.Kind.SPREADSHEET))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not an Excel workbook");
    }

    @Test
    void rejectsBinaryContentClaimingToBeCsv() {
        MockMultipartFile file = new MockMultipartFile("file", "holdings.csv", "text/csv", ELF_BYTES);

        assertThatThrownBy(() -> validator.validate(file, UploadValidator.Kind.SPREADSHEET))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not readable text");
    }

    @Test
    void rejectsADisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "payload.svg", "image/svg+xml",
                "<svg onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(file, UploadValidator.Kind.IMAGE))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void acceptsAPng() {
        MockMultipartFile file = new MockMultipartFile("file", "statement.png", "image/png", PNG_BYTES);

        assertThatCode(() -> validator.validate(file, UploadValidator.Kind.IMAGE))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnImageWhoseBytesAreNotAnImage() {
        MockMultipartFile file = new MockMultipartFile("file", "statement.png", "image/png",
                "not really a png".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(file, UploadValidator.Kind.IMAGE))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not a readable image");
    }

    @Test
    void rejectsAFileOverTheSizeLimit() {
        ReflectionTestUtils.setField(validator, "maxBytes", 10L);
        MockMultipartFile file = new MockMultipartFile("file", "holdings.csv", "text/csv",
                "ticker,name,type,quantity,price\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(file, UploadValidator.Kind.SPREADSHEET))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void rejectsAnEmptyUpload() {
        MockMultipartFile file = new MockMultipartFile("file", "holdings.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file, UploadValidator.Kind.SPREADSHEET))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void reducesAFilenameToItsLastSegment() {
        assertThat(UploadValidator.safeFilename("../../../etc/passwd.csv")).isEqualTo("passwd.csv");
        assertThat(UploadValidator.safeFilename("C:\\Windows\\System32\\evil.csv")).isEqualTo("evil.csv");
        assertThat(UploadValidator.safeFilename("  ")).isEqualTo("upload");
        assertThat(UploadValidator.safeFilename(null)).isEqualTo("upload");
    }

    @Test
    void stripsControlCharactersFromAFilename() {
        assertThat(UploadValidator.safeFilename("holdings\r\nSECURITY: forged.csv"))
                .doesNotContain("\r")
                .doesNotContain("\n");
    }
}
