package com.example.simple;

import com.example.simple.common.Pair;
import com.example.simple.dto.UserSummary;
import com.example.simple.entity.User;
import com.example.simple.mapper.ConcreteMultiTypeUserMapper;
import com.example.simple.mapper.ConcreteMultiTypeUserMapperImpl;
import com.example.simple.mapper.MixedUserMapper;
import com.example.simple.mapper.MixedUserMapperImpl;
import com.example.simple.mapper.PairUserMapper;
import com.example.simple.mapper.RawMultiTypeUserMapper;
import com.example.simple.mapper.RawMultiTypeUserMapperImpl;
import com.example.simple.mapper.UserMapper;
import com.example.simple.support.CapturingSqlExecutor;
import com.example.simple.support.GeneratedTypeNames;
import com.example.simple.support.NoopSqlExecutor;
import org.byteora.kyra.orm.runtime.AbstractMapper;
import org.byteora.kyra.core.runtime.AnnotationMeta;
import org.byteora.kyra.orm.runtime.DbType;
import org.byteora.kyra.orm.runtime.SqlExecutionContext;
import org.byteora.kyra.orm.runtime.SqlExecutor;
import org.byteora.kyra.orm.runtime.jdbc.DefaultSqlExecutor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MapperFeaturesTest {
    @Nested
    class GenericCapabilities {
        @Test
        void mixedCapabilityMapperShouldResolvePerCapabilityGenericTypes() {
            MixedUserMapper mapper = new MixedUserMapperImpl(new NoopSqlExecutor());

            assertInstanceOf(MixedUserMapperImpl.class, mapper);
            assertFalse(AbstractMapper.class.isAssignableFrom(MixedUserMapperImpl.class));
            assertEquals(UserSummary.class.getName(), mapper.mappedTypeName());
        }

        @Test
        void rawMultiGenericCapabilityShouldFallbackToNullEntityClass() {
            RawMultiTypeUserMapper mapper = new RawMultiTypeUserMapperImpl(new NoopSqlExecutor());

            assertNotNull(mapper);
            assertEquals("null", mapper.firstTypeName());
        }

        @Test
        void concreteMultiGenericCapabilityShouldUseFirstGenericType() {
            ConcreteMultiTypeUserMapper mapper = new ConcreteMultiTypeUserMapperImpl(new NoopSqlExecutor());

            assertNotNull(mapper);
            assertEquals(UserSummary.class.getName(), mapper.firstTypeName());
        }
    }

    @Nested
    class MethodAnnotations {
        @Test
        void shouldPassCompiledMethodAnnotationsToSqlInterceptorContext() throws Exception {
            CapturingSqlExecutor sqlExecutor = new CapturingSqlExecutor();
            UserMapper mapper = newUserMapper(sqlExecutor);

            mapper.selectById(1L);

            AnnotationMeta annotation = (AnnotationMeta) SqlExecutionContext.class
                    .getMethod("getMapperMethodAnnotation", String.class)
                    .invoke(sqlExecutor.capturedContext(), TestMapperTag.class.getName());
            assertNotNull(annotation);
            assertEquals(TestMapperTag.class.getName(), annotation.type());
            assertEquals("selectById", annotation.value("value"));
            assertEquals(7, annotation.value("order"));
            assertEquals(true, annotation.value("enabled"));
        }

        @Test
        void shouldCopyRuntimeAnnotationsFromInterfaceMethod() throws Exception {
            java.lang.reflect.Method method = Class.forName("com.example.simple.mapper.UserMapperImpl")
                    .getMethod("selectById", Long.class);

            TestMapperTag annotation = method.getAnnotation(TestMapperTag.class);

            assertNotNull(annotation);
            assertEquals("selectById", annotation.value());
            assertEquals(7, annotation.order());
            assertEquals(true, annotation.enabled());
        }

        @Test
        void generatedMapperImplShouldNotBeFinal() throws Exception {
            Class<?> mapperImpl = Class.forName("com.example.simple.mapper.UserMapperImpl");
            assertFalse(java.lang.reflect.Modifier.isFinal(mapperImpl.getModifiers()));
        }

        @Test
        void updateMapperMethodShouldUseValidOperandStackOrder() throws Exception {
            CapturingSqlExecutor sqlExecutor = new CapturingSqlExecutor();
            UserMapper mapper = newUserMapper(sqlExecutor);

            int rows = mapper.expireUsers(LocalDateTime.of(2026, 5, 11, 15, 44));

            assertEquals(1, rows);
            assertEquals(org.byteora.kyra.orm.xml.SqlCommandType.UPDATE, sqlExecutor.capturedContext().getCommandType());
        }
    }

    @Nested
    class PairRecordMapping {
        @Test
        void shouldGenerateMapperAndRecordReflector() throws ClassNotFoundException {
            assertNotNull(Class.forName("com.example.simple.mapper.PairUserMapperImpl"));
            assertNotNull(Class.forName(GeneratedTypeNames.reflectorTypeName(Pair.class)));
        }

        @Test
        void shouldPreserveNestedListElementGenericTypes() throws Exception {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:pair-generic-result;MODE=MYSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            initializeUsers(dataSource);

            DefaultSqlExecutor sqlExecutor = new DefaultSqlExecutor(dataSource);
            sqlExecutor.setDbType(DbType.H2);
            PairUserMapper mapper = newPairUserMapper(sqlExecutor);

            List<Pair<String, Long>> results = mapper.memberNumOfDay(
                    LocalDateTime.of(2026, 4, 1, 0, 0),
                    LocalDateTime.of(2026, 4, 2, 0, 0)
            );

            assertEquals(2, results.size());
            assertEquals("alice", results.get(0).key());
            assertEquals(2L, results.get(0).value());
            assertInstanceOf(String.class, results.get(0).key());
            assertInstanceOf(Long.class, results.get(0).value());
        }
    }

    private UserMapper newUserMapper(SqlExecutor sqlExecutor) throws Exception {
        return (UserMapper) Class.forName("com.example.simple.mapper.UserMapperImpl")
                .getConstructor(SqlExecutor.class)
                .newInstance(sqlExecutor);
    }

    private PairUserMapper newPairUserMapper(SqlExecutor sqlExecutor) throws Exception {
        return (PairUserMapper) Class.forName("com.example.simple.mapper.PairUserMapperImpl")
                .getConstructor(SqlExecutor.class)
                .newInstance(sqlExecutor);
    }

    private void initializeUsers(JdbcDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table users (
                        id bigint primary key,
                        name varchar(64),
                        created_time timestamp
                    )
                    """);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "insert into users(id, name, created_time) values (?, ?, ?)")) {
            insertUser(statement, 1L, "alice", LocalDateTime.of(2026, 4, 1, 10, 0));
            insertUser(statement, 2L, "alice", LocalDateTime.of(2026, 4, 1, 11, 0));
            insertUser(statement, 3L, "bob", LocalDateTime.of(2026, 4, 1, 12, 0));
            statement.executeBatch();
        }
    }

    private void insertUser(PreparedStatement statement, long id, String name, LocalDateTime createdTime) throws Exception {
        statement.setLong(1, id);
        statement.setString(2, name);
        statement.setObject(3, createdTime);
        statement.addBatch();
    }
}
