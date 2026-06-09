package org.byteora.kyra.orm.runtime.dialect.impl;

import org.byteora.kyra.orm.runtime.SqlExecutorException;
import org.byteora.kyra.orm.runtime.dialect.PageClause;
import org.byteora.kyra.orm.runtime.dialect.PagingRenderer;
import org.byteora.kyra.orm.runtime.dialect.RenderContext;

public final class OffsetFetchPagingRenderer implements PagingRenderer {
    @Override
    public void render(PageClause pageClause, RenderContext context) {
        if (pageClause.limit() == null) {
            return;
        }
        if (pageClause.forDataMutation()) {
            throw new SqlExecutorException("Dialect " + context.dialect().id() + " does not support LIMIT on update/delete");
        }
        if (context.dialect().capabilities().requiresOrderByForOffsetFetch()
                && context.sql().indexOf(" ORDER BY ") < 0) {
            context.sql().append(" ORDER BY 1");
        }
        context.sql().append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        context.args().add(pageClause.offset() == null ? 0 : pageClause.offset());
        context.args().add(pageClause.limit());
    }
}
