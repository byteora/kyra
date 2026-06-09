package org.byteora.kyra.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

final class XlsxReader {
    private XlsxReader() {
    }

    static ExcelWorkbook read(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return read(inputStream);
        }
    }

    static ExcelWorkbook read(InputStream inputStream) throws IOException {
        Map<String, byte[]> parts = readZip(inputStream);
        byte[] workbookPart = parts.get("xl/workbook.xml");
        if (workbookPart == null) {
            throw new IOException("Invalid xlsx file: missing xl/workbook.xml");
        }

        ExcelWorkbook workbook = new ExcelWorkbook();
        List<String> sharedStrings = parseSharedStrings(parts.get("xl/sharedStrings.xml"));
        Map<Integer, Integer> styleMap = registerStyles(workbook, parts.get("xl/styles.xml"));
        Map<String, String> relationships = parseRelationships(parts.get("xl/_rels/workbook.xml.rels"));
        Document workbookDocument = XmlSupport.parse(workbookPart);
        Element sheetsElement = XmlSupport.child(workbookDocument.getDocumentElement(), "sheets");
        if (sheetsElement == null) {
            throw new IOException("Invalid xlsx file: missing workbook sheets");
        }

        for (Element sheetElement : XmlSupport.children(sheetsElement, "sheet")) {
            String name = XmlSupport.attr(sheetElement, "name");
            String relationshipId = XmlSupport.attr(sheetElement, "r:id");
            String target = relationships.get(relationshipId);
            if (target == null || target.isBlank()) {
                continue;
            }
            String sheetPath = normalizeWorkbookTarget(target);
            byte[] sheetPart = parts.get(sheetPath);
            if (sheetPart == null) {
                continue;
            }
            ExcelSheet sheet = workbook.sheet(name);
            readSheet(sheet, sheetPart, sharedStrings, styleMap);
        }

        if (workbook.sheets().isEmpty()) {
            workbook.sheet("Sheet1");
        }
        return workbook;
    }

    private static Map<String, byte[]> readZip(InputStream inputStream) throws IOException {
        Map<String, byte[]> parts = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    zip.transferTo(output);
                    parts.put(entry.getName(), output.toByteArray());
                }
            }
        }
        return parts;
    }

    private static List<String> parseSharedStrings(byte[] part) {
        List<String> values = new ArrayList<>();
        if (part == null) {
            return values;
        }
        Document document = XmlSupport.parse(part);
        for (Element si : XmlSupport.children(document.getDocumentElement(), "si")) {
            StringBuilder value = new StringBuilder();
            for (Element text : XmlSupport.descendants(si, "t")) {
                value.append(XmlSupport.text(text));
            }
            values.add(value.toString());
        }
        return values;
    }

    private static Map<String, String> parseRelationships(byte[] part) {
        Map<String, String> relationships = new HashMap<>();
        if (part == null) {
            return relationships;
        }
        Document document = XmlSupport.parse(part);
        for (Element relationship : XmlSupport.children(document.getDocumentElement(), "Relationship")) {
            relationships.put(XmlSupport.attr(relationship, "Id"), XmlSupport.attr(relationship, "Target"));
        }
        return relationships;
    }

    private static void readSheet(
            ExcelSheet sheet,
            byte[] part,
            List<String> sharedStrings,
            Map<Integer, Integer> styleMap
    ) {
        Document document = XmlSupport.parse(part);
        for (Element cellElement : XmlSupport.descendants(document.getDocumentElement(), "c")) {
            String reference = XmlSupport.attr(cellElement, "r");
            if (reference.isBlank()) {
                continue;
            }
            ExcelCell cell = sheet.cell(reference);
            int sourceStyle = parseInt(XmlSupport.attr(cellElement, "s"), 0);
            cell.styleIndex(styleMap.getOrDefault(sourceStyle, 0));

            Element formula = XmlSupport.child(cellElement, "f");
            if (formula != null && XmlSupport.text(formula) != null && !XmlSupport.text(formula).isBlank()) {
                cell.formula(XmlSupport.text(formula));
            }

            String type = XmlSupport.attr(cellElement, "t");
            cell.value(readCellValue(cellElement, type, sharedStrings));
        }

        Element mergeCells = XmlSupport.child(document.getDocumentElement(), "mergeCells");
        if (mergeCells != null) {
            for (Element mergeCell : XmlSupport.children(mergeCells, "mergeCell")) {
                String range = XmlSupport.attr(mergeCell, "ref");
                if (!range.isBlank()) {
                    sheet.merge(range);
                }
            }
        }
    }

    private static Object readCellValue(Element cellElement, String type, List<String> sharedStrings) {
        if ("inlineStr".equals(type)) {
            Element inlineString = XmlSupport.child(cellElement, "is");
            if (inlineString == null) {
                return null;
            }
            StringBuilder value = new StringBuilder();
            for (Element text : XmlSupport.descendants(inlineString, "t")) {
                value.append(XmlSupport.text(text));
            }
            return value.toString();
        }
        Element valueElement = XmlSupport.child(cellElement, "v");
        String raw = XmlSupport.text(valueElement);
        if (raw == null) {
            return null;
        }
        return switch (type) {
            case "s" -> {
                int index = parseInt(raw, -1);
                yield index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : raw;
            }
            case "b" -> "1".equals(raw) || "true".equalsIgnoreCase(raw);
            case "str" -> raw;
            default -> parseNumber(raw);
        };
    }

    private static Map<Integer, Integer> registerStyles(ExcelWorkbook workbook, byte[] part) {
        Map<Integer, Integer> styleMap = new HashMap<>();
        styleMap.put(0, 0);
        if (part == null) {
            return styleMap;
        }
        Document document = XmlSupport.parse(part);
        Element root = document.getDocumentElement();
        Map<Integer, String> numberFormats = parseNumberFormats(XmlSupport.child(root, "numFmts"));
        List<FontStyle> fonts = parseFonts(XmlSupport.child(root, "fonts"));
        List<String> fills = parseFills(XmlSupport.child(root, "fills"));
        Element cellXfs = XmlSupport.child(root, "cellXfs");
        if (cellXfs == null) {
            return styleMap;
        }
        int index = 0;
        for (Element xf : XmlSupport.children(cellXfs, "xf")) {
            FontStyle font = getOrDefault(fonts, parseInt(XmlSupport.attr(xf, "fontId"), 0), FontStyle.DEFAULT);
            String fill = getOrDefault(fills, parseInt(XmlSupport.attr(xf, "fillId"), 0), null);
            int numberFormatId = parseInt(XmlSupport.attr(xf, "numFmtId"), 0);
            CellStyle.Builder builder = CellStyle.builder()
                    .font(font)
                    .fillColor(fill)
                    .numberFormat(numberFormats.getOrDefault(numberFormatId, builtInNumberFormat(numberFormatId)));
            Element alignment = XmlSupport.child(xf, "alignment");
            if (alignment != null) {
                builder.horizontalAlignment(nullIfBlank(XmlSupport.attr(alignment, "horizontal")))
                        .verticalAlignment(nullIfBlank(XmlSupport.attr(alignment, "vertical")))
                        .wrapText("1".equals(XmlSupport.attr(alignment, "wrapText"))
                                || "true".equalsIgnoreCase(XmlSupport.attr(alignment, "wrapText")));
            }
            styleMap.put(index, workbook.styleIndex(builder.build()));
            index++;
        }
        return styleMap;
    }

    private static Map<Integer, String> parseNumberFormats(Element numFmts) {
        Map<Integer, String> formats = new HashMap<>();
        if (numFmts == null) {
            return formats;
        }
        for (Element numFmt : XmlSupport.children(numFmts, "numFmt")) {
            formats.put(parseInt(XmlSupport.attr(numFmt, "numFmtId"), 0), XmlSupport.attr(numFmt, "formatCode"));
        }
        return formats;
    }

    private static List<FontStyle> parseFonts(Element fontsElement) {
        List<FontStyle> fonts = new ArrayList<>();
        if (fontsElement == null) {
            return fonts;
        }
        for (Element font : XmlSupport.children(fontsElement, "font")) {
            FontStyle.Builder builder = FontStyle.builder();
            Element name = XmlSupport.child(font, "name");
            Element size = XmlSupport.child(font, "sz");
            Element color = XmlSupport.child(font, "color");
            if (name != null) {
                builder.name(XmlSupport.attr(name, "val"));
            }
            if (size != null) {
                builder.size(parseDouble(XmlSupport.attr(size, "val"), 11D));
            }
            builder.bold(XmlSupport.child(font, "b") != null)
                    .italic(XmlSupport.child(font, "i") != null)
                    .underline(XmlSupport.child(font, "u") != null);
            String rgb = XmlSupport.attr(color, "rgb");
            if (!rgb.isBlank()) {
                builder.color(rgb);
            }
            fonts.add(builder.build());
        }
        return fonts;
    }

    private static List<String> parseFills(Element fillsElement) {
        List<String> fills = new ArrayList<>();
        if (fillsElement == null) {
            return fills;
        }
        for (Element fill : XmlSupport.children(fillsElement, "fill")) {
            Element patternFill = XmlSupport.child(fill, "patternFill");
            Element fgColor = patternFill == null ? null : XmlSupport.child(patternFill, "fgColor");
            String rgb = XmlSupport.attr(fgColor, "rgb");
            fills.add(rgb.isBlank() ? null : XmlColor.normalize(rgb));
        }
        return fills;
    }

    private static String normalizeWorkbookTarget(String target) {
        String value = target.startsWith("/") ? target.substring(1) : target;
        return value.startsWith("xl/") ? value : "xl/" + value;
    }

    private static Object parseNumber(String raw) {
        try {
            if (raw.contains(".") || raw.contains("E") || raw.contains("e")) {
                return Double.parseDouble(raw);
            }
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return raw;
        }
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static <T> T getOrDefault(List<T> values, int index, T defaultValue) {
        return index >= 0 && index < values.size() ? values.get(index) : defaultValue;
    }

    private static String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String builtInNumberFormat(int id) {
        return switch (id) {
            case 1 -> "0";
            case 2 -> "0.00";
            case 3 -> "#,##0";
            case 4 -> "#,##0.00";
            case 9 -> "0%";
            case 10 -> "0.00%";
            case 14 -> "m/d/yy";
            case 22 -> "m/d/yy h:mm";
            default -> null;
        };
    }
}
