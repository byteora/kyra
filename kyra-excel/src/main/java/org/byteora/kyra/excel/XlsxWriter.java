package org.byteora.kyra.excel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class XlsxWriter {
    private XlsxWriter() {
    }

    static void write(ExcelWorkbook workbook, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypes(workbook));
            put(zip, "_rels/.rels", packageRelationships());
            put(zip, "docProps/core.xml", coreProperties());
            put(zip, "docProps/app.xml", appProperties(workbook));
            put(zip, "xl/workbook.xml", workbookXml(workbook));
            put(zip, "xl/_rels/workbook.xml.rels", workbookRelationships(workbook));
            put(zip, "xl/styles.xml", stylesXml(workbook.styles()));
            for (int i = 0; i < workbook.sheets().size(); i++) {
                put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(workbook.sheets().get(i)));
            }
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes(ExcelWorkbook workbook) {
        StringBuilder xml = new StringBuilder(xmlHeader())
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
                .append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
                .append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < workbook.sheets().size(); i++) {
            xml.append("<Override PartName=\"/xl/worksheets/sheet")
                    .append(i + 1)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return xml.append("</Types>").toString();
    }

    private static String packageRelationships() {
        return xmlHeader()
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
                + "</Relationships>";
    }

    private static String coreProperties() {
        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        return xmlHeader()
                + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
                + "xmlns:dcterms=\"http://purl.org/dc/terms/\" "
                + "xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<dc:creator>kyra-excel</dc:creator>"
                + "<cp:lastModifiedBy>kyra-excel</cp:lastModifiedBy>"
                + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:created>"
                + "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:modified>"
                + "</cp:coreProperties>";
    }

    private static String appProperties(ExcelWorkbook workbook) {
        StringBuilder titles = new StringBuilder();
        for (ExcelSheet sheet : workbook.sheets()) {
            titles.append("<vt:lpstr>")
                    .append(XmlSupport.escape(sheet.name()))
                    .append("</vt:lpstr>");
        }
        return xmlHeader()
                + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" "
                + "xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\">"
                + "<Application>Microsoft Excel</Application>"
                + "<DocSecurity>0</DocSecurity>"
                + "<ScaleCrop>false</ScaleCrop>"
                + "<HeadingPairs><vt:vector size=\"2\" baseType=\"variant\">"
                + "<vt:variant><vt:lpstr>Worksheets</vt:lpstr></vt:variant>"
                + "<vt:variant><vt:i4>" + workbook.sheets().size() + "</vt:i4></vt:variant>"
                + "</vt:vector></HeadingPairs>"
                + "<TitlesOfParts><vt:vector size=\"" + workbook.sheets().size() + "\" baseType=\"lpstr\">"
                + titles
                + "</vt:vector></TitlesOfParts>"
                + "<LinksUpToDate>false</LinksUpToDate>"
                + "<SharedDoc>false</SharedDoc>"
                + "<HyperlinksChanged>false</HyperlinksChanged>"
                + "<AppVersion>16.0300</AppVersion>"
                + "</Properties>";
    }

    private static String workbookXml(ExcelWorkbook workbook) {
        StringBuilder xml = new StringBuilder(xmlHeader())
                .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                .append("<bookViews><workbookView xWindow=\"0\" yWindow=\"0\" windowWidth=\"12000\" windowHeight=\"7200\"/></bookViews>")
                .append("<sheets>");
        for (int i = 0; i < workbook.sheets().size(); i++) {
            xml.append("<sheet name=\"")
                    .append(XmlSupport.escape(workbook.sheets().get(i).name()))
                    .append("\" sheetId=\"")
                    .append(i + 1)
                    .append("\" r:id=\"rId")
                    .append(i + 1)
                    .append("\"/>");
        }
        return xml.append("</sheets><calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/></workbook>").toString();
    }

    private static String workbookRelationships(ExcelWorkbook workbook) {
        StringBuilder xml = new StringBuilder(xmlHeader())
                .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        int relationshipId = 1;
        for (int i = 0; i < workbook.sheets().size(); i++) {
            xml.append("<Relationship Id=\"rId")
                    .append(relationshipId++)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i + 1)
                    .append(".xml\"/>");
        }
        xml.append("<Relationship Id=\"rId")
                .append(relationshipId)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return xml.append("</Relationships>").toString();
    }

    private static String sheetXml(ExcelSheet sheet) {
        addMergedShadowCells(sheet);
        List<ExcelCell> cells = sheet.cells().stream()
                .filter(cell -> cell.value() != null || cell.formula().isPresent() || isMergedShadowCell(sheet, cell))
                .sorted(Comparator.comparingInt(ExcelCell::row).thenComparingInt(ExcelCell::column))
                .toList();
        String dimension = cells.isEmpty() ? "A1" : "A1:" + cells.getLast().reference();
        Map<Integer, List<ExcelCell>> rows = new TreeMap<>();
        for (ExcelCell cell : cells) {
            rows.computeIfAbsent(cell.row(), ignored -> new ArrayList<>()).add(cell);
        }

        StringBuilder xml = new StringBuilder(xmlHeader())
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                .append("<dimension ref=\"").append(dimension).append("\"/>")
                .append("<sheetViews><sheetView tabSelected=\"1\" workbookViewId=\"0\"><selection activeCell=\"A1\" sqref=\"A1\"/></sheetView></sheetViews>")
                .append("<sheetFormatPr defaultRowHeight=\"15\"/>");
        appendColumns(xml, sheet);
        xml.append("<sheetData>");
        for (Map.Entry<Integer, List<ExcelCell>> row : rows.entrySet()) {
            appendRowStart(xml, sheet, row.getKey());
            for (ExcelCell cell : row.getValue()) {
                appendCell(xml, sheet, cell);
            }
            xml.append("</row>");
        }
        xml.append("</sheetData>");
        if (!sheet.mergedRegions().isEmpty()) {
            xml.append("<mergeCells count=\"").append(sheet.mergedRegions().size()).append("\">");
            for (CellRange range : sheet.mergedRegions()) {
                xml.append("<mergeCell ref=\"").append(range.reference()).append("\"/>");
            }
            xml.append("</mergeCells>");
        }
        return xml.append("</worksheet>").toString();
    }

    private static void appendColumns(StringBuilder xml, ExcelSheet sheet) {
        if (sheet.columnWidths().isEmpty()) {
            return;
        }
        xml.append("<cols>");
        for (Map.Entry<Integer, Double> entry : sheet.columnWidths().entrySet()) {
            xml.append("<col min=\"")
                    .append(entry.getKey())
                    .append("\" max=\"")
                    .append(entry.getKey())
                    .append("\" width=\"")
                    .append(formatDouble(entry.getValue()))
                    .append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols>");
    }

    private static void appendRowStart(StringBuilder xml, ExcelSheet sheet, int row) {
        xml.append("<row r=\"").append(row).append("\"");
        Double height = sheet.rowHeights().get(row);
        if (height != null) {
            xml.append(" ht=\"")
                    .append(formatDouble(height))
                    .append("\" customHeight=\"1\"");
        }
        xml.append(">");
    }

    private static void addMergedShadowCells(ExcelSheet sheet) {
        for (CellRange range : sheet.mergedRegions()) {
            for (int row = range.firstRow(); row <= range.lastRow(); row++) {
                for (int column = range.firstColumn(); column <= range.lastColumn(); column++) {
                    boolean topLeft = row == range.firstRow() && column == range.firstColumn();
                    if (!topLeft) {
                        sheet.cell(row, column);
                    }
                }
            }
        }
    }

    private static boolean isMergedShadowCell(ExcelSheet sheet, ExcelCell cell) {
        for (CellRange range : sheet.mergedRegions()) {
            boolean insideRange = cell.row() >= range.firstRow()
                    && cell.row() <= range.lastRow()
                    && cell.column() >= range.firstColumn()
                    && cell.column() <= range.lastColumn();
            boolean topLeft = cell.row() == range.firstRow() && cell.column() == range.firstColumn();
            if (insideRange && !topLeft) {
                return true;
            }
        }
        return false;
    }

    private static void appendCell(StringBuilder xml, ExcelSheet sheet, ExcelCell cell) {
        Object value = cell.value();
        xml.append("<c r=\"").append(cell.reference()).append("\"");
        if (cell.styleIndex() > 0) {
            xml.append(" s=\"").append(cell.styleIndex()).append("\"");
        }
        if (isMergedShadowCell(sheet, cell)) {
            xml.append("/>");
            return;
        }
        if (cell.formula().isEmpty() && value instanceof String) {
            xml.append(" t=\"inlineStr\"");
        } else if (cell.formula().isEmpty() && value instanceof Boolean) {
            xml.append(" t=\"b\"");
        }
        xml.append(">");
        cell.formula().ifPresent(formula -> xml.append("<f>").append(XmlSupport.escape(formula)).append("</f>"));
        if (cell.formula().isEmpty()) {
            appendPlainValue(xml, value);
        } else {
            Number cachedValue = value instanceof Number number
                    ? number
                    : evaluateFormula(sheet, cell.formula().orElseThrow());
            if (cachedValue != null) {
                xml.append("<v>").append(formatNumber(cachedValue)).append("</v>");
            }
        }
        xml.append("</c>");
    }

    private static void appendPlainValue(StringBuilder xml, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String string) {
            appendInlineString(xml, string);
        } else if (value instanceof Boolean bool) {
            xml.append("<v>").append(bool ? 1 : 0).append("</v>");
        } else if (value instanceof Number number) {
            xml.append("<v>").append(formatNumber(number)).append("</v>");
        } else {
            xml.append("<v>").append(XmlSupport.escape(value.toString())).append("</v>");
        }
    }

    private static void appendInlineString(StringBuilder xml, String value) {
        xml.append("<is><t");
        if (needsPreserveSpace(value)) {
            xml.append(" xml:space=\"preserve\"");
        }
        xml.append(">")
                .append(XmlSupport.escape(value))
                .append("</t></is>");
    }

    private static boolean needsPreserveSpace(String value) {
        return !value.isEmpty()
                && (Character.isWhitespace(value.charAt(0))
                || Character.isWhitespace(value.charAt(value.length() - 1)));
    }

    private static Number evaluateFormula(ExcelSheet sheet, String formula) {
        String normalized = formula.strip();
        if (normalized.regionMatches(true, 0, "SUM(", 0, 4) && normalized.endsWith(")")) {
            String rangeExpression = normalized.substring(4, normalized.length() - 1).strip();
            try {
                CellRange range = CellRange.parse(rangeExpression);
                double sum = 0D;
                boolean hasNumber = false;
                for (int row = range.firstRow(); row <= range.lastRow(); row++) {
                    for (int column = range.firstColumn(); column <= range.lastColumn(); column++) {
                        Object value = sheet.findCell(row, column).map(ExcelCell::value).orElse(null);
                        if (value instanceof Number number) {
                            sum += number.doubleValue();
                            hasNumber = true;
                        }
                    }
                }
                return hasNumber ? sum : null;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String formatNumber(Number number) {
        double value = number.doubleValue();
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return number.toString();
    }

    private static String formatDouble(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static String stylesXml(List<CellStyle> styles) {
        StyleTable table = StyleTable.from(styles);
        StringBuilder xml = new StringBuilder(xmlHeader())
                .append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        if (!table.customNumberFormats().isEmpty()) {
            xml.append("<numFmts count=\"").append(table.customNumberFormats().size()).append("\">");
            for (Map.Entry<String, Integer> entry : table.customNumberFormats().entrySet()) {
                xml.append("<numFmt numFmtId=\"")
                        .append(entry.getValue())
                        .append("\" formatCode=\"")
                        .append(XmlSupport.escape(entry.getKey()))
                        .append("\"/>");
            }
            xml.append("</numFmts>");
        }
        xml.append("<fonts count=\"").append(table.fonts().size()).append("\">");
        for (FontStyle font : table.fonts()) {
            xml.append("<font>");
            if (font.bold()) {
                xml.append("<b/>");
            }
            if (font.italic()) {
                xml.append("<i/>");
            }
            if (font.underline()) {
                xml.append("<u/>");
            }
            xml.append("<sz val=\"").append(formatFontSize(font.size())).append("\"/>");
            if (font.color() != null) {
                xml.append("<color rgb=\"").append(font.color()).append("\"/>");
            }
            xml.append("<name val=\"")
                    .append(XmlSupport.escape(font.name()))
                    .append("\"/><family val=\"2\"/></font>");
        }
        xml.append("</fonts>");
        xml.append("<fills count=\"").append(table.fills().size()).append("\">")
                .append("<fill><patternFill patternType=\"none\"/></fill>")
                .append("<fill><patternFill patternType=\"gray125\"/></fill>");
        for (int i = 2; i < table.fills().size(); i++) {
            xml.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"")
                    .append(table.fills().get(i))
                    .append("\"/><bgColor indexed=\"64\"/></patternFill></fill>");
        }
        xml.append("</fills>")
                .append("<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>")
                .append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
                .append("<cellXfs count=\"").append(styles.size()).append("\">");
        for (CellStyle style : styles) {
            int numFmtId = style.numberFormat() == null ? 0 : table.numberFormatId(style.numberFormat());
            int fontId = table.fonts().indexOf(style.font());
            int fillId = style.fillColor() == null ? 0 : table.fills().indexOf(style.fillColor());
            boolean hasAlignment = style.horizontalAlignment() != null || style.verticalAlignment() != null || style.wrapText();
            xml.append("<xf numFmtId=\"")
                    .append(numFmtId)
                    .append("\" fontId=\"")
                    .append(fontId)
                    .append("\" fillId=\"")
                    .append(fillId)
                    .append("\" borderId=\"0\" xfId=\"0\"");
            if (style.numberFormat() != null) {
                xml.append(" applyNumberFormat=\"1\"");
            }
            if (!style.font().equals(FontStyle.DEFAULT)) {
                xml.append(" applyFont=\"1\"");
            }
            if (style.fillColor() != null) {
                xml.append(" applyFill=\"1\"");
            }
            if (hasAlignment) {
                xml.append(" applyAlignment=\"1\"><alignment");
                if (style.horizontalAlignment() != null) {
                    xml.append(" horizontal=\"").append(XmlSupport.escape(style.horizontalAlignment())).append("\"");
                }
                if (style.verticalAlignment() != null) {
                    xml.append(" vertical=\"").append(XmlSupport.escape(style.verticalAlignment())).append("\"");
                }
                if (style.wrapText()) {
                    xml.append(" wrapText=\"1\"");
                }
                xml.append("/></xf>");
            } else {
                xml.append("/>");
            }
        }
        return xml.append("</cellXfs>")
                .append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
                .append("<dxfs count=\"0\"/><tableStyles count=\"0\" defaultTableStyle=\"TableStyleMedium2\" defaultPivotStyle=\"PivotStyleLight16\"/>")
                .append("</styleSheet>")
                .toString();
    }

    private static String xmlHeader() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    }

    private static String formatFontSize(double size) {
        if (size == Math.rint(size)) {
            return Long.toString((long) size);
        }
        return Double.toString(size);
    }

    private record StyleTable(List<FontStyle> fonts, List<String> fills, Map<String, Integer> customNumberFormats) {
        private static final Map<String, Integer> BUILT_IN_NUMBER_FORMATS = Map.ofEntries(
                Map.entry("0", 1),
                Map.entry("0.00", 2),
                Map.entry("#,##0", 3),
                Map.entry("#,##0.00", 4),
                Map.entry("0%", 9),
                Map.entry("0.00%", 10),
                Map.entry("0.00E+00", 11),
                Map.entry("# ?/?", 12),
                Map.entry("# ??/??", 13),
                Map.entry("m/d/yy", 14),
                Map.entry("d-mmm-yy", 15),
                Map.entry("d-mmm", 16),
                Map.entry("mmm-yy", 17),
                Map.entry("h:mm AM/PM", 18),
                Map.entry("h:mm:ss AM/PM", 19),
                Map.entry("h:mm", 20),
                Map.entry("h:mm:ss", 21),
                Map.entry("m/d/yy h:mm", 22),
                Map.entry("#,##0 ;(#,##0)", 37),
                Map.entry("#,##0 ;[Red](#,##0)", 38),
                Map.entry("#,##0.00;(#,##0.00)", 39),
                Map.entry("#,##0.00;[Red](#,##0.00)", 40),
                Map.entry("mm:ss", 45),
                Map.entry("[h]:mm:ss", 46),
                Map.entry("mmss.0", 47),
                Map.entry("##0.0E+0", 48),
                Map.entry("@", 49)
        );

        static StyleTable from(List<CellStyle> styles) {
            List<FontStyle> fonts = new ArrayList<>();
            List<String> fills = new ArrayList<>();
            Map<String, Integer> customNumberFormats = new LinkedHashMap<>();
            fonts.add(FontStyle.DEFAULT);
            fills.add(null);
            fills.add(null);
            int nextNumberFormatId = 164;
            for (CellStyle style : styles) {
                if (!fonts.contains(style.font())) {
                    fonts.add(style.font());
                }
                if (style.fillColor() != null && !fills.contains(style.fillColor())) {
                    fills.add(style.fillColor());
                }
                if (style.numberFormat() != null
                        && !BUILT_IN_NUMBER_FORMATS.containsKey(style.numberFormat())
                        && !customNumberFormats.containsKey(style.numberFormat())) {
                    customNumberFormats.put(style.numberFormat(), nextNumberFormatId++);
                }
            }
            return new StyleTable(fonts, fills, customNumberFormats);
        }

        int numberFormatId(String numberFormat) {
            Integer builtIn = BUILT_IN_NUMBER_FORMATS.get(numberFormat);
            if (builtIn != null) {
                return builtIn;
            }
            Integer custom = customNumberFormats.get(numberFormat);
            if (custom == null) {
                throw new IllegalStateException("Number format is not registered: " + numberFormat);
            }
            return custom;
        }
    }
}
