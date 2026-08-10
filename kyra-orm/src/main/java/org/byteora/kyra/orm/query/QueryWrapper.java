package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.runtime.SqlExecutionContext;
import org.byteora.kyra.orm.runtime.SqlExecutor;
import org.byteora.kyra.orm.runtime.SqlRequest;
import org.byteora.kyra.orm.runtime.SqlExecutorException;
import org.byteora.kyra.orm.xml.SqlCommandType;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class QueryWrapper<R> {
    private final List<SqlExpression> selectExpressions = new ArrayList<>();
    private final List<JoinSpec> joins = new ArrayList<>();
    private final List<SqlExpression> groupByExpressions = new ArrayList<>();
    private final WhereWrapper whereWrapper = new WhereWrapper();
    private final SqlExecutor sqlExecutor;
    private final Type resultType;
    private Condition having;
    private Table<?> from;
    private boolean explicitSelect;
    private boolean selectAll;

    QueryWrapper(SqlExecutor sqlExecutor, Type resultType) {
        this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "sqlExecutor");
        this.resultType = Objects.requireNonNull(resultType, "resultType");
    }

    public QueryWrapper<R> select(SqlExpression... expressions) {
        if (selectAll) {
            throw new SqlExecutorException("select(...) and selectAll() are mutually exclusive");
        }
        explicitSelect = true;
        Collections.addAll(selectExpressions, expressions);
        return this;
    }

    public QueryWrapper<R> selectAll() {
        if (explicitSelect) {
            throw new SqlExecutorException("selectAll() and select(...) are mutually exclusive");
        }
        this.selectAll = true;
        return this;
    }

    public QueryWrapper<R> from(Table<?> table) {
        this.from = Objects.requireNonNull(table, "table");
        return this;
    }

    public JoinStep<R> leftJoin(Table<?> table) {
        return new JoinStep<>(this, "LEFT JOIN", table);
    }

    public QueryWrapper<R> leftJoin(Table<?> table, Condition on) {
        return leftJoin(table).on(on);
    }

    public QueryWrapper<R> leftJoin(Table<?> table, Consumer<PredicateBuilder> on) {
        return leftJoin(table).on(on);
    }

    public JoinStep<R> innerJoin(Table<?> table) {
        return new JoinStep<>(this, "INNER JOIN", table);
    }

    public QueryWrapper<R> innerJoin(Table<?> table, Condition on) {
        return innerJoin(table).on(on);
    }

    public QueryWrapper<R> innerJoin(Table<?> table, Consumer<PredicateBuilder> on) {
        return innerJoin(table).on(on);
    }

    public JoinStep<R> rightJoin(Table<?> table) {
        return new JoinStep<>(this, "RIGHT JOIN", table);
    }

    public QueryWrapper<R> rightJoin(Table<?> table, Condition on) {
        return rightJoin(table).on(on);
    }

    public QueryWrapper<R> rightJoin(Table<?> table, Consumer<PredicateBuilder> on) {
        return rightJoin(table).on(on);
    }

    public QueryWrapper<R> where(Consumer<PredicateBuilder> consumer) {
        whereWrapper.where(consumer);
        return this;
    }

    public QueryWrapper<R> where(Condition condition) {
        whereWrapper.condition(condition);
        return this;
    }

    public QueryWrapper<R> where(Condition... conditions) {
        whereWrapper.where(conditions);
        return this;
    }

    public QueryWrapper<R> groupBy(SqlExpression... expressions) {
        groupByExpressions.clear();
        Collections.addAll(groupByExpressions, expressions);
        return this;
    }

    public QueryWrapper<R> groupBy(NamedSqlExpression... expressions) {
        groupByExpressions.clear();
        for (NamedSqlExpression expression : expressions) {
            groupByExpressions.add(expression.aliasRef());
        }
        return this;
    }

    public QueryWrapper<R> groupByAlias(String... aliases) {
        groupByExpressions.clear();
        for (String alias : aliases) {
            groupByExpressions.add(Expressions.aliasRef(alias));
        }
        return this;
    }

    public QueryWrapper<R> having(Consumer<PredicateBuilder> consumer) {
        PredicateBuilder builder = new PredicateBuilder();
        consumer.accept(builder);
        this.having = builder.build();
        return this;
    }

    public QueryWrapper<R> having(Condition condition) {
        this.having = condition;
        return this;
    }

    public QueryWrapper<R> having(Condition... conditions) {
        this.having = Conditions.and(conditions);
        return this;
    }

    public QueryWrapper<R> orderBy(Consumer<OrderBuilder> consumer) {
        whereWrapper.orderBy(consumer);
        return this;
    }

    public QueryWrapper<R> orderBy(Order... orders) {
        whereWrapper.orderBy(orders);
        return this;
    }

    public QueryWrapper<R> limit(int limit) {
        whereWrapper.limit(limit);
        return this;
    }

    public QueryWrapper<R> limit(int offset, int limit) {
        whereWrapper.limit(offset, limit);
        return this;
    }

    public QueryDefinition toDefinition() {
        if (from == null) {
            throw new SqlExecutorException("QueryWrapper requires from(table) before rendering SQL");
        }
        return new QueryDefinition(
                List.copyOf(selectExpressions),
                selectAll,
                from,
                joins.stream().map(join -> new QueryJoin(join.joinType(), join.table(), join.on())).toList(),
                List.copyOf(groupByExpressions),
                having,
                whereWrapper.toDefinition()
        );
    }

    public R one() {
        SqlRequest request = renderQuery(sqlExecutor);
        return sqlExecutor.selectOne(request.sql(), request.args(), context("one"), resultType);
    }

    public List<R> list() {
        SqlRequest request = renderQuery(sqlExecutor);
        return sqlExecutor.selectList(request.sql(), request.args(), context("list"), resultType);
    }

    public long count() {
        QueryDefinition definition = toDefinition();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderQuery(definition, sqlExecutor.getDbType());
        SqlRequest countRequest = sqlExecutor.getSqlGenerator().rewriteCount(definition, sqlExecutor.getDbType());
        SqlExecutionContext context = contextBuilder("count")
                .countRequest(countRequest)
                .build();
        return sqlExecutor.getSqlPagingSupport().count(sqlExecutor, context, request.sql(), request.args());
    }

    public Page<R> page(int current, int size) {
        return page(Paging.of(current, size));
    }

    public Page<R> page(Paging paging) {
        Objects.requireNonNull(paging, "paging");
        QueryDefinition definition = toDefinition();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderQuery(definition, sqlExecutor.getDbType());
        SqlRequest countRequest = sqlExecutor.getSqlGenerator().rewriteCount(definition, sqlExecutor.getDbType());
        SqlExecutionContext context = contextBuilder("page")
                .paging(paging)
                .countRequest(countRequest)
                .build();
        return sqlExecutor.getSqlPagingSupport().page(sqlExecutor, context, request.sql(), request.args(), paging, resultType);
    }

    public boolean exists() {
        QueryDefinition base = toDefinition();
        QueryDefinition existsDefinition = new QueryDefinition(
                List.of(Expressions.raw("1")),
                false,
                base.from(),
                base.joins(),
                base.groupByExpressions(),
                base.having(),
                new WhereDefinition(base.where().condition(), List.of(), 1, null)
        );
        SqlRequest request = sqlExecutor.getSqlGenerator().renderQuery(existsDefinition, sqlExecutor.getDbType());
        return !sqlExecutor.selectList(request.sql(), request.args(), context("exists"), Integer.class).isEmpty();
    }

    private SqlRequest renderQuery(SqlExecutor sqlExecutor) {
        return sqlExecutor.getSqlGenerator().renderQuery(toDefinition(), sqlExecutor.getDbType());
    }

    private SqlExecutionContext context(String methodName) {
        return contextBuilder(methodName).build();
    }

    private SqlExecutionContext.Builder contextBuilder(String methodName) {
        return SqlExecutionContext.builder(SqlCommandType.SELECT)
                .sqlExecutor(sqlExecutor)
                .mapper(DSLContext.class, methodName);
    }

    private void addJoin(String joinType, Table<?> table, Condition on) {
        joins.add(new JoinSpec(joinType, table, on));
    }

    public static final class JoinStep<R> {
        private final QueryWrapper<R> owner;
        private final String joinType;
        private final Table<?> table;

        private JoinStep(QueryWrapper<R> owner, String joinType, Table<?> table) {
            this.owner = owner;
            this.joinType = joinType;
            this.table = table;
        }

        public QueryWrapper<R> on(Condition condition) {
            owner.addJoin(joinType, table, condition);
            return owner;
        }

        public QueryWrapper<R> on(Consumer<PredicateBuilder> consumer) {
            PredicateBuilder builder = new PredicateBuilder();
            consumer.accept(builder);
            return on(builder.build());
        }
    }

    private record JoinSpec(String joinType, Table<?> table, Condition on) {
    }

}
