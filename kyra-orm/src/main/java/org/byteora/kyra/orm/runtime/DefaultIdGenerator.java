package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.orm.query.Table;

import java.util.UUID;

public final class DefaultIdGenerator implements IdGenerator {
    public static final DefaultIdGenerator INSTANCE = new DefaultIdGenerator();

    private DefaultIdGenerator() {
    }

    @Override
    public Object generate(SqlExecutor sqlExecutor, Table<?> table, Object entity) {
        return switch (table.idStrategy()) {
            case NONE -> null;
            case UUID -> generateUuid(table);
            case CUSTOM -> generateCustom(sqlExecutor, table, entity);
        };
    }

    private Object generateUuid(Table<?> table) {
        UUID uuid = UUID.randomUUID();
        Class<?> javaType = table.idColumn().javaType();
        if (javaType == UUID.class) {
            return uuid;
        }
        if (javaType == String.class) {
            return uuid.toString();
        }
        throw new SqlExecutorException("UUID id strategy only supports String or UUID fields: "
                + table.type().getName() + "." + table.fieldName(table.idColumn().columnName()));
    }

    private Object generateCustom(SqlExecutor sqlExecutor, Table<?> table, Object entity) {
        IdGenerator generator = table.idGenerator();
        if (generator != null) {
            return generator.generate(sqlExecutor, table, entity);
        }
        IdGenerator executorGenerator = sqlExecutor.getIdGenerator();
        if (executorGenerator != null) {
            return executorGenerator.generate(sqlExecutor, table, entity);
        }
        throw new SqlExecutorException("No custom id generator configured for type: " + table.type().getName());
    }
}
