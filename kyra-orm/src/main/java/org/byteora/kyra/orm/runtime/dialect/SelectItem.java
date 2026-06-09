package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.SqlExpression;

public record SelectItem(
        SqlExpression expression,
        String alias
) {
    public boolean aliased() {
        return alias != null && !alias.isBlank();
    }
}
