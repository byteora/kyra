package org.byteora.kyra.orm.runtime.dialect;

public interface PagingRenderer {
    void render(PageClause pageClause, RenderContext context);
}
