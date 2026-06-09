package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.EntityTable;
import org.byteora.kyra.orm.query.WhereDefinition;

public record DeleteModel(
        EntityTable<?> table,
        WhereDefinition where
) {
}
