package org.byteora.kyra.orm.query;

public record QueryJoin(String joinType, EntityTable<?> table, Condition on) {
}
