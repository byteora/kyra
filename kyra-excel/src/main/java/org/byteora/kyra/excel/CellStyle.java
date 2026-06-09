package org.byteora.kyra.excel;

public record CellStyle(
        FontStyle font,
        String numberFormat,
        String fillColor,
        String horizontalAlignment,
        String verticalAlignment,
        boolean wrapText
) {
    public static final CellStyle DEFAULT = builder().build();

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .font(font)
                .numberFormat(numberFormat)
                .fillColor(fillColor)
                .horizontalAlignment(horizontalAlignment)
                .verticalAlignment(verticalAlignment)
                .wrapText(wrapText);
    }

    public static final class Builder {
        private FontStyle font = FontStyle.DEFAULT;
        private String numberFormat;
        private String fillColor;
        private String horizontalAlignment;
        private String verticalAlignment;
        private boolean wrapText;

        public Builder font(FontStyle font) {
            this.font = font == null ? FontStyle.DEFAULT : font;
            return this;
        }

        public Builder numberFormat(String numberFormat) {
            this.numberFormat = numberFormat == null || numberFormat.isBlank() ? null : numberFormat;
            return this;
        }

        public Builder fillColor(String fillColor) {
            this.fillColor = XmlColor.normalize(fillColor);
            return this;
        }

        public Builder horizontalAlignment(String horizontalAlignment) {
            this.horizontalAlignment = normalizeAlignment(horizontalAlignment);
            return this;
        }

        public Builder horizontalAlignment(HorizontalAlign horizontalAlignment) {
            this.horizontalAlignment = horizontalAlignment == null ? null : horizontalAlignment.value();
            return this;
        }

        public Builder verticalAlignment(String verticalAlignment) {
            this.verticalAlignment = normalizeAlignment(verticalAlignment);
            return this;
        }

        public Builder verticalAlignment(VerticalAlign verticalAlignment) {
            this.verticalAlignment = verticalAlignment == null ? null : verticalAlignment.value();
            return this;
        }

        public Builder align(HorizontalAlign horizontalAlignment, VerticalAlign verticalAlignment) {
            return horizontalAlignment(horizontalAlignment).verticalAlignment(verticalAlignment);
        }

        public Builder wrapText(boolean wrapText) {
            this.wrapText = wrapText;
            return this;
        }

        public CellStyle build() {
            return new CellStyle(font, numberFormat, fillColor, horizontalAlignment, verticalAlignment, wrapText);
        }

        private static String normalizeAlignment(String alignment) {
            return alignment == null || alignment.isBlank() ? null : alignment;
        }
    }
}
