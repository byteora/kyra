package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.Table;
import org.byteora.kyra.orm.query.UpdateDefinition;

public record UpdateModel(
        Table<?> table,
        UpdateDefinition definition
) {
}
