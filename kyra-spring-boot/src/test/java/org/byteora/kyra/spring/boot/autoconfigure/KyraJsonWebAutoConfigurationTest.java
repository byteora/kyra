package org.byteora.kyra.spring.boot.autoconfigure;

import org.byteora.kyra.core.runtime.AnnotationMeta;
import org.byteora.kyra.core.runtime.ClassInfo;
import org.byteora.kyra.core.runtime.FieldInfo;
import org.byteora.kyra.core.runtime.MethodInfo;
import org.byteora.kyra.core.runtime.ParameterInfo;
import org.byteora.kyra.core.runtime.Reflector;
import org.byteora.kyra.core.runtime.ReflectorRegistry;
import org.byteora.kyra.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KyraJsonWebAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KyraJsonWebAutoConfiguration.class));

    @BeforeEach
    void setUp() {
        ReflectorRegistry.clear();
        ReflectorRegistry.register(WebUser.class, new WebUserReflector());
    }

    @Test
    void shouldRegisterJsonMapperAndHttpMessageConverter() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("kyraJsonMapper"));
            assertTrue(context.containsBean("kyraJsonHttpMessageConverter"));
        });
    }

    @Test
    void converterShouldReadAndWriteUsingKyraJsonMapper() {
        contextRunner.run(context -> {
            KyraJsonHttpMessageConverter converter = context.getBean(KyraJsonHttpMessageConverter.class);

            WebUser user = (WebUser) converter.read(WebUser.class, WebUser.class,
                    new TestHttpInputMessage("{\"name\":\"web\",\"age\":9}"));
            assertEquals("web", user.name);
            assertEquals(9, user.age);

            TestHttpOutputMessage outputMessage = new TestHttpOutputMessage();
            converter.write(user, WebUser.class, MediaType.APPLICATION_JSON, outputMessage);

            assertEquals("{\"name\":\"web\",\"age\":9}", outputMessage.body());
        });
    }

    @Test
    void shouldUseCustomJsonMapperBean() {
        JsonMapper customMapper = JsonMapper.builder().includeNulls(false).build();

        contextRunner
                .withBean(JsonMapper.class, () -> customMapper)
                .run(context -> assertEquals(customMapper, context.getBean(JsonMapper.class)));
    }

    static final class WebUser {
        String name;
        int age;
    }

    static final class WebUserReflector implements Reflector<WebUser> {
        @Override
        public WebUser newInstance() {
            return new WebUser();
        }

        @Override
        public ClassInfo getClassInfo() {
            return new ClassInfo(WebUser.class, Object.class, Modifier.PUBLIC, new AnnotationMeta[0], new ParameterInfo[0]);
        }

        @Override
        public Object invoke(WebUser target, int index, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(WebUser target, int index, Object value) {
            if (index == 0) {
                target.name = (String) value;
            } else if (index == 1) {
                target.age = (Integer) value;
            } else {
                throw new IllegalArgumentException("Unknown field");
            }
        }

        @Override
        public Object get(WebUser target, int index) {
            return switch (index) {
                case 0 -> target.name;
                case 1 -> target.age;
                default -> throw new IllegalArgumentException("Unknown field");
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"name", "age"};
        }

        @Override
        public FieldInfo getField(int index) {
            return switch (index) {
                case 0 -> new FieldInfo("name", String.class, 0, null, new AnnotationMeta[0]);
                case 1 -> new FieldInfo("age", int.class, 0, null, new AnnotationMeta[0]);
                default -> throw new IllegalArgumentException("Unknown field");
            };
        }

        @Override
        public String[] getMethods() {
            return NO_METHOD_NAMES;
        }

        @Override
        public MethodInfo[] getMethod(int index) {
            return new MethodInfo[0];
        }

        @Override
        public MethodInfo[] getMethod(String name) {
            return new MethodInfo[0];
        }
    }

    private record TestHttpInputMessage(String json) implements HttpInputMessage {
        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }
    }

    private static final class TestHttpOutputMessage implements HttpOutputMessage {
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        @Override
        public OutputStream getBody() {
            return body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return new HttpHeaders();
        }

        String body() {
            return body.toString(StandardCharsets.UTF_8);
        }
    }
}
