package org.byteora.kyra.json;

import tools.jackson.core.JsonGenerator;

import java.lang.reflect.Type;

public interface JsonWriterContext {
    JsonGenerator generator();

    void write(Object value);

    void write(Object value, Type type);
}
