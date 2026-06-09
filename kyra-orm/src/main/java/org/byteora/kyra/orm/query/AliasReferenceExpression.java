package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.runtime.dialect.RenderContext;
import org.byteora.kyra.orm.runtime.dialect.SqlDialects;

final class AliasReferenceExpression implements SqlExpression {
    private final String alias;

    AliasReferenceExpression(String alias) {
        this.alias = alias;
    }

    @Override
    public void appendTo(RenderContext context) {
        context.sql().append(SqlDialects.identifiers(context.dialect().dbType()).quote(alias));
    }
}
