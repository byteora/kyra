package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.Table;

import java.util.List;

public record InsertModel(
        Table<?> table,
        List<String> columns,
        List<Object> args
) {
}
