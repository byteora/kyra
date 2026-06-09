package org.byteora.kyra.json;

import java.lang.reflect.Type;

public interface JsonTypeHandler<T> {
    boolean supports(Type type);

    void write(JsonWriterContext context, T value);

    T read(JsonReaderContext context, Type type);
}
