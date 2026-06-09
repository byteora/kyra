package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.runtime.DbType;
import org.byteora.kyra.orm.runtime.dialect.RenderContext;

import java.util.List;

public interface Condition {
    void appendTo(StringBuilder sql, List<Object> args, DbType dbType);

    void appendTo(RenderContext context);
}
