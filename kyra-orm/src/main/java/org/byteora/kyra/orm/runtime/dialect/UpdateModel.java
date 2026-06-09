package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.EntityTable;
import org.byteora.kyra.orm.query.UpdateDefinition;

public record UpdateModel(
        EntityTable<?> table,
        UpdateDefinition definition
) {
}
