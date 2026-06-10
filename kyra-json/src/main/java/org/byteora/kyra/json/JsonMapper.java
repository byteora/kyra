package org.byteora.kyra.json;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public interface JsonMapper {
    static Builder builder() {
        return new DefaultJsonMapper.Builder();
    }

    String toJson(Object value);

    /**
     * Serializes {@code value} straight to UTF-8 bytes. Prefer this (or {@link #writeTo}) over
     * {@link #toJson} when the destination is bytes (HTTP responses, files, sockets): it skips the
     * char→String→UTF-8 round trip and lets property names be written as pre-encoded UTF-8.
     */
    default byte[] toBytes(Object value) {
        return toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Serializes {@code value} as UTF-8 directly to {@code out}. The stream is flushed but not
     * closed.
     */
    default void writeTo(OutputStream out, Object value) {
        try {
            out.write(toBytes(value));
        } catch (IOException ex) {
            throw new JsonException("Failed to write JSON", ex);
        }
    }

    <T> T fromJson(String json, Class<T> type);

    <T> T fromJson(String json, Type type);

    default <T> T fromJson(String json, TypeRef<T> type) {
        return fromJson(json, type.type());
    }

    /**
     * Parses UTF-8 {@code json} bytes. Prefer this over {@link #fromJson(String, Class)} when the
     * source is already bytes (request bodies, files): it uses the UTF-8 byte parser directly and
     * skips materializing an intermediate {@link String}.
     */
    default <T> T fromBytes(byte[] json, Class<T> type) {
        return fromJson(new String(json, StandardCharsets.UTF_8), type);
    }

    default <T> T fromBytes(byte[] json, Type type) {
        return fromJson(new String(json, StandardCharsets.UTF_8), type);
    }

    default <T> T fromBytes(byte[] json, TypeRef<T> type) {
        return fromBytes(json, type.type());
    }

    interface Builder {
        Builder register(JsonTypeHandler<?> handler);

        Builder failOnUnknownProperties(boolean failOnUnknownProperties);

        Builder includeNulls(boolean includeNulls);

        JsonMapper build();
    }
}
