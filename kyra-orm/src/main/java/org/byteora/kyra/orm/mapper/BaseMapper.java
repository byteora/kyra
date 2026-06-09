package org.byteora.kyra.orm.mapper;

import org.byteora.kyra.orm.query.Page;
import org.byteora.kyra.orm.query.Paging;
import org.byteora.kyra.orm.query.UpdateWrapper;
import org.byteora.kyra.orm.query.WhereWrapper;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public interface BaseMapper<T> {
    T selectById(Serializable id);

    List<T> selectByIds(Collection<? extends Serializable> ids);

    List<T> selectList(WhereWrapper query);

    T selectOne(WhereWrapper query);

    long count(WhereWrapper query);

    Page<T> page(Paging paging, WhereWrapper query);

    int insert(T entity);

    int insert(Collection<T> entities);

    int updateById(T entity);

    int updateById(Collection<T> entities);

    int delete(WhereWrapper query);

    int update(UpdateWrapper updateWrapper);

    int deleteById(Serializable id);

    int deleteByIds(Collection<? extends Serializable> ids);
}
