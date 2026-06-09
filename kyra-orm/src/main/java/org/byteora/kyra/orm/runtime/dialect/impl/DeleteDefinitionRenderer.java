package org.byteora.kyra.orm.runtime.dialect.impl;

import org.byteora.kyra.orm.runtime.SqlRequest;
import org.byteora.kyra.orm.runtime.dialect.DeleteModel;
import org.byteora.kyra.orm.runtime.dialect.DeleteRenderer;
import org.byteora.kyra.orm.runtime.dialect.OrderItem;
import org.byteora.kyra.orm.runtime.dialect.PageClause;
import org.byteora.kyra.orm.runtime.dialect.RenderContext;
import org.byteora.kyra.orm.runtime.dialect.WhereClause;

import java.util.List;

public final class DeleteDefinitionRenderer implements DeleteRenderer {
    @Override
    public SqlRequest render(DeleteModel deleteModel, RenderContext context) {
        QueryDefinitionRenderer.configureSingleTableQualification(deleteModel.table(), context);

        var sql = context.sql();
        sql.append("delete from ").append(deleteModel.table().tableReference(context.dialect().dbType()));
        QueryDefinitionRenderer.appendWhere(
                new WhereClause(deleteModel.where() == null ? null : deleteModel.where().condition()),
                context
        );
        QueryDefinitionRenderer.appendOrder(
                deleteModel.where() == null
                        ? List.of()
                        : deleteModel.where().orders().stream().map(order -> new OrderItem(order.expression(), order.ascending())).toList(),
                context
        );
        context.dialect().paging().render(new PageClause(
                deleteModel.where() == null ? null : deleteModel.where().offset(),
                deleteModel.where() == null ? null : deleteModel.where().limit(),
                true
        ), context);
        return context.toRequest();
    }
}
