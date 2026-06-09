package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.NamedSqlExpression;
import org.byteora.kyra.orm.query.Order;
import org.byteora.kyra.orm.query.QueryDefinition;
import org.byteora.kyra.orm.query.QueryJoin;
import org.byteora.kyra.orm.query.SqlExpression;

import java.util.List;

public final class QueryModelMapper {
    private QueryModelMapper() {
    }

    public static QueryModel from(QueryDefinition definition) {
        List<SelectItem> selectItems = definition.selectExpressions().stream()
                .map(QueryModelMapper::toSelectItem)
                .toList();
        List<JoinItem> joinItems = definition.joins().stream()
                .map(QueryModelMapper::toJoinItem)
                .toList();
        List<GroupByItem> groupByItems = definition.groupByExpressions().stream()
                .map(GroupByItem::new)
                .toList();
        List<OrderItem> orderItems = definition.where() == null
                ? List.of()
                : definition.where().orders().stream().map(QueryModelMapper::toOrderItem).toList();
        return new QueryModel(
                definition,
                selectItems,
                joinItems,
                groupByItems,
                orderItems,
                new WhereClause(definition.where() == null ? null : definition.where().condition()),
                new HavingClause(definition.having())
        );
    }

    private static SelectItem toSelectItem(SqlExpression expression) {
        if (expression instanceof NamedSqlExpression namedSqlExpression) {
            return new SelectItem(namedSqlExpression.source(), namedSqlExpression.alias());
        }
        return new SelectItem(expression, null);
    }

    private static OrderItem toOrderItem(Order order) {
        return new OrderItem(order.expression(), order.ascending());
    }

    private static JoinItem toJoinItem(QueryJoin join) {
        return new JoinItem(join.joinType(), join.table(), join.on());
    }
}
