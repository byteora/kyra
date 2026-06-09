package org.byteora.kyra.excel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ExcelSheet {
    private final ExcelWorkbook workbook;
    private final String name;
    private final Map<String, ExcelCell> cells = new LinkedHashMap<>();
    private final Set<Integer> rows = new LinkedHashSet<>();
    private final List<CellRange> mergedRegions = new ArrayList<>();
    private final Map<Integer, Double> columnWidths = new LinkedHashMap<>();
    private final Map<Integer, Double> rowHeights = new LinkedHashMap<>();
    private int nextRow = 1;

    ExcelSheet(ExcelWorkbook workbook, String name) {
        this.workbook = workbook;
        this.name = name;
    }

    public String name() {
        return name;
    }

    public ExcelCell cell(String reference) {
        CellRef cellRef = CellRef.parse(reference);
        return cell(cellRef.row(), cellRef.column());
    }

    public SheetRow row() {
        return row1Based(nextRow++, 1);
    }

    public SheetRow row(int index) {
        return row(index, 0);
    }

    public SheetRow row(int rowIndex, int columnIndex) {
        return row1Based(rowIndex + 1, columnIndex + 1);
    }

    public SheetRow row(String startReference) {
        CellRef cellRef = CellRef.parse(startReference);
        return row1Based(cellRef.row(), cellRef.column());
    }

    public boolean hasRow(int index) {
        return hasRow1Based(index + 1);
    }

    public boolean hasRow(String reference) {
        return hasRow1Based(CellRef.parse(reference).row());
    }

    public ExcelSheet width(int columnIndex, double width) {
        return width1Based(columnIndex + 1, width);
    }

    public ExcelSheet width(String columnReference, double width) {
        return width1Based(CellRef.parseColumn(columnReference), width);
    }

    public ExcelSheet height(int rowIndex, double height) {
        return height1Based(rowIndex + 1, height);
    }

    public ExcelSheet height(String rowReference, double height) {
        return height1Based(CellRef.parse(rowReference).row(), height);
    }

    public ExcelCell cell(int row, int column) {
        CellRef.requireValid(row, column);
        rows.add(row);
        String reference = CellRef.toReference(row, column);
        return cells.computeIfAbsent(reference, ignored -> new ExcelCell(this, row, column));
    }

    public Optional<ExcelCell> findCell(String reference) {
        return Optional.ofNullable(cells.get(CellRef.parse(reference).reference()));
    }

    public Optional<ExcelCell> findCell(int row, int column) {
        CellRef.requireValid(row, column);
        return Optional.ofNullable(cells.get(CellRef.toReference(row, column)));
    }

    public ExcelSheet value(String reference, Object value) {
        cell(reference).value(value);
        return this;
    }

    public ExcelSheet value(int row, int column, Object value) {
        cell(row, column).value(value);
        return this;
    }

    public ExcelSheet formula(String reference, String formula) {
        cell(reference).formula(formula);
        return this;
    }

    public ExcelSheet merge(String range) {
        return merge(CellRange.parse(range));
    }

    public ExcelSheet merge(int firstRow, int firstColumn, int lastRow, int lastColumn) {
        return merge(new CellRange(firstRow, firstColumn, lastRow, lastColumn));
    }

    public ExcelSheet merge(CellRange range) {
        Objects.requireNonNull(range, "range");
        if (!mergedRegions.contains(range)) {
            mergedRegions.add(range);
        }
        return this;
    }

    public ExcelSheet unmerge(String range) {
        mergedRegions.remove(CellRange.parse(range));
        return this;
    }

    public List<CellRange> mergedRegions() {
        return Collections.unmodifiableList(mergedRegions);
    }

    public Collection<ExcelCell> cells() {
        return Collections.unmodifiableCollection(cells.values());
    }

    ExcelWorkbook workbook() {
        return workbook;
    }

    void addCell(ExcelCell cell) {
        cells.put(cell.reference(), cell);
    }

    Map<Integer, Double> columnWidths() {
        return Collections.unmodifiableMap(columnWidths);
    }

    Map<Integer, Double> rowHeights() {
        return Collections.unmodifiableMap(rowHeights);
    }

    ExcelSheet width1Based(int column, double width) {
        CellRef.requireValid(1, column);
        if (width <= 0D) {
            throw new IllegalArgumentException("Column width must be positive");
        }
        columnWidths.put(column, width);
        return this;
    }

    ExcelSheet height1Based(int row, double height) {
        CellRef.requireValid(row, 1);
        if (height <= 0D) {
            throw new IllegalArgumentException("Row height must be positive");
        }
        rows.add(row);
        rowHeights.put(row, height);
        return this;
    }

    private SheetRow row1Based(int row, int column) {
        CellRef.requireValid(row, column);
        rows.add(row);
        return new SheetRow(this, row, column);
    }

    private boolean hasRow1Based(int row) {
        CellRef.requireValid(row, 1);
        return rows.contains(row);
    }
}
