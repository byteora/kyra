package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.runtime.dialect.RenderContext;
import org.byteora.kyra.orm.runtime.dialect.SqlDialects;

record AliasedExpression(SqlExpression expression, String alias) implements NamedSqlExpression {
    @Override
    public SqlExpression source() {
        return expression;
    }

    @Override
    public void appendTo(RenderContext context) {
        expression.appendTo(context);
        context.sql().append(" AS ").append(SqlDialects.identifiers(context.dialect().dbType()).quote(alias));
    }
}
