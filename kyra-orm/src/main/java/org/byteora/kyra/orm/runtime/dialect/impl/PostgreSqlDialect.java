package org.byteora.kyra.orm.runtime.dialect.impl;

import org.byteora.kyra.orm.runtime.DbType;
import org.byteora.kyra.orm.runtime.dialect.DialectCapabilities;

public final class PostgreSqlDialect extends AbstractSqlDialect {
    public PostgreSqlDialect() {
        super(
                "postgresql",
                DbType.POSTGRESQL,
                NativeIdentifierPolicy.postgreSql(),
                new DialectCapabilities(false, false, false, true, true, true, true, false),
                new LimitOffsetPagingRenderer()
        );
    }
}
