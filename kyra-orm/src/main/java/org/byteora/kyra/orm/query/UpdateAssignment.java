package org.byteora.kyra.orm.query;

public record UpdateAssignment(Column<?, ?> column, SqlExpression value) {
}
