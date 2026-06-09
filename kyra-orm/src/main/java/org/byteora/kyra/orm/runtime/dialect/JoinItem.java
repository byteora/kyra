package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.Condition;
import org.byteora.kyra.orm.query.EntityTable;

public record JoinItem(
        String joinType,
        EntityTable<?> table,
        Condition on
) {
}
