package org.byteora.kyra.spring.boot.autoconfigure;

import org.byteora.kyra.json.JsonException;
import org.byteora.kyra.json.JsonMapper;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public final class KyraJsonHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {
    private final JsonMapper jsonMapper;

    public KyraJsonHttpMessageConverter(JsonMapper jsonMapper) {
        super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
        this.jsonMapper = jsonMapper;
        setDefaultCharset(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return true;
    }

    @Override
    public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        try {
            String json = new String(inputMessage.getBody().readAllBytes(), StandardCharsets.UTF_8);
            return jsonMapper.fromJson(json, type);
        } catch (JsonException ex) {
            throw new HttpMessageNotReadableException(ex.getMessage(), ex, inputMessage);
        }
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return read(clazz, clazz, inputMessage);
    }

    @Override
    protected void writeInternal(Object value, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        try {
            byte[] body = jsonMapper.toJson(value).getBytes(StandardCharsets.UTF_8);
            outputMessage.getBody().write(body);
        } catch (JsonException ex) {
            throw new HttpMessageNotWritableException(ex.getMessage(), ex);
        }
    }
}
