package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.runtime.dialect.RenderContext;

record RawExpression(String value) implements SqlExpression {
    @Override
    public void appendTo(RenderContext context) {
        context.sql().append(value);
    }
}
