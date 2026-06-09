package org.byteora.kyra.excel;

public enum HorizontalAlign {
    GENERAL("general"),
    LEFT("left"),
    CENTER("center"),
    RIGHT("right"),
    FILL("fill"),
    JUSTIFY("justify"),
    CENTER_SELECTION("centerContinuous"),
    DISTRIBUTED("distributed");

    private final String value;

    HorizontalAlign(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}
