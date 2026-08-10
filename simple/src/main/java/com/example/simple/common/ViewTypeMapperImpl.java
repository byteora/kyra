package com.example.simple.common;

import org.byteora.kyra.orm.annotation.MapperCapability;
import org.byteora.kyra.orm.runtime.AbstractMapper;
import org.byteora.kyra.orm.runtime.SqlExecutor;

@MapperCapability(ViewTypeMapper.class)
public class ViewTypeMapperImpl<T> extends AbstractMapper<T> implements ViewTypeMapper<T> {
    public ViewTypeMapperImpl(SqlExecutor sqlExecutor, Class<T> type) {
        super(sqlExecutor, type);
    }

    @Override
    public String mappedTypeName() {
        return type == null ? "null" : type.getName();
    }
}
