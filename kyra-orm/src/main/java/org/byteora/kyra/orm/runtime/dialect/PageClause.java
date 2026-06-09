package org.byteora.kyra.orm.runtime.dialect;

public record PageClause(
        Integer offset,
        Integer limit,
        boolean forDataMutation
) {
}
