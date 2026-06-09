package org.byteora.kyra.excel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

public final class ExcelWorkbook {
    private final List<ExcelSheet> sheets = new ArrayList<>();
    private final StyleRegistry styles = new StyleRegistry();

    ExcelWorkbook() {
    }

    public List<ExcelSheet> sheets() {
        return Collections.unmodifiableList(sheets);
    }

    public ExcelSheet sheet(String name) {
        Objects.requireNonNull(name, "name");
        return sheets.stream()
                .filter(sheet -> sheet.name().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    String normalized = validateSheetName(name);
                    ExcelSheet sheet = new ExcelSheet(this, normalized);
                    sheets.add(sheet);
                    return sheet;
                });
    }

    public ExcelWorkbook sheet(String name, Consumer<ExcelSheet> consumer) {
        var sheet = sheet(name);
        consumer.accept(sheet);
        return this;
    }

    public void save(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            write(outputStream);
        }
    }

    public void write(OutputStream outputStream) throws IOException {
        Objects.requireNonNull(outputStream, "outputStream");
        if (sheets.isEmpty()) {
            sheet("Sheet1");
        }
        XlsxWriter.write(this, outputStream);
    }

    int styleIndex(CellStyle style) {
        return styles.indexOf(style);
    }

    CellStyle style(int index) {
        return styles.get(index);
    }

    List<CellStyle> styles() {
        return styles.styles();
    }

    void addSheet(ExcelSheet sheet) {
        sheets.add(sheet);
    }

    private static String validateSheetName(String name) {
        Objects.requireNonNull(name, "name");
        String value = name.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Sheet name must not be empty");
        }
        if (value.length() > 31) {
            throw new IllegalArgumentException("Sheet name must be 31 characters or fewer");
        }
        String invalidChars = "[]:*?/\\";
        for (int i = 0; i < value.length(); i++) {
            if (invalidChars.indexOf(value.charAt(i)) >= 0) {
                throw new IllegalArgumentException(
                        "Sheet name contains an invalid character: " + value.charAt(i));
            }
        }
        if (value.toLowerCase(Locale.ROOT).equals("history")) {
            throw new IllegalArgumentException("Sheet name is reserved by Excel: " + value);
        }
        return value;
    }
}
