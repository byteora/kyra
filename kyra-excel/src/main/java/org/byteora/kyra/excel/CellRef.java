package org.byteora.kyra.excel;

import java.util.Locale;

record CellRef(int row, int column) {
    static final int MAX_ROW = 1_048_576;
    static final int MAX_COLUMN = 16_384;

    CellRef {
        requireValid(row, column);
    }

    String reference() {
        return toReference(row, column);
    }

    static CellRef parse(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Cell reference must not be empty");
        }
        String value = reference.trim().toUpperCase(Locale.ROOT);
        int split = 0;
        while (split < value.length() && Character.isLetter(value.charAt(split))) {
            split++;
        }
        if (split == 0 || split == value.length()) {
            throw new IllegalArgumentException("Invalid cell reference: " + reference);
        }
        int column = 0;
        for (int i = 0; i < split; i++) {
            char ch = value.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                throw new IllegalArgumentException("Invalid cell reference: " + reference);
            }
            column = column * 26 + ch - 'A' + 1;
        }
        int row;
        try {
            row = Integer.parseInt(value.substring(split));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid cell reference: " + reference, ex);
        }
        return new CellRef(row, column);
    }

    static int parseColumn(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Column reference must not be empty");
        }
        String value = reference.trim().toUpperCase(Locale.ROOT);
        if (Character.isDigit(value.charAt(value.length() - 1))) {
            return parse(value).column();
        }
        int column = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                throw new IllegalArgumentException("Invalid column reference: " + reference);
            }
            column = column * 26 + ch - 'A' + 1;
        }
        requireValid(1, column);
        return column;
    }

    static String toReference(int row, int column) {
        requireValid(row, column);
        StringBuilder letters = new StringBuilder();
        int current = column;
        while (current > 0) {
            current--;
            letters.append((char) ('A' + current % 26));
            current /= 26;
        }
        return letters.reverse().append(row).toString();
    }

    static void requireValid(int row, int column) {
        if (row < 1 || row > MAX_ROW) {
            throw new IllegalArgumentException("Row is outside xlsx bounds: " + row);
        }
        if (column < 1 || column > MAX_COLUMN) {
            throw new IllegalArgumentException("Column is outside xlsx bounds: " + column);
        }
    }
}
