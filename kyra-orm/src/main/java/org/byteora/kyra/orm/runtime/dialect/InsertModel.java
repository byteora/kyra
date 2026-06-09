package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.EntityTable;

import java.util.List;

public record InsertModel(
        EntityTable<?> table,
        List<String> columns,
        List<Object> args
) {
}
