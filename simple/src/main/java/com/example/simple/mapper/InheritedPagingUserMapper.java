package com.example.simple.mapper;

import com.example.simple.common.ReadMapper;
import com.example.simple.dto.InheritedPagingUserQuery;
import com.example.simple.entity.User;
import org.byteora.kyra.orm.query.Page;

public interface InheritedPagingUserMapper extends ReadMapper<User> {
    Page<User> selectPageByQuery(InheritedPagingUserQuery query);
}
