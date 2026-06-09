package org.byteora.kyra.orm.dynamic;

import org.byteora.kyra.orm.runtime.BoundSql;

public final class DynamicSqlArgumentResolver {
    private DynamicSqlArgumentResolver() {
    }

    public static Object[] resolve(BoundSql boundSql) {
        Object[] args = new Object[boundSql.getBindings().size()];
        for (int i = 0; i < boundSql.getBindings().size(); i++) {
            args[i] = PropertyAccess.resolve(boundSql.getValues(), boundSql.getBindings().get(i));
        }
        return args;
    }
}
