package org.byteora.kyra.excel;

import java.util.Objects;
import java.util.Optional;

public final class SheetRow {
    private final ExcelSheet sheet;
    private final int row;
    private final int startColumn;
    private int nextColumn;

    SheetRow(ExcelSheet sheet, int row, int startColumn) {
        CellRef.requireValid(row, startColumn);
        this.sheet = sheet;
        this.row = row;
        this.startColumn = startColumn;
        this.nextColumn = startColumn;
    }

    public int row() {
        return row;
    }

    public int startColumn() {
        return startColumn;
    }

    public ExcelCell cell(int columnIndex) {
        return sheet.cell(row, columnIndex + 1);
    }

    public ExcelCell cell(String columnReference) {
        return sheet.cell(row, CellRef.parseColumn(columnReference));
    }

    public Optional<ExcelCell> findCell(int columnIndex) {
        return sheet.findCell(row, columnIndex + 1);
    }

    public Optional<ExcelCell> findCell(String columnReference) {
        return sheet.findCell(row, CellRef.parseColumn(columnReference));
    }

    public boolean hasCell(int columnIndex) {
        return findCell(columnIndex).isPresent();
    }

    public boolean hasCell(String columnReference) {
        return findCell(columnReference).isPresent();
    }

    public Object value(int columnIndex) {
        return findCell(columnIndex).map(ExcelCell::value).orElse(null);
    }

    public Object value(String columnReference) {
        return findCell(columnReference).map(ExcelCell::value).orElse(null);
    }

    public SheetRow cell(Cell... cells) {
        Objects.requireNonNull(cells, "cells");
        for (Cell cell : cells) {
            Cell value = cell == null ? Cell.blank() : cell;
            value.applyTo(sheet.cell(row, nextColumn++));
        }
        return this;
    }

    public SheetRow height(double height) {
        sheet.height1Based(row, height);
        return this;
    }

    public SheetRow skip() {
        CellRef.requireValid(row, nextColumn + 1);
        nextColumn++;
        return this;
    }

    public SheetRow skip(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Skip count must not be negative");
        }
        for (int i = 0; i < count; i++) {
            skip();
        }
        return this;
    }
}
