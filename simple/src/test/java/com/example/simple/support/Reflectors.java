package com.example.simple.support;

import org.byteora.kyra.core.runtime.Reflector;

public final class Reflectors {
    private Reflectors() {
    }

    @SuppressWarnings("unchecked")
    public static <T> Reflector<T> load(Class<T> type) {
        try {
            return (Reflector<T>) Class
                    .forName(GeneratedTypeNames.reflectorTypeName(type))
                    .getConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Unknown type: " + type.getName(), ex);
        }
    }
}
