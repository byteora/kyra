package com.example.simple.common;

import org.byteora.kyra.orm.annotation.MapperCapability;
import org.byteora.kyra.orm.runtime.AbstractMapper;
import org.byteora.kyra.orm.runtime.SqlExecutor;

@MapperCapability(ViewTypeMapper.class)
public class ViewTypeMapperImpl<T> extends AbstractMapper<T> implements ViewTypeMapper<T> {
    public ViewTypeMapperImpl(SqlExecutor sqlExecutor, Class<T> entityClass) {
        super(sqlExecutor, entityClass);
    }

    @Override
    public String mappedTypeName() {
        return entityClass == null ? "null" : entityClass.getName();
    }
}
