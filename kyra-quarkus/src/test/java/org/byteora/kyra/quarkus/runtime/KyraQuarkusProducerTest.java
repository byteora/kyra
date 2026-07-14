package org.byteora.kyra.quarkus.runtime;

import org.byteora.kyra.orm.runtime.DefaultSqlGenerator;
import org.byteora.kyra.orm.runtime.DefaultSqlPagingSupport;
import org.byteora.kyra.orm.runtime.SqlExecutor;
import org.byteora.kyra.orm.runtime.SqlGenerator;
import org.byteora.kyra.orm.runtime.SqlInterceptor;
import org.byteora.kyra.orm.runtime.SqlPagingSupport;
import org.byteora.kyra.orm.runtime.TypeConverter;
import org.byteora.kyra.quarkus.QuarkusSqlExecutor;
import org.byteora.kyra.json.JsonMapper;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class KyraQuarkusProducerTest {
    @Test
    void shouldCreateDefaultJsonMapper() {
        JsonMapper jsonMapper = new KyraQuarkusProducer().jsonMapper();

        assertNotNull(jsonMapper);
        assertEquals("{\"value\":1}", jsonMapper.toJson(java.util.Map.of("value", 1)));
    }

    @Test
    void shouldCreateSqlExecutorWithInjectedCollaborators() {
        KyraQuarkusProducer producer = new KyraQuarkusProducer();
        TypeConverter typeConverter = new TypeConverter();
        SqlPagingSupport pagingSupport = new DefaultSqlPagingSupport();
        SqlGenerator sqlGenerator = new DefaultSqlGenerator();
        SqlInterceptor interceptor = (context, request) -> request;

        SqlExecutor sqlExecutor = producer.sqlExecutor(
                new TestInstance<>(new NoopDataSource()),
                new TestInstance<>(typeConverter),
                new TestInstance<>(pagingSupport),
                new TestInstance<>(sqlGenerator),
                new TestInstance<>(List.of(interceptor))
        );

        QuarkusSqlExecutor executor = assertInstanceOf(QuarkusSqlExecutor.class, sqlExecutor);
        assertSame(typeConverter, executor.getTypeConverter());
        assertSame(pagingSupport, executor.getSqlPagingSupport());
        assertSame(sqlGenerator, executor.getSqlGenerator());
        assertEquals(List.of(interceptor), executor.getInterceptors());
    }

    private static final class NoopDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }

    private static final class TestInstance<T> implements Instance<T> {
        private final List<T> values;

        private TestInstance(T value) {
            this.values = List.of(value);
        }

        private TestInstance(List<T> values) {
            this.values = values;
        }

        @Override
        public Instance<T> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            return values.isEmpty();
        }

        @Override
        public boolean isAmbiguous() {
            return values.size() > 1;
        }

        @Override
        public void destroy(T instance) {
        }

        @Override
        public Handle<T> getHandle() {
            T value = get();
            return new Handle<>() {
                @Override
                public T get() {
                    return value;
                }

                @Override
                public void destroy() {
                }

                @Override
                public void close() {
                }

                @Override
                public Bean<T> getBean() {
                    return null;
                }
            };
        }

        @Override
        public Iterable<? extends Handle<T>> handles() {
            return values.stream().map(value -> new Handle<T>() {
                @Override
                public T get() {
                    return value;
                }

                @Override
                public void destroy() {
                }

                @Override
                public void close() {
                }

                @Override
                public Bean<T> getBean() {
                    return null;
                }
            }).toList();
        }

        @Override
        public Iterator<T> iterator() {
            return values.iterator();
        }

        @Override
        public T get() {
            return values.getFirst();
        }
    }
}
