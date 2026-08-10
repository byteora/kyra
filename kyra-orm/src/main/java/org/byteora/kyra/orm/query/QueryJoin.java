package org.byteora.kyra.orm.query;

public record QueryJoin(String joinType, Table<?> table, Condition on) {
}
