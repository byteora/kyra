package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.Table;
import org.byteora.kyra.orm.query.WhereDefinition;

public record DeleteModel(
        Table<?> table,
        WhereDefinition where
) {
}
