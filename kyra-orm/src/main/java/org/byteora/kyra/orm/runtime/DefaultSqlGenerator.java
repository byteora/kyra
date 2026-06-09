package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.orm.query.EntityTable;
import org.byteora.kyra.orm.query.QueryDefinition;
import org.byteora.kyra.orm.query.UpdateDefinition;
import org.byteora.kyra.orm.query.WhereDefinition;
import org.byteora.kyra.orm.runtime.dialect.DefaultSqlDialectRegistry;
import org.byteora.kyra.orm.runtime.dialect.DeleteModel;
import org.byteora.kyra.orm.runtime.dialect.QueryModelMapper;
import org.byteora.kyra.orm.runtime.dialect.RenderContext;
import org.byteora.kyra.orm.runtime.dialect.SqlDialect;
import org.byteora.kyra.orm.runtime.dialect.SqlDialectRegistry;
import org.byteora.kyra.orm.runtime.dialect.UpdateModel;

import java.util.List;

public class DefaultSqlGenerator implements SqlGenerator {
    private final SqlDialectRegistry dialectRegistry;

    public DefaultSqlGenerator() {
        this(new DefaultSqlDialectRegistry());
    }

    public DefaultSqlGenerator(SqlDialectRegistry dialectRegistry) {
        this.dialectRegistry = dialectRegistry;
    }

    @Override
    public SqlRequest renderQuery(QueryDefinition definition, DbType dbType) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        return dialect.queryRenderer().render(QueryModelMapper.from(definition), new RenderContext(dialect));
    }

    @Override
    public SqlRequest rewriteCount(QueryDefinition definition, DbType dbType) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        return dialect.countQueryRewriter().rewrite(QueryModelMapper.from(definition), new RenderContext(dialect));
    }

    @Override
    public SqlRequest renderSelect(EntityTable<?> table, WhereDefinition whereDefinition, DbType dbType) {
        QueryDefinition definition = new QueryDefinition(
                java.util.List.of(),
                true,
                table,
                java.util.List.of(),
                java.util.List.of(),
                null,
                whereDefinition
        );
        return renderQuery(definition, dbType);
    }

    @Override
    public SqlRequest renderDelete(EntityTable<?> table, WhereDefinition whereDefinition, DbType dbType) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        return dialect.deleteRenderer().render(new DeleteModel(table, whereDefinition), new RenderContext(dialect));
    }

    @Override
    public SqlRequest renderUpdate(EntityTable<?> table, UpdateDefinition updateDefinition, DbType dbType) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        return dialect.updateRenderer().render(new UpdateModel(table, updateDefinition), new RenderContext(dialect));
    }

    @Override
    public SqlRequest renderInsert(EntityTable<?> table, List<String> columns, List<Object> args, DbType dbType) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        return dialect.insertRenderer().render(
                new org.byteora.kyra.orm.runtime.dialect.InsertModel(table, columns, args),
                new RenderContext(dialect)
        );
    }

    public SqlRequest appendPaging(String sql, Object[] args, DbType dbType, boolean dataChange, Integer limit, Integer offset) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        RenderContext context = new RenderContext(dialect);
        context.sql().append(sql);
        if (args != null) {
            context.args().addAll(List.of(args));
        }
        dialect.paging().render(new org.byteora.kyra.orm.runtime.dialect.PageClause(offset, limit, dataChange), context);
        return context.toRequest();
    }
}
