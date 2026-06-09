package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.runtime.DbType;

import java.util.Collection;

public interface SqlDialectRegistry {
    SqlDialect require(DbType dbType);

    Collection<SqlDialect> all();
}
