package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.orm.query.Table;
import org.byteora.kyra.orm.query.QueryDefinition;
import org.byteora.kyra.orm.query.UpdateDefinition;
import org.byteora.kyra.orm.query.WhereDefinition;

import java.util.List;

public interface SqlGenerator {
    SqlRequest renderQuery(QueryDefinition definition, DbType dbType);

    SqlRequest renderSelect(Table<?> table, WhereDefinition whereDefinition, DbType dbType);

    SqlRequest renderDelete(Table<?> table, WhereDefinition whereDefinition, DbType dbType);

    SqlRequest renderUpdate(Table<?> table, UpdateDefinition updateDefinition, DbType dbType);

    default SqlRequest renderInsert(Table<?> table, List<String> columns, List<Object> args, DbType dbType) {
        throw new SqlExecutorException("Insert render is not supported by generator: " + getClass().getName());
    }

    default SqlRequest rewriteCount(QueryDefinition definition, DbType dbType) {
        throw new SqlExecutorException("Count rewrite is not supported by generator: " + getClass().getName());
    }
}
