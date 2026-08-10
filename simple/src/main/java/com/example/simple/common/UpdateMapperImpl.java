package com.example.simple.common;

import org.byteora.kyra.orm.annotation.MapperCapability;
import org.byteora.kyra.orm.query.Table;
import org.byteora.kyra.orm.query.Tables;
import org.byteora.kyra.orm.runtime.AbstractMapper;
import org.byteora.kyra.orm.runtime.SqlExecutor;

@MapperCapability(UpdateMapper.class)
public class UpdateMapperImpl<T> extends AbstractMapper<T> implements UpdateMapper<T> {
    private final Table<T> table;

    public UpdateMapperImpl(SqlExecutor sqlExecutor, Class<T> type) {
        super(sqlExecutor, type);
        this.table = Tables.get(type);
    }

    @Override
    public int updateNameById(Long id, String name) {
        String sql = "update " + table.tableName()
                + " set " + table.columnName("name") + " = ? where "
                + table.idColumn().columnName() + " = ?";
        return sqlExecutor.update(sql, new Object[]{name, id});
    }
}
