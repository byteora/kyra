package org.byteora.kyra.excel;

import java.util.Objects;
import java.util.function.UnaryOperator;

public final class Style {
    private final UnaryOperator<CellStyle> customizer;

    private Style(UnaryOperator<CellStyle> customizer) {
        this.customizer = customizer;
    }

    public static CellStyle of(Style... styles) {
        return apply(CellStyle.DEFAULT, styles);
    }

    public static Style font(FontStyle font) {
        return new Style(style -> style.toBuilder().font(font).build());
    }

    public static Style bold() {
        return new Style(style -> style.toBuilder().font(style.font().toBuilder().bold(true).build()).build());
    }

    public static Style italic() {
        return new Style(style -> style.toBuilder().font(style.font().toBuilder().italic(true).build()).build());
    }

    public static Style fontColor(String color) {
        return new Style(style -> style.toBuilder().font(style.font().toBuilder().color(color).build()).build());
    }

    public static Style fillColor(String color) {
        return new Style(style -> style.toBuilder().fillColor(color).build());
    }

    public static Style numberFormat(String numberFormat) {
        return new Style(style -> style.toBuilder().numberFormat(numberFormat).build());
    }

    public static Style align(HorizontalAlign horizontal) {
        return new Style(style -> style.toBuilder().horizontalAlignment(horizontal).build());
    }

    public static Style align(HorizontalAlign horizontal, VerticalAlign vertical) {
        return new Style(style -> style.toBuilder().horizontalAlignment(horizontal).verticalAlignment(vertical).build());
    }

    public static Style valign(VerticalAlign vertical) {
        return new Style(style -> style.toBuilder().verticalAlignment(vertical).build());
    }

    public static Style center() {
        return align(HorizontalAlign.CENTER);
    }

    public static Style middle() {
        return valign(VerticalAlign.CENTER);
    }

    public static Style wrapText() {
        return new Style(style -> style.toBuilder().wrapText(true).build());
    }

    static CellStyle apply(CellStyle base, Style... styles) {
        CellStyle style = base == null ? CellStyle.DEFAULT : base;
        if (styles != null) {
            for (Style next : styles) {
                if (next != null) {
                    style = next.customizer.apply(style);
                }
            }
        }
        return style;
    }

    static Style combine(UnaryOperator<CellStyle> customizer) {
        return new Style(Objects.requireNonNull(customizer, "customizer"));
    }
}
