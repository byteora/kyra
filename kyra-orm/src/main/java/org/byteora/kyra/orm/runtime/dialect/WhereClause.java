package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.query.Condition;

public record WhereClause(Condition condition) {
    public boolean present() {
        return condition != null;
    }
}
