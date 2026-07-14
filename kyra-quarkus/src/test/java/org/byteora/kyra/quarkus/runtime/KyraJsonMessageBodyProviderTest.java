package org.byteora.kyra.quarkus.runtime;

import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.runtime.GeneratedReflectors;
import org.byteora.kyra.core.runtime.ReflectorRegistry;
import org.byteora.kyra.json.JsonMapper;
import org.byteora.kyra.json.TypeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KyraJsonMessageBodyProviderTest {
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
    private KyraJsonMessageBodyProvider provider;

    @BeforeEach
    void setUp() {
        ReflectorRegistry.clear();
        ReflectorRegistry.register(JsonUser.class, GeneratedReflectors.load(JsonUser.class));
        provider = new KyraJsonMessageBodyProvider(JsonMapper.builder().build());
    }

    @Test
    void shouldReadGenericJsonRequestBody() throws Exception {
        Type targetType = new TypeRef<List<JsonUser>>() {
        }.type();

        @SuppressWarnings("unchecked")
        List<JsonUser> users = (List<JsonUser>) provider.readFrom(
                objectClass(),
                targetType,
                NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                new ByteArrayInputStream("[{\"name\":\"quarkus\",\"age\":12}]".getBytes(StandardCharsets.UTF_8))
        );

        assertEquals(1, users.size());
        assertEquals("quarkus", users.getFirst().name);
        assertEquals(12, users.getFirst().age);
    }

    @Test
    void shouldWriteJsonResponseBody() {
        JsonUser user = new JsonUser();
        user.name = "quarkus";
        user.age = 12;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        provider.writeTo(user, JsonUser.class, JsonUser.class, NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE, new MultivaluedHashMap<>(), output);

        assertEquals("{\"name\":\"quarkus\",\"age\":12}", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void shouldSupportStructuredJsonMediaTypesAndRejectInvalidJson() {
        assertTrue(provider.isReadable(JsonUser.class, JsonUser.class, NO_ANNOTATIONS,
                MediaType.valueOf("application/problem+json")));
        assertThrows(BadRequestException.class, () -> provider.readFrom(
                objectClass(),
                JsonUser.class,
                NO_ANNOTATIONS,
                MediaType.APPLICATION_JSON_TYPE,
                new MultivaluedHashMap<>(),
                new ByteArrayInputStream("{invalid}".getBytes(StandardCharsets.UTF_8))
        ));
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> objectClass() {
        return (Class<Object>) (Class<?>) JsonUser.class;
    }

    @Reflect
    public static final class JsonUser {
        public String name;
        public int age;
    }
}
