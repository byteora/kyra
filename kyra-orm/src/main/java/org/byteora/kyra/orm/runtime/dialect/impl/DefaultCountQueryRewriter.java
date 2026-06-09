package org.byteora.kyra.orm.runtime.dialect.impl;

import org.byteora.kyra.orm.runtime.SqlRequest;
import org.byteora.kyra.orm.runtime.dialect.CountQueryRewriter;
import org.byteora.kyra.orm.runtime.dialect.QueryModel;
import org.byteora.kyra.orm.runtime.dialect.RenderContext;

public final class DefaultCountQueryRewriter implements CountQueryRewriter {
    @Override
    public SqlRequest rewrite(QueryModel queryModel, RenderContext context) {
        SqlRequest query = context.dialect().queryRenderer().render(queryModel, context);
        return new SqlRequest("select count(*) from (" + query.getSql() + ") _kyra_count", query.getArgs());
    }
}
