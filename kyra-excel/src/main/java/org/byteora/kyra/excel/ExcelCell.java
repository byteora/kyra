package org.byteora.kyra.excel;

import java.util.Objects;
import java.util.Optional;

public final class ExcelCell {
    private final ExcelSheet sheet;
    private final int row;
    private final int column;
    private Object value;
    private String formula;
    private int styleIndex;

    ExcelCell(ExcelSheet sheet, int row, int column) {
        this.sheet = sheet;
        this.row = row;
        this.column = column;
    }

    public int row() {
        return row;
    }

    public int column() {
        return column;
    }

    public String reference() {
        return CellRef.toReference(row, column);
    }

    public Object value() {
        return value;
    }

    public Optional<String> formula() {
        return Optional.ofNullable(formula);
    }

    public CellStyle style() {
        return sheet.workbook().style(styleIndex);
    }

    public ExcelCell value(Object value) {
        this.value = normalizeValue(value);
        return this;
    }

    public ExcelCell formula(String formula) {
        Objects.requireNonNull(formula, "formula");
        String normalized = formula.strip();
        if (normalized.startsWith("=")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Formula must not be empty");
        }
        this.formula = normalized;
        return this;
    }

    public ExcelCell clearFormula() {
        this.formula = null;
        return this;
    }

    public ExcelCell style(CellStyle style) {
        this.styleIndex = sheet.workbook().styleIndex(Objects.requireNonNull(style, "style"));
        return this;
    }

    public ExcelCell font(FontStyle font) {
        return style(style().toBuilder().font(font).build());
    }

    public ExcelCell numberFormat(String numberFormat) {
        return style(style().toBuilder().numberFormat(numberFormat).build());
    }

    public ExcelCell fillColor(String rgb) {
        return style(style().toBuilder().fillColor(rgb).build());
    }

    public ExcelCell align(String horizontal, String vertical) {
        return style(style().toBuilder()
                .horizontalAlignment(horizontal)
                .verticalAlignment(vertical)
                .build());
    }

    public ExcelCell align(HorizontalAlign horizontal, VerticalAlign vertical) {
        return style(style().toBuilder()
                .horizontalAlignment(horizontal)
                .verticalAlignment(vertical)
                .build());
    }

    public ExcelCell align(HorizontalAlign horizontal) {
        return style(style().toBuilder().horizontalAlignment(horizontal).build());
    }

    public ExcelCell valign(VerticalAlign vertical) {
        return style(style().toBuilder().verticalAlignment(vertical).build());
    }

    int styleIndex() {
        return styleIndex;
    }

    void styleIndex(int styleIndex) {
        this.styleIndex = styleIndex;
    }

    private static Object normalizeValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        return value.toString();
    }
}
