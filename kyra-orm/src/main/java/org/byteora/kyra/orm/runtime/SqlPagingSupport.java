package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.orm.query.Page;
import org.byteora.kyra.orm.query.Paging;

import org.byteora.kyra.core.runtime.RuntimeTypes;
import java.lang.reflect.Type;

public interface SqlPagingSupport {
    <T> Page<T> page(SqlExecutor sqlExecutor, SqlExecutionContext context, String sql, Object[] args, Paging paging, Class<T> elementType);

    @SuppressWarnings("unchecked")
    default <T> Page<T> page(SqlExecutor sqlExecutor, SqlExecutionContext context, String sql, Object[] args,
                             Paging paging, Type elementType) {
        return page(sqlExecutor, context, sql, args, paging, (Class<T>) RuntimeTypes.rawClass(elementType));
    }

    long count(SqlExecutor sqlExecutor, SqlExecutionContext context, String sql, Object[] args);
}
