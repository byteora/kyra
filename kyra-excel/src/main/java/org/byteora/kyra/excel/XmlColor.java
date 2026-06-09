package org.byteora.kyra.excel;

import java.util.Locale;

final class XmlColor {
    private XmlColor() {
    }

    static String normalize(String rgb) {
        if (rgb == null || rgb.isBlank()) {
            return null;
        }
        String value = rgb.strip().replace("#", "").toUpperCase(Locale.ROOT);
        if (value.length() == 6) {
            value = "FF" + value;
        }
        if (value.length() != 8) {
            throw new IllegalArgumentException("Color must be RRGGBB or AARRGGBB: " + rgb);
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F'))) {
                throw new IllegalArgumentException("Color must be hexadecimal: " + rgb);
            }
        }
        return value;
    }
}
