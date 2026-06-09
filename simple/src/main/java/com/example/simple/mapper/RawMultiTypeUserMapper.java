package com.example.simple.mapper;

import com.example.simple.common.MultiTypeMapper;
import com.example.simple.entity.User;
import org.byteora.kyra.orm.mapper.BaseMapper;

@SuppressWarnings({"rawtypes"})
public interface RawMultiTypeUserMapper extends BaseMapper<User>, MultiTypeMapper {
}
