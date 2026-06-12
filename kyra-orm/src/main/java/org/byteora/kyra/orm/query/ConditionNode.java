package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.runtime.DbType;
import org.byteora.kyra.orm.runtime.dialect.RenderContext;

import java.util.List;

/**
 * Internal {@link Condition} variant whose only required method is {@link #appendTo(RenderContext)}.
 *
 * <p>The legacy {@code appendTo(StringBuilder, List, DbType)} entry point is provided here as a
 * default bridge so concrete nodes implement rendering once. This interface is intentionally a
 * functional interface (single abstract method), whereas {@link Condition} keeps two abstract
 * methods so that fluent overloads such as {@code where(Condition)} versus
 * {@code where(Consumer<PredicateBuilder>)} stay unambiguous for lambda arguments.
 */
@FunctionalInterface
interface ConditionNode extends Condition {

    @Override
    void appendTo(RenderContext context);

    @Override
    default void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
        appendTo(RenderContext.bridge(dbType, sql, args));
    }
}
