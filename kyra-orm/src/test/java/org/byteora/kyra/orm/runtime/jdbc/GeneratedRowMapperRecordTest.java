package org.byteora.kyra.orm.runtime.jdbc;

import org.byteora.kyra.core.annotation.Alias;
import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.runtime.GeneratedReflectors;
import org.byteora.kyra.orm.runtime.TypeConverter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GeneratedRowMapperRecordTest {
    @Test
    void shouldInstantiateRecordWithoutSetterAccess() throws Exception {
        GeneratedRowMapper<PairRecord> rowMapper = new GeneratedRowMapper<>(PairRecord.class, GeneratedReflectors.load(PairRecord.class), new TypeConverter());

        PairRecord pair = rowMapper.mapRow(resultSet(new String[]{"key", "value"}, new Object[]{"day1", 12L}));

        assertEquals("day1", pair.key());
        assertEquals(12L, pair.value());
    }

    @Test
    void shouldInstantiatePojoWithConstructorAndSetterMix() throws Exception {
        GeneratedRowMapper<HybridUser> rowMapper = new GeneratedRowMapper<>(HybridUser.class, GeneratedReflectors.load(HybridUser.class), new TypeConverter());

        HybridUser user = rowMapper.mapRow(resultSet(new String[]{"id", "age", "display_name"}, new Object[]{"u1", 18, "neo"}));

        assertEquals("u1", user.getId());
        assertEquals(18, user.getAge());
        assertEquals("neo", user.getDisplayName());
    }

    @Test
    void shouldMapSnakeCaseColumnToCamelCaseFieldWithoutAlias() throws Exception {
        GeneratedRowMapper<PlainUser> rowMapper = new GeneratedRowMapper<>(PlainUser.class, GeneratedReflectors.load(PlainUser.class), new TypeConverter());

        PlainUser user = rowMapper.mapRow(resultSet(new String[]{"user_id", "first_name"}, new Object[]{"42", "ada"}));

        assertEquals("42", user.getUserId());
        assertEquals("ada", user.getFirstName());
    }

    @Test
    void shouldMapUppercasedColumnLabelsCaseInsensitively() throws Exception {
        GeneratedRowMapper<PlainUser> rowMapper = new GeneratedRowMapper<>(PlainUser.class, GeneratedReflectors.load(PlainUser.class), new TypeConverter());

        PlainUser user = rowMapper.mapRow(resultSet(new String[]{"USER_ID", "FIRST_NAME"}, new Object[]{"7", "grace"}));

        assertEquals("7", user.getUserId());
        assertEquals("grace", user.getFirstName());
    }

    @Test
    void shouldReuseColumnPlanAcrossRows() throws Exception {
        GeneratedRowMapper<PlainUser> rowMapper = new GeneratedRowMapper<>(PlainUser.class, GeneratedReflectors.load(PlainUser.class), new TypeConverter());
        int[] metadataCalls = new int[1];

        ResultSet rs = recordingResultSet(new String[]{"user_id", "first_name"}, new Object[]{"1", "alpha"}, metadataCalls);
        PlainUser row1 = rowMapper.mapRow(rs);
        PlainUser row2 = rowMapper.mapRow(rs);

        assertEquals("1", row1.getUserId());
        assertEquals("alpha", row1.getFirstName());
        assertEquals("1", row2.getUserId());
        assertEquals(1, metadataCalls[0], "column plan should be cached after the first row");
    }

    private ResultSet resultSet(String[] columns, Object[] values) {
        return recordingResultSet(columns, values, new int[1]);
    }

    private ResultSet recordingResultSet(String[] columns, Object[] values, int[] metadataCalls) {
        boolean[] lastWasNull = new boolean[1];
        ResultSetMetaData metaData = (ResultSetMetaData) Proxy.newProxyInstance(
                ResultSetMetaData.class.getClassLoader(),
                new Class[]{ResultSetMetaData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getColumnCount" -> columns.length;
                    case "getColumnLabel" -> columns[(Integer) args[0] - 1];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMetaData" -> {
                        metadataCalls[0]++;
                        yield metaData;
                    }
                    case "getObject" -> {
                        Object value = values[(Integer) args[0] - 1];
                        lastWasNull[0] = value == null;
                        yield value;
                    }
                    case "getString" -> {
                        Object value = values[(Integer) args[0] - 1];
                        lastWasNull[0] = value == null;
                        yield value == null ? null : String.valueOf(value);
                    }
                    case "getInt" -> {
                        Object value = values[(Integer) args[0] - 1];
                        lastWasNull[0] = value == null;
                        yield value == null ? 0 : ((Number) value).intValue();
                    }
                    case "getLong" -> {
                        Object value = values[(Integer) args[0] - 1];
                        lastWasNull[0] = value == null;
                        yield value == null ? 0L : ((Number) value).longValue();
                    }
                    case "wasNull" -> lastWasNull[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @Reflect
    public record PairRecord(String key, Long value) {
    }

    @Reflect
    public static final class HybridUser {
        private final String id;
        private Integer age;
        @Alias("display_name")
        private String displayName;

        public HybridUser(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    /** Plain POJO that deliberately has no {@code @Alias} annotations so snake↔camel mapping is exercised. */
    @Reflect
    public static final class PlainUser {
        private String userId;
        private String firstName;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
    }
}
