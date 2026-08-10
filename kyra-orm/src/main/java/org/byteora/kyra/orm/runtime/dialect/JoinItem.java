package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.Condition;
import org.byteora.kyra.orm.query.Table;

public record JoinItem(
        String joinType,
        Table<?> table,
        Condition on
) {
}
