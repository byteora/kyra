package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.runtime.DbType;

public interface SqlDialect {
    String id();

    DbType dbType();

    IdentifierPolicy identifiers();

    DialectCapabilities capabilities();

    PagingRenderer paging();

    QueryRenderer queryRenderer();

    UpdateRenderer updateRenderer();

    DeleteRenderer deleteRenderer();

    InsertRenderer insertRenderer();

    CountQueryRewriter countQueryRewriter();
}
