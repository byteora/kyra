package org.byteora.kyra.orm.runtime;

public abstract class AbstractMapper<T> {
    protected final Class<T> type;
    protected final SqlExecutor sqlExecutor;

    protected AbstractMapper(SqlExecutor sqlExecutor, Class<T> type) {
        this.sqlExecutor = sqlExecutor;
        this.type = type;
    }
}
