package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.orm.query.EntityTable;

public interface IdGenerator {
    Object generate(SqlExecutor sqlExecutor, EntityTable<?> entityTable, Object entity);
}
