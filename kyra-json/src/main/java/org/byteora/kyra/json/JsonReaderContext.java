package org.byteora.kyra.json;

import tools.jackson.core.JsonParser;

import java.lang.reflect.Type;

public interface JsonReaderContext {
    JsonParser parser();

    <T> T read(Class<T> type);

    <T> T read(Type type);
}
