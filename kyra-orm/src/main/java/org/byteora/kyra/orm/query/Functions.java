package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.runtime.dialect.RenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Functions {
    private static final SqlExpression STAR = Expressions.raw("*");

    private Functions() {
    }

    public static SqlExpression count() {
        return count(STAR);
    }

    public static SqlExpression count(SqlExpression expression) {
        return simple("COUNT", expression);
    }

    public static SqlExpression avg(SqlExpression expression) {
        return simple("AVG", expression);
    }

    public static SqlExpression max(SqlExpression expression) {
        return simple("MAX", expression);
    }

    public static SqlExpression min(SqlExpression expression) {
        return simple("MIN", expression);
    }

    public static SqlExpression ifElse(Condition condition, SqlExpression whenTrue, SqlExpression whenFalse) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(whenTrue, "whenTrue");
        Objects.requireNonNull(whenFalse, "whenFalse");
        return new FunctionExpression(context -> appendIf(context, condition, whenTrue, whenFalse));
    }

    public static SqlExpression ifElse(Condition condition, Object whenTrue, Object whenFalse) {
        return ifElse(condition, Expressions.literal(whenTrue), Expressions.literal(whenFalse));
    }

    /**
     * Starts a multi-branch {@code CASE WHEN} expression. Chain additional branches with
     * {@link CaseBuilder#when}, then terminate with {@link CaseBuilder#orElse} (adds {@code ELSE})
     * or {@link CaseBuilder#end} (no {@code ELSE}). Each condition may itself combine predicates
     * with {@link Conditions#and} / {@link Conditions#or}.
     */
    public static CaseBuilder caseWhen(Condition condition, SqlExpression result) {
        return new CaseBuilder().when(condition, result);
    }

    public static CaseBuilder caseWhen(Condition condition, Object result) {
        return caseWhen(condition, Expressions.literal(result));
    }

    /**
     * Fluent builder for a {@code CASE WHEN ... THEN ... [WHEN ...] [ELSE ...] END} expression,
     * rendered identically across dialects (MySQL included). Use {@link Functions#caseWhen} to start.
     */
    public static final class CaseBuilder {
        private final List<CaseBranch> branches = new ArrayList<>();

        private CaseBuilder() {
        }

        public CaseBuilder when(Condition condition, SqlExpression result) {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(result, "result");
            branches.add(new CaseBranch(condition, result));
            return this;
        }

        public CaseBuilder when(Condition condition, Object result) {
            return when(condition, Expressions.literal(result));
        }

        public SqlExpression orElse(SqlExpression result) {
            Objects.requireNonNull(result, "result");
            return build(result);
        }

        public SqlExpression orElse(Object result) {
            return orElse(Expressions.literal(result));
        }

        public SqlExpression end() {
            return build(null);
        }

        private SqlExpression build(SqlExpression elseResult) {
            List<CaseBranch> snapshot = List.copyOf(branches);
            return new FunctionExpression(context -> appendCase(context, snapshot, elseResult));
        }
    }

    private record CaseBranch(Condition condition, SqlExpression result) {
    }

    private static SqlExpression simple(String functionName, SqlExpression expression) {
        Objects.requireNonNull(expression, "expression");
        return new FunctionExpression(context -> {
            context.sql().append(functionName).append('(');
            expression.appendTo(context);
            context.sql().append(')');
        });
    }

    private static void appendIf(RenderContext context,
                                 Condition condition,
                                 SqlExpression whenTrue,
                                 SqlExpression whenFalse) {
        switch (context.dialect().dbType()) {
            case MYSQL, MARIADB -> {
                context.sql().append("IF(");
                condition.appendTo(context);
                context.sql().append(", ");
                whenTrue.appendTo(context);
                context.sql().append(", ");
                whenFalse.appendTo(context);
                context.sql().append(')');
            }
            default -> {
                context.sql().append("CASE WHEN ");
                condition.appendTo(context);
                context.sql().append(" THEN ");
                whenTrue.appendTo(context);
                context.sql().append(" ELSE ");
                whenFalse.appendTo(context);
                context.sql().append(" END");
            }
        }
    }

    private static void appendCase(RenderContext context, List<CaseBranch> branches, SqlExpression elseResult) {
        StringBuilder sql = context.sql();
        sql.append("CASE");
        for (CaseBranch branch : branches) {
            sql.append(" WHEN ");
            branch.condition().appendTo(context);
            sql.append(" THEN ");
            branch.result().appendTo(context);
        }
        if (elseResult != null) {
            sql.append(" ELSE ");
            elseResult.appendTo(context);
        }
        sql.append(" END");
    }
}
