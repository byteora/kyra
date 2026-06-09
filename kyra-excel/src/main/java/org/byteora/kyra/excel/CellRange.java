package org.byteora.kyra.excel;

public record CellRange(int firstRow, int firstColumn, int lastRow, int lastColumn) {
    public CellRange {
        CellRef.requireValid(firstRow, firstColumn);
        CellRef.requireValid(lastRow, lastColumn);
        if (firstRow > lastRow || firstColumn > lastColumn) {
            throw new IllegalArgumentException("Range start must be before range end");
        }
    }

    public static CellRange parse(String range) {
        if (range == null || range.isBlank()) {
            throw new IllegalArgumentException("Range must not be empty");
        }
        String[] parts = range.trim().split(":", -1);
        if (parts.length == 1) {
            CellRef cell = CellRef.parse(parts[0]);
            return new CellRange(cell.row(), cell.column(), cell.row(), cell.column());
        }
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid range: " + range);
        }
        CellRef first = CellRef.parse(parts[0]);
        CellRef last = CellRef.parse(parts[1]);
        return new CellRange(
                Math.min(first.row(), last.row()),
                Math.min(first.column(), last.column()),
                Math.max(first.row(), last.row()),
                Math.max(first.column(), last.column()));
    }

    public String reference() {
        return CellRef.toReference(firstRow, firstColumn) + ":" + CellRef.toReference(lastRow, lastColumn);
    }
}
