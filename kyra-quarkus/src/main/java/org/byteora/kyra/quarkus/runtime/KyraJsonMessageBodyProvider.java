package org.byteora.kyra.quarkus.runtime;

import org.byteora.kyra.json.JsonException;
import org.byteora.kyra.json.JsonMapper;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Locale;

/**
 * Quarkus REST request and response body adapter backed by Kyra's reflection-free
 * {@link JsonMapper}.
 */
@Provider
@Singleton
@Priority(Priorities.USER - 100)
@Consumes({MediaType.APPLICATION_JSON, "application/*+json"})
@Produces({MediaType.APPLICATION_JSON, "application/*+json"})
public final class KyraJsonMessageBodyProvider
        implements MessageBodyReader<Object>, MessageBodyWriter<Object> {
    private final JsonMapper jsonMapper;

    @Inject
    public KyraJsonMessageBodyProvider(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return isJson(mediaType);
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                           MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException {
        Type targetType = genericType == null ? type : genericType;
        try {
            return jsonMapper.fromBytes(entityStream.readAllBytes(), targetType);
        } catch (JsonException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        }
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return isJson(mediaType);
    }

    @Override
    public void writeTo(Object value, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                        MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) {
        try {
            jsonMapper.writeTo(entityStream, value);
        } catch (JsonException ex) {
            throw new InternalServerErrorException("Failed to write JSON response", ex);
        }
    }

    @Override
    public long getSize(Object value, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    private boolean isJson(MediaType mediaType) {
        if (mediaType == null) {
            return true;
        }
        String subtype = mediaType.getSubtype();
        return "application".equalsIgnoreCase(mediaType.getType())
                && ("json".equalsIgnoreCase(subtype) || subtype.toLowerCase(Locale.ROOT).endsWith("+json"));
    }
}
