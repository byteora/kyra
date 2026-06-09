package org.byteora.kyra.orm.runtime;

public interface SqlInterceptor {
    SqlRequest intercept(SqlExecutionContext context, SqlRequest request);
}
