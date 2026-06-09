package org.byteora.kyra.orm.runtime.dialect.impl;

import org.byteora.kyra.orm.runtime.dialect.PageClause;
import org.byteora.kyra.orm.runtime.dialect.PagingRenderer;
import org.byteora.kyra.orm.runtime.dialect.RenderContext;

public final class LimitOffsetPagingRenderer implements PagingRenderer {
    @Override
    public void render(PageClause pageClause, RenderContext context) {
        if (pageClause.limit() == null) {
            return;
        }
        context.sql().append(" LIMIT ?");
        context.args().add(pageClause.limit());
        if (pageClause.offset() != null) {
            context.sql().append(" OFFSET ?");
            context.args().add(pageClause.offset());
        }
    }
}
