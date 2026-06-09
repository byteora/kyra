package org.byteora.kyra.excel;

public enum VerticalAlign {
    TOP("top"),
    CENTER("center"),
    BOTTOM("bottom"),
    JUSTIFY("justify"),
    DISTRIBUTED("distributed");

    private final String value;

    VerticalAlign(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}
