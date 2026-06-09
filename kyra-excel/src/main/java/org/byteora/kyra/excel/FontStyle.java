package org.byteora.kyra.excel;

public record FontStyle(
        String name,
        double size,
        boolean bold,
        boolean italic,
        boolean underline,
        String color
) {
    public static final FontStyle DEFAULT = builder().build();

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .name(name)
                .size(size)
                .bold(bold)
                .italic(italic)
                .underline(underline)
                .color(color);
    }

    public static final class Builder {
        private String name = "Calibri";
        private double size = 11D;
        private boolean bold;
        private boolean italic;
        private boolean underline;
        private String color;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder size(double size) {
            if (size <= 0D) {
                throw new IllegalArgumentException("Font size must be positive");
            }
            this.size = size;
            return this;
        }

        public Builder bold(boolean bold) {
            this.bold = bold;
            return this;
        }

        public Builder italic(boolean italic) {
            this.italic = italic;
            return this;
        }

        public Builder underline(boolean underline) {
            this.underline = underline;
            return this;
        }

        public Builder color(String color) {
            this.color = XmlColor.normalize(color);
            return this;
        }

        public FontStyle build() {
            return new FontStyle(name == null || name.isBlank() ? "Calibri" : name, size, bold, italic, underline, color);
        }
    }
}
