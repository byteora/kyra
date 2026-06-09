package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.runtime.DbType;
import org.byteora.kyra.orm.runtime.SqlExecutorException;
import org.byteora.kyra.orm.runtime.dialect.impl.PostgreSqlDialect;
import org.byteora.kyra.orm.runtime.dialect.impl.StandardLimitDialect;
import org.byteora.kyra.orm.runtime.dialect.impl.StandardOffsetFetchDialect;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public final class DefaultSqlDialectRegistry implements SqlDialectRegistry {
    private final Map<DbType, SqlDialect> dialects;

    public DefaultSqlDialectRegistry() {
        this.dialects = createDialects();
    }

    @Override
    public SqlDialect require(DbType dbType) {
        SqlDialect dialect = dialects.get(dbType);
        if (dialect == null) {
            throw new SqlExecutorException("No SqlDialect registered for DbType: " + dbType);
        }
        return dialect;
    }

    @Override
    public Collection<SqlDialect> all() {
        return dialects.values();
    }

    private Map<DbType, SqlDialect> createDialects() {
        EnumMap<DbType, SqlDialect> values = new EnumMap<>(DbType.class);
        values.put(DbType.MYSQL, StandardLimitDialect.mysql());
        values.put(DbType.MARIADB, StandardLimitDialect.mariaDb());
        values.put(DbType.SQLITE, StandardLimitDialect.sqlite());
        values.put(DbType.H2, StandardLimitDialect.h2());
        values.put(DbType.POSTGRESQL, new PostgreSqlDialect());
        values.put(DbType.ORACLE, StandardOffsetFetchDialect.oracle());
        values.put(DbType.SQLSERVER, StandardOffsetFetchDialect.sqlServer());
        return Map.copyOf(values);
    }
}
