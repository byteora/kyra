package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.orm.query.Page;
import org.byteora.kyra.orm.query.Paging;

public interface SqlPagingSupport {
    <T> Page<T> page(SqlExecutor sqlExecutor, SqlExecutionContext context, String sql, Object[] args, Paging paging, Class<T> elementType);

    long count(SqlExecutor sqlExecutor, SqlExecutionContext context, String sql, Object[] args);
}
