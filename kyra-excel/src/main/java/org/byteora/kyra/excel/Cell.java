package org.byteora.kyra.excel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class Cell {
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final String DEFAULT_DATE_FORMAT = "yyyy-mm-dd";
    private static final String DEFAULT_TIME_FORMAT = "hh:mm:ss";
    private static final String DEFAULT_DATETIME_FORMAT = "yyyy-mm-dd hh:mm:ss";

    private final Object value;
    private final String formula;
    private CellStyle style;

    private Cell(Object value, String formula, CellStyle style) {
        this.value = value;
        this.formula = formula;
        this.style = style;
    }

    public static Cell blank() {
        return new Cell(null, null, null);
    }

    public static Cell text(String value) {
        return new Cell(value, null, null);
    }

    public static Cell number(Number value) {
        return new Cell(value, null, null);
    }

    public static Cell decimal(Number value, int scale) {
        return number(value).numberFormat(fixedScaleFormat(scale));
    }

    public static Cell percent(Number value) {
        return percent(value, 2);
    }

    public static Cell percent(Number value, int scale) {
        return number(value).numberFormat(fixedScaleFormat(scale) + "%");
    }

    public static Cell currency(Number value) {
        return currency(value, "¥", 2);
    }

    public static Cell currency(Number value, String symbol) {
        return currency(value, symbol, 2);
    }

    public static Cell currency(Number value, String symbol, int scale) {
        String escaped = Objects.requireNonNull(symbol, "symbol").replace("\"", "\"\"");
        return number(value).numberFormat("\"" + escaped + "\"#,##" + fixedScaleFormat(scale));
    }

    public static Cell date(LocalDate value) {
        return date(value, DEFAULT_DATE_FORMAT);
    }

    public static Cell date(LocalDate value, String format) {
        return number(toExcelDate(value)).numberFormat(format);
    }

    public static Cell date(String value, String inputFormat) {
        return date(value, inputFormat, DEFAULT_DATE_FORMAT);
    }

    public static Cell date(String value, String inputFormat, String format) {
        return date(LocalDate.parse(value, DateTimeFormatter.ofPattern(inputFormat)), format);
    }

    public static Cell date(OffsetDateTime value) {
        return date(value, DEFAULT_DATE_FORMAT);
    }

    public static Cell date(OffsetDateTime value, String format) {
        return date(value.toLocalDate(), format);
    }

    public static Cell time(LocalTime value) {
        return time(value, DEFAULT_TIME_FORMAT);
    }

    public static Cell time(LocalTime value, String format) {
        return number(toExcelTime(value)).numberFormat(format);
    }

    public static Cell time(String value, String inputFormat) {
        return time(value, inputFormat, DEFAULT_TIME_FORMAT);
    }

    public static Cell time(String value, String inputFormat, String format) {
        return time(LocalTime.parse(value, DateTimeFormatter.ofPattern(inputFormat)), format);
    }

    public static Cell time(OffsetDateTime value) {
        return time(value, DEFAULT_TIME_FORMAT);
    }

    public static Cell time(OffsetDateTime value, String format) {
        return time(value.toLocalTime(), format);
    }

    public static Cell datetime(LocalDateTime value) {
        return datetime(value, DEFAULT_DATETIME_FORMAT);
    }

    public static Cell datetime(LocalDateTime value, String format) {
        return number(toExcelDateTime(value)).numberFormat(format);
    }

    public static Cell datetime(String value, String inputFormat) {
        return datetime(value, inputFormat, DEFAULT_DATETIME_FORMAT);
    }

    public static Cell datetime(String value, String inputFormat, String format) {
        return datetime(LocalDateTime.parse(value, DateTimeFormatter.ofPattern(inputFormat)), format);
    }

    public static Cell datetime(long epochMillis) {
        return datetime(epochMillis, DEFAULT_DATETIME_FORMAT);
    }

    public static Cell datetime(long epochMillis, String format) {
        return datetime(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()), format);
    }

    public static Cell datetime(OffsetDateTime value) {
        return datetime(value, DEFAULT_DATETIME_FORMAT);
    }

    public static Cell datetime(OffsetDateTime value, String format) {
        return datetime(value.toLocalDateTime(), format);
    }

    public static Cell datetime(ZonedDateTime value) {
        return datetime(value, DEFAULT_DATETIME_FORMAT);
    }

    public static Cell datetime(ZonedDateTime value, String format) {
        return datetime(value.toLocalDateTime(), format);
    }

    public static Cell formula(String formula) {
        return new Cell(null, Objects.requireNonNull(formula, "formula"), null);
    }

    public static Cell formula(String formula, Number cachedValue) {
        return new Cell(cachedValue, Objects.requireNonNull(formula, "formula"), null);
    }

    public Cell style(CellStyle style) {
        this.style = Objects.requireNonNull(style, "style");
        return this;
    }

    public Cell style(Style... styles) {
        this.style = Style.apply(style, styles);
        return this;
    }

    public Cell font(FontStyle font) {
        return style(styleBuilder().font(font).build());
    }

    public Cell numberFormat(String numberFormat) {
        return style(styleBuilder().numberFormat(numberFormat).build());
    }

    public Cell fillColor(String rgb) {
        return style(styleBuilder().fillColor(rgb).build());
    }

    public Cell align(String horizontal, String vertical) {
        return style(styleBuilder()
                .horizontalAlignment(horizontal)
                .verticalAlignment(vertical)
                .build());
    }

    public Cell align(HorizontalAlign horizontal, VerticalAlign vertical) {
        return style(styleBuilder()
                .horizontalAlignment(horizontal)
                .verticalAlignment(vertical)
                .build());
    }

    public Cell align(HorizontalAlign horizontal) {
        return style(styleBuilder().horizontalAlignment(horizontal).build());
    }

    public Cell valign(VerticalAlign vertical) {
        return style(styleBuilder().verticalAlignment(vertical).build());
    }

    public Cell wrapText() {
        return style(styleBuilder().wrapText(true).build());
    }

    void applyTo(ExcelCell cell) {
        cell.value(value);
        if (formula != null) {
            cell.formula(formula);
        }
        if (style != null) {
            cell.style(style);
        }
    }

    private CellStyle.Builder styleBuilder() {
        return style == null ? CellStyle.builder() : style.toBuilder();
    }

    private static String fixedScaleFormat(int scale) {
        if (scale < 0) {
            throw new IllegalArgumentException("Scale must not be negative");
        }
        return scale == 0 ? "0" : "0." + "0".repeat(scale);
    }

    private static double toExcelDate(LocalDate value) {
        Objects.requireNonNull(value, "value");
        return ChronoUnit.DAYS.between(EXCEL_EPOCH, value);
    }

    private static double toExcelTime(LocalTime value) {
        Objects.requireNonNull(value, "value");
        return value.toSecondOfDay() / 86_400D + value.getNano() / 86_400_000_000_000D;
    }

    private static double toExcelDateTime(LocalDateTime value) {
        Objects.requireNonNull(value, "value");
        return toExcelDate(value.toLocalDate()) + toExcelTime(value.toLocalTime());
    }
}
