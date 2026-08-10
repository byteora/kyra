package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.orm.query.Table;

public interface IdGenerator {
    Object generate(SqlExecutor sqlExecutor, Table<?> table, Object entity);
}
