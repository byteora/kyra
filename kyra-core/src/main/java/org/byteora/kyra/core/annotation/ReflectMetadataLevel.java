package org.byteora.kyra.core.annotation;

public enum ReflectMetadataLevel {
    BASIC,
    METHOD;

    public boolean includesFields() {
        return true;
    }

    public boolean includesMethods() {
        return this == METHOD;
    }
}
