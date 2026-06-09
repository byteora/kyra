package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.SqlExpression;

public record OrderItem(
        SqlExpression expression,
        boolean ascending
) {
}
