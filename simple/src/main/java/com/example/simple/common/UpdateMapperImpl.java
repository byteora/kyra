package com.example.simple.common;

import org.byteora.kyra.orm.annotation.MapperCapability;
import org.byteora.kyra.orm.query.EntityTable;
import org.byteora.kyra.orm.query.Tables;
import org.byteora.kyra.orm.runtime.AbstractMapper;
import org.byteora.kyra.orm.runtime.SqlExecutor;

@MapperCapability(UpdateMapper.class)
public class UpdateMapperImpl<T> extends AbstractMapper<T> implements UpdateMapper<T> {
    private final EntityTable<T> entityTable;

    public UpdateMapperImpl(SqlExecutor sqlExecutor, Class<T> entityClass) {
        super(sqlExecutor, entityClass);
        this.entityTable = Tables.get(entityClass);
    }

    @Override
    public int updateNameById(Long id, String name) {
        String sql = "update " + entityTable.tableName()
                + " set " + entityTable.columnName("name") + " = ? where "
                + entityTable.idColumn().columnName() + " = ?";
        return sqlExecutor.update(sql, new Object[]{name, id});
    }
}
