package com.example.simple.common;

import org.byteora.kyra.orm.annotation.MapperCapability;
import org.byteora.kyra.orm.runtime.AbstractMapper;
import org.byteora.kyra.orm.runtime.SqlExecutor;

@MapperCapability(MultiTypeMapper.class)
public class MultiTypeMapperImpl<T, V> extends AbstractMapper<T> implements MultiTypeMapper<T, V> {
    public MultiTypeMapperImpl(SqlExecutor sqlExecutor, Class<T> entityClass) {
        super(sqlExecutor, entityClass);
    }

    @Override
    public String firstTypeName() {
        return entityClass == null ? "null" : entityClass.getName();
    }
}
