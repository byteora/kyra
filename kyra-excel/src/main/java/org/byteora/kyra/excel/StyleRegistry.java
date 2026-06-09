package org.byteora.kyra.excel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class StyleRegistry {
    private final List<CellStyle> styles = new ArrayList<>();

    StyleRegistry() {
        styles.add(CellStyle.DEFAULT);
    }

    int indexOf(CellStyle style) {
        int index = styles.indexOf(style);
        if (index >= 0) {
            return index;
        }
        styles.add(style);
        return styles.size() - 1;
    }

    CellStyle get(int index) {
        if (index < 0 || index >= styles.size()) {
            return CellStyle.DEFAULT;
        }
        return styles.get(index);
    }

    List<CellStyle> styles() {
        return Collections.unmodifiableList(styles);
    }
}
