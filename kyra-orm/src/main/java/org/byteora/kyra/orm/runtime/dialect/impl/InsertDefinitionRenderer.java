package org.byteora.kyra.orm.runtime.dialect.impl;

import org.byteora.kyra.orm.runtime.SqlRequest;
import org.byteora.kyra.orm.runtime.dialect.InsertModel;
import org.byteora.kyra.orm.runtime.dialect.InsertRenderer;
import org.byteora.kyra.orm.runtime.dialect.RenderContext;

public final class InsertDefinitionRenderer implements InsertRenderer {
    @Override
    public SqlRequest render(InsertModel insertModel, RenderContext context) {
        StringBuilder sql = context.sql();
        sql.append("insert into ").append(insertModel.table().tableReference(context.dialect().dbType())).append(" (");
        for (int i = 0; i < insertModel.columns().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(context.dialect().identifiers().quote(insertModel.columns().get(i)));
        }
        sql.append(") values (");
        for (int i = 0; i < insertModel.columns().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(')');
        context.args().addAll(insertModel.args());
        return context.toRequest();
    }
}
