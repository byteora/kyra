package org.byteora.kyra.orm.runtime.dialect.impl;

import org.byteora.kyra.orm.runtime.DbType;
import org.byteora.kyra.orm.runtime.dialect.*;

public abstract class AbstractSqlDialect implements SqlDialect {
    private final String id;
    private final DbType dbType;
    private final IdentifierPolicy identifiers;
    private final DialectCapabilities capabilities;
    private final PagingRenderer pagingRenderer;
    private final QueryRenderer queryRenderer = new QueryDefinitionRenderer();
    private final UpdateRenderer updateRenderer = new UpdateDefinitionRenderer();
    private final DeleteRenderer deleteRenderer = new DeleteDefinitionRenderer();
    private final InsertRenderer insertRenderer = new InsertDefinitionRenderer();
    private final CountQueryRewriter countQueryRewriter = new DefaultCountQueryRewriter();

    protected AbstractSqlDialect(String id,
                                 DbType dbType,
                                 IdentifierPolicy identifiers,
                                 DialectCapabilities capabilities,
                                 PagingRenderer pagingRenderer) {
        this.id = id;
        this.dbType = dbType;
        this.identifiers = identifiers;
        this.capabilities = capabilities;
        this.pagingRenderer = pagingRenderer;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public DbType dbType() {
        return dbType;
    }

    @Override
    public IdentifierPolicy identifiers() {
        return identifiers;
    }

    @Override
    public DialectCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public PagingRenderer paging() {
        return pagingRenderer;
    }

    @Override
    public QueryRenderer queryRenderer() {
        return queryRenderer;
    }

    @Override
    public UpdateRenderer updateRenderer() {
        return updateRenderer;
    }

    @Override
    public DeleteRenderer deleteRenderer() {
        return deleteRenderer;
    }

    @Override
    public InsertRenderer insertRenderer() {
        return insertRenderer;
    }

    @Override
    public CountQueryRewriter countQueryRewriter() {
        return countQueryRewriter;
    }
}
