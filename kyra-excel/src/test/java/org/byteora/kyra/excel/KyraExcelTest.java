package org.byteora.kyra.excel;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KyraExcelTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldCreateWorkbookWithoutDefaultSheet() {
        ExcelWorkbook workbook = KyraExcel.create();

        assertTrue(workbook.sheets().isEmpty());
    }

    @Test
    void shouldWriteAndReadBasicWorkbookParts() throws Exception {
        ExcelWorkbook workbook = KyraExcel.create();
        ExcelSheet sheet = workbook.sheet("Sheet 1");
        assertEquals(1, workbook.sheets().size());
        CellStyle header = CellStyle.builder()
                .font(FontStyle.builder()
                        .bold(true)
                        .color("#FFFFFF")
                        .build())
                .fillColor("#4472C4")
                .horizontalAlignment("center")
                .verticalAlignment("center")
                .wrapText(true)
                .build();

        sheet.value("A1", "Name")
                .value("B1", "Amount")
                .value("C1", "Currency")
                .value("A2", "Book")
                .value("B2", 12.5D)
                .formula("B3", "SUM(B2:B2)")
                .merge("A1:B1");
        sheet.cell("A1").style(header);
        sheet.cell("B2").numberFormat("#,##0.00");

        Path output = tempDir.resolve("sample.xlsx");
        workbook.save(output);

        ExcelWorkbook loaded = KyraExcel.open(output);
        ExcelSheet loadedSheet = loaded.sheet("Sheet 1");

        assertEquals("Name", loadedSheet.cell("A1").value());
        assertEquals("Book", loadedSheet.cell("A2").value());
        assertEquals(12.5D, (Double) loadedSheet.cell("B2").value(), 0.001D);
        assertEquals("SUM(B2:B2)", loadedSheet.cell("B3").formula().orElseThrow());
        assertEquals("A1:B1", loadedSheet.mergedRegions().getFirst().reference());
        assertEquals("#,##0.00", loadedSheet.cell("B2").style().numberFormat());
        assertTrue(loadedSheet.cell("A1").style().font().bold());
        assertEquals("FF4472C4", loadedSheet.cell("A1").style().fillColor());
    }

    @Test
    void shouldNotWriteSkippedMergeCellsIntoPackageParts() throws Exception {
        ExcelWorkbook workbook = KyraExcel.create();
        ExcelSheet sheet = workbook.sheet("Sheet1");
        sheet.value("A1", "Name")
                .value("B1", "Amount")
                .merge("A1:B1");

        Path output = tempDir.resolve("merged.xlsx");
        workbook.save(output);

        try (ZipFile zipFile = new ZipFile(output.toFile())) {
            assertNull(zipFile.getEntry("xl/sharedStrings.xml"));
            String worksheet = new String(
                    zipFile.getInputStream(zipFile.getEntry("xl/worksheets/sheet1.xml")).readAllBytes());
            assertTrue(worksheet.contains("Name"));
            assertTrue(!worksheet.contains("Amount"));
            assertTrue(worksheet.contains("<c r=\"B1\"/>"));
        }
    }

    @Test
    void shouldWriteEmptyPlaceholdersForMergedCells() throws Exception {
        ExcelWorkbook workbook = KyraExcel.create();
        workbook.sheet("Sheet1")
                .value("A1", "Name")
                .merge("A1:B1");

        Path output = tempDir.resolve("merged-placeholder.xlsx");
        workbook.save(output);

        try (ZipFile zipFile = new ZipFile(output.toFile())) {
            String worksheet = new String(
                    zipFile.getInputStream(zipFile.getEntry("xl/worksheets/sheet1.xml")).readAllBytes());
            assertTrue(worksheet.contains("<c r=\"B1\"/>"));
        }
    }

    @Test
    void shouldWriteCachedValueForSimpleSumFormula() throws Exception {
        ExcelWorkbook workbook = KyraExcel.create();
        ExcelSheet sheet = workbook.sheet("Sheet1");
        sheet.value("B2", 12.5D)
                .formula("B3", "SUM(B2:B2)");

        Path output = tempDir.resolve("formula.xlsx");
        workbook.save(output);

        try (ZipFile zipFile = new ZipFile(output.toFile())) {
            String worksheet = new String(
                    zipFile.getInputStream(zipFile.getEntry("xl/worksheets/sheet1.xml")).readAllBytes());
            assertTrue(worksheet.contains("<c r=\"B3\"><f>SUM(B2:B2)</f><v>12.5</v></c>"));
        }
    }

    @Test
    void shouldUseBuiltInNumberFormatIds() throws Exception {
        ExcelWorkbook workbook = KyraExcel.create();
        workbook.sheet("Sheet1").cell("A1")
                .value(12.5D)
                .numberFormat("#,##0.00");

        Path output = tempDir.resolve("number-format.xlsx");
        workbook.save(output);

        try (ZipFile zipFile = new ZipFile(output.toFile())) {
            String styles = new String(zipFile.getInputStream(zipFile.getEntry("xl/styles.xml")).readAllBytes());
            assertTrue(styles.contains("numFmtId=\"4\""));
            assertTrue(!styles.contains("<numFmts"));
        }
    }

    @Test
    void shouldSupportModernRowCellSyntax() throws Exception {
        ExcelWorkbook workbook = KyraExcel.create();
        ExcelSheet sheet = workbook.sheet("Sheet1");
        sheet.width("A", 18)
                .width("B", 12)
                .height(0, 24);
        sheet.row(0)
                .height(24)
                .cell(
                Cell.text("Name").style(
                        Style.bold(),
                        Style.fontColor("#FFFFFF"),
                        Style.fillColor("#4472C4"),
                        Style.align(HorizontalAlign.CENTER, VerticalAlign.CENTER),
                        Style.wrapText()),
                Cell.decimal(0.111D, 2),
                Cell.percent(0.12D),
                Cell.currency(19.9D),
                Cell.date(LocalDate.of(2026, 5, 20)),
                Cell.time(LocalTime.of(9, 30, 15)),
                Cell.datetime(LocalDateTime.of(2026, 5, 20, 9, 30, 15)),
                Cell.formula("SUM(B1:B1)", 0.111D)
                );
        sheet.row(1).cell(
                Cell.text("Book"),
                Cell.date("2026-05-20", "yyyy-MM-dd", "yyyy-mm-dd"),
                Cell.time("09:30:15", "HH:mm:ss", "hh:mm:ss"),
                Cell.datetime("2026-05-20 09:30:15", "yyyy-MM-dd HH:mm:ss", "yyyy-mm-dd hh:mm:ss")
        );
        sheet.row("M1").cell(Cell.text("Test"));
        sheet.merge("M1:P1");

        Path output = tempDir.resolve("modern.xlsx");
        workbook.save(output);
        ExcelWorkbook loaded = KyraExcel.open(output);
        ExcelSheet loadedSheet = loaded.sheet("Sheet1");

        assertEquals("Name", loadedSheet.cell("A1").value());
        assertEquals("Book", loadedSheet.cell("A2").value());
        assertEquals("0.00", loadedSheet.cell("B1").style().numberFormat());
        assertEquals("0.00%", loadedSheet.cell("C1").style().numberFormat());
        assertEquals("\"¥\"#,##0.00", loadedSheet.cell("D1").style().numberFormat());
        assertEquals("yyyy-mm-dd", loadedSheet.cell("E1").style().numberFormat());
        assertEquals("hh:mm:ss", loadedSheet.cell("F1").style().numberFormat());
        assertEquals("yyyy-mm-dd hh:mm:ss", loadedSheet.cell("G1").style().numberFormat());
        assertEquals("SUM(B1:B1)", loadedSheet.cell("H1").formula().orElseThrow());

        try (ZipFile zipFile = new ZipFile(output.toFile())) {
            String worksheet = new String(
                    zipFile.getInputStream(zipFile.getEntry("xl/worksheets/sheet1.xml")).readAllBytes());
            assertTrue(worksheet.contains("<col min=\"1\" max=\"1\" width=\"18\" customWidth=\"1\"/>"));
            assertTrue(worksheet.contains("<col min=\"2\" max=\"2\" width=\"12\" customWidth=\"1\"/>"));
            assertTrue(worksheet.contains("<row r=\"1\" ht=\"24\" customHeight=\"1\">"));
        }
    }

    @Test
    void rowWithoutArgumentsShouldAppendNextRow() throws Exception {
        ExcelWorkbook workbook = KyraExcel.create();
        ExcelSheet sheet = workbook.sheet("Sheet1");

        sheet.row().cell(Cell.text("header"));
        sheet.row().cell(Cell.text("first"));
        sheet.row().cell(Cell.text("second"));

        Path output = tempDir.resolve("append-row.xlsx");
        workbook.save(output);

        ExcelWorkbook loaded = KyraExcel.open(output);
        ExcelSheet loadedSheet = loaded.sheet("Sheet1");
        assertEquals("header", loadedSheet.cell("A1").value());
        assertEquals("first", loadedSheet.cell("A2").value());
        assertEquals("second", loadedSheet.cell("A3").value());
    }

    @Test
    void shouldReportRowsCreatedByExplicitRowAccess() {
        ExcelWorkbook workbook = KyraExcel.create();
        ExcelSheet sheet = workbook.sheet("Sheet1");

        assertTrue(!sheet.hasRow(4));
        sheet.row(4);
        assertTrue(sheet.hasRow(4));

        assertTrue(!sheet.hasRow("C10"));
        sheet.row("C10");
        assertTrue(sheet.hasRow("A10"));
    }

    @Test
    void rowWithoutArgumentsShouldReadLoadedRowsInOrder() throws Exception {
        ExcelWorkbook workbook = KyraExcel.create();
        ExcelSheet sheet = workbook.sheet("Sheet1");
        sheet.row().cell(Cell.text("header"));
        sheet.row().cell(Cell.text("first"));

        Path output = tempDir.resolve("append-loaded-row.xlsx");
        workbook.save(output);

        ExcelWorkbook loaded = KyraExcel.open(output);
        ExcelSheet loadedSheet = loaded.sheet("Sheet1");
        assertTrue(loadedSheet.hasRow(0));
        assertTrue(loadedSheet.hasRow(1));
        assertTrue(!loadedSheet.hasRow(2));

        SheetRow firstRow = loadedSheet.row();
        SheetRow secondRow = loadedSheet.row();
        assertEquals(1, firstRow.row());
        assertEquals(2, secondRow.row());
        assertTrue(firstRow.hasCell(0));
        assertTrue(firstRow.hasCell("A"));
        assertEquals("header", firstRow.value(0));
        assertEquals("header", firstRow.value("A"));
        assertEquals("first", secondRow.cell(0).value());
        SheetRow createdRow = loadedSheet.row();
        assertEquals(3, createdRow.row());
        assertTrue(loadedSheet.hasRow(2));

        createdRow.cell(Cell.text("second"));
        assertEquals("header", loadedSheet.cell("A1").value());
        assertEquals("first", loadedSheet.cell("A2").value());
        assertEquals("second", loadedSheet.cell("A3").value());
    }
}
