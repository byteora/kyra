package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.annotation.IdStrategy;
import org.byteora.kyra.orm.runtime.DbType;
import org.byteora.kyra.orm.runtime.IdGenerator;
import org.byteora.kyra.orm.runtime.dialect.SqlDialects;

public abstract class Table<T> {
    private final Class<T> type;
    private final String tableName;
    private final String alias;

    protected Table(Class<T> type, String tableName) {
        this(type, tableName, null);
    }

    protected Table(Class<T> type, String tableName, String alias) {
        this.type = type;
        this.tableName = tableName;
        this.alias = alias;
    }

    public final Class<T> type() {
        return type;
    }

    public final String tableName() {
        return tableName;
    }

    public final String alias() {
        return alias;
    }

    public final String tableReference(DbType dbType) {
        return SqlDialects.identifiers(dbType).tableReference(tableName, alias);
    }

    public final String qualifier() {
        return alias == null || alias.isBlank() ? tableName : alias;
    }

    public final String qualifier(DbType dbType) {
        return SqlDialects.identifiers(dbType).quote(qualifier());
    }

    protected final <V> Column<T, V> column(String columnName, Class<V> javaType) {
        return new Column<>(this, columnName, javaType);
    }

    public final Column<T, Object> columnRef(String columnName) {
        return new Column<>(this, columnName, Object.class);
    }

    public IdStrategy idStrategy() {
        return IdStrategy.NONE;
    }

    public IdGenerator idGenerator() {
        return null;
    }

    public abstract Column<T, ?> idColumn();
    public abstract String fieldName(String column);
    public abstract String columnName(String field);
}
