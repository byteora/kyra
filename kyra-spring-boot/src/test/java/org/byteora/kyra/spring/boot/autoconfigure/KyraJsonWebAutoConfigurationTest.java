package org.byteora.kyra.spring.boot.autoconfigure;

import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.runtime.GeneratedReflectors;
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
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KyraJsonWebAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KyraJsonWebAutoConfiguration.class));

    @BeforeEach
    void setUp() {
        ReflectorRegistry.clear();
        ReflectorRegistry.register(WebUser.class, GeneratedReflectors.load(WebUser.class));
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

    @Reflect
    public static final class WebUser {
        public String name;
        public int age;
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
