package org.byteora.kyra.orm.query;

import java.util.List;

public record QueryDefinition(
        List<SqlExpression> selectExpressions,
        boolean selectAll,
        Table<?> from,
        List<QueryJoin> joins,
        List<SqlExpression> groupByExpressions,
        Condition having,
        WhereDefinition where
) {
}
