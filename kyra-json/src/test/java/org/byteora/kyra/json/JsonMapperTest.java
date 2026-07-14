package org.byteora.kyra.json;

import org.byteora.kyra.core.annotation.Alias;
import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.runtime.ClassInfo;
import org.byteora.kyra.core.runtime.FieldInfo;
import org.byteora.kyra.core.runtime.MethodInfo;
import org.byteora.kyra.core.runtime.ParameterInfo;
import org.byteora.kyra.core.runtime.Reflector;
import org.byteora.kyra.core.runtime.ReflectorRegistry;
import org.byteora.kyra.core.runtime.RuntimeTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonMapperTest {
    @BeforeEach
    void setUp() {
    }
    private StrValue<Integer> strValue = new StrValue<Integer>("123",123);
    @Test
    void strValueTest() throws NoSuchFieldException {
        var field=JsonMapperTest.class.getDeclaredField("strValue");
        var type =field.getGenericType();
        JsonMapper mapper = JsonMapper.builder().build();
        var json=mapper.toJson(strValue);
        assertEquals(json,"{\"id\":\"123\",\"val\":123}");
        var obj=(StrValue<Integer>)mapper.fromJson(json,type);
        assertEquals(obj.value,123);
    }
    @Test
    void shouldSerializeAndDeserializeReflectorObject() {
        JsonMapper mapper = JsonMapper.builder().build();
        User user = new User();
        user.id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        user.name = "Alice";
        user.age = 31;
        user.role = Role.ADMIN;
        user.tags = List.of((short)0,(short)1,(short)2);

        String json = mapper.toJson(user);
        assertTrue(json.contains("\"name\":\"Alice\""));
        assertFalse(json.contains("\"username\""));

        User decoded = mapper.fromJson(json, User.class);
        assertEquals(user.id, decoded.id);
        assertEquals("Alice", decoded.name);
        assertEquals(31, decoded.age);
        assertEquals(Role.ADMIN, decoded.role);
        assertEquals(List.of((short)0,(short)1,(short)2), decoded.tags);
    }

    @Test
    void shouldSerializeToBytesAndStreamMatchingToJson() throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        User user = new User();
        user.id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        user.name = "Ünïcödé/\"quote\"\n";
        user.age = 31;
        user.role = Role.ADMIN;
        user.tags = List.of((short) 0, (short) 1, (short) 2);

        byte[] expected = mapper.toJson(user).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertEquals(java.util.Arrays.toString(expected), java.util.Arrays.toString(mapper.toBytes(user)));

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        boolean[] closed = {false};
        java.io.FilterOutputStream guard = new java.io.FilterOutputStream(out) {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        mapper.writeTo(guard, user);
        assertEquals(java.util.Arrays.toString(expected), java.util.Arrays.toString(out.toByteArray()));
        assertFalse(closed[0], "writeTo must not close the caller's stream");

        User decoded = mapper.fromBytes(mapper.toBytes(user), User.class);
        assertEquals(user.id, decoded.id);
        assertEquals(user.name, decoded.name);
        assertEquals(user.age, decoded.age);
        assertEquals(user.role, decoded.role);
        assertEquals(user.tags, decoded.tags);
    }

    @Test
    void shouldResolveGenericObjectFields() {
        JsonMapper mapper = JsonMapper.builder().build();

        Box<User> box = mapper.fromJson(
                "{\"value\":{\"name\":\"Bob\",\"age\":24,\"role\":\"USER\",\"tags\":[7]}}",
                new TypeRef<Box<User>>() {
                }
        );

        assertEquals("Bob", box.value.name);
        assertEquals(List.of((short) 7), box.value.tags);
    }

    @Test
    void shouldReadCollectionsAndMapsWithGenericValues() {
        JsonMapper mapper = JsonMapper.builder().build();

        List<User> users = mapper.fromJson("[{\"name\":\"A\",\"age\":1},{\"name\":\"B\",\"age\":2}]",
                new TypeRef<List<User>>() {
                });
        Map<String, User> usersByKey = mapper.fromJson("{\"first\":{\"name\":\"A\",\"age\":1}}",
                new TypeRef<Map<String, User>>() {
                });

        assertEquals(2, users.size());
        assertEquals("B", users.get(1).name);
        assertEquals("A", usersByKey.get("first").name);
    }

    @Test
    void shouldUseCustomTypeHandler() {
        JsonMapper mapper = JsonMapper.builder()
                .register(new MoneyHandler())
                .build();
        Invoice invoice = new Invoice();
        invoice.total = new Money(new BigDecimal("12.50"), "USD");

        String json = mapper.toJson(invoice);
        assertEquals("{\"total\":\"USD 12.50\"}", json);

        Invoice decoded = mapper.fromJson(json, Invoice.class);
        assertEquals(new BigDecimal("12.50"), decoded.total.amount());
        assertEquals("USD", decoded.total.currency());
    }

    @Test
    void shouldPreferConstructorAndSetRemainingKnownFields() {
        JsonMapper mapper = JsonMapper.builder().build();

        ConstructorUser decoded = mapper.fromJson(
                "{\"username\":\"Alice\",\"age\":31,\"displayName\":\"Alice A.\"}",
                ConstructorUser.class);

        assertEquals("Alice", decoded.username);
        assertEquals(31, decoded.age);
        assertEquals("Alice A.", decoded.displayName);
    }

    @Test
    void shouldHandleUnknownPropertiesAccordingToConfiguration() {
        JsonMapper relaxed = JsonMapper.builder().build();
        User relaxedUser = relaxed.fromJson("{\"name\":\"A\",\"unknown\":1}", User.class);
        assertEquals("A", relaxedUser.name);

        JsonMapper strict = JsonMapper.builder().failOnUnknownProperties(true).build();
        JsonException exception = assertThrows(JsonException.class,
                () -> strict.fromJson("{\"name\":\"A\",\"unknown\":1}", User.class));
        assertTrue(exception.getMessage().contains("Unknown JSON property"));
    }

    @Test
    void shouldFailWhenReflectorIsMissing() {
        JsonMapper mapper = JsonMapper.builder().build();

        JsonException exception = assertThrows(JsonException.class, () -> mapper.fromJson("{}", Unregistered.class));

        assertTrue(exception.getMessage().contains("No Reflector registered"));
    }

    @Test
    void shouldWrapJacksonThreeSyntaxErrors() {
        JsonMapper mapper = JsonMapper.builder().build();

        JsonException stringError = assertThrows(JsonException.class,
                () -> mapper.fromJson("{invalid}", User.class));
        JsonException byteError = assertThrows(JsonException.class,
                () -> mapper.fromBytes("{invalid}".getBytes(java.nio.charset.StandardCharsets.UTF_8), User.class));

        assertEquals("Failed to read JSON", stringError.getMessage());
        assertEquals("Failed to read JSON", byteError.getMessage());
    }

    static final class Unregistered {
        public int value;
    }
    @Target({ElementType.TYPE,ElementType.FIELD})
    @Retention(RetentionPolicy.CLASS)
    public @interface TestAnnotation {
    }
    @Reflect
    public record StrValue<T>(String id,@TestAnnotation @Alias("val") T value) {};
    public enum Role {
        USER,
        ADMIN
    }
    @Reflect
    public static final class User {
        public UUID id;
        public String name;
        public int age;
        public Role role;
        public List<Short> tags;
    }

    @Reflect
    public static final class Box<T> {
        public T value;
    }
    @Reflect
    public record Money(BigDecimal amount, String currency) {
    }
    @Reflect
    public static final class Invoice {
        public Money total;
    }
    @Reflect
    public static final class ConstructorUser {
        public final String username;
        public final int age;
        public String displayName;

        public ConstructorUser(String username, int age) {
            this.username = username;
            this.age = age;
        }
    }

    static final class MoneyHandler implements JsonTypeHandler<Money> {
        @Override
        public boolean supports(Type type) {
            return type == Money.class;
        }

        @Override
        public void write(JsonWriterContext context, Money value) {
            context.write(value.currency() + " " + value.amount());
        }

        @Override
        public Money read(JsonReaderContext context, Type type) {
            String[] parts = context.read(String.class).split(" ", 2);
            return new Money(new BigDecimal(parts[1]), parts[0]);
        }
    }
}
