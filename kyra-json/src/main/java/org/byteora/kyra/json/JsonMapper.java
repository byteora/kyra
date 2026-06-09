package org.byteora.kyra.json;

import java.lang.reflect.Type;

public interface JsonMapper {
    static Builder builder() {
        return new DefaultJsonMapper.Builder();
    }

    String toJson(Object value);

    <T> T fromJson(String json, Class<T> type);

    <T> T fromJson(String json, Type type);

    default <T> T fromJson(String json, TypeRef<T> type) {
        return fromJson(json, type.type());
    }

    interface Builder {
        Builder register(JsonTypeHandler<?> handler);

        Builder failOnUnknownProperties(boolean failOnUnknownProperties);

        Builder includeNulls(boolean includeNulls);

        JsonMapper build();
    }
}
