package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.runtime.dialect.RenderContext;

record LiteralExpression(Object value) implements SqlExpression {
    @Override
    public void appendTo(RenderContext context) {
        context.sql().append('?');
        context.args().add(value);
    }
}
