package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.runtime.dialect.RenderContext;

public record Order(SqlExpression expression, boolean ascending) {
    public void appendTo(RenderContext context) {
        expression.appendTo(context);
        context.sql().append(ascending ? " ASC" : " DESC");
    }
}
