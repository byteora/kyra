package org.byteora.kyra.orm.mapper;

import org.byteora.kyra.orm.query.Condition;
import org.byteora.kyra.orm.query.Page;
import org.byteora.kyra.orm.query.Paging;
import org.byteora.kyra.orm.query.UpdateWrapper;
import org.byteora.kyra.orm.query.WhereWrapper;
import org.byteora.kyra.orm.query.Wrapper;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public interface BaseMapper<T> {
    T selectById(Serializable id);

    List<T> selectByIds(Collection<? extends Serializable> ids);

    List<T> list(WhereWrapper query);

    default List<T> list(Condition... conditions) {
        return list(Wrapper.where().where(conditions));
    }

    T one(WhereWrapper query);

    default T one(Condition... conditions) {
        return one(Wrapper.where().where(conditions));
    }

    long count(WhereWrapper query);

    default long count(Condition... conditions) {
        return count(Wrapper.where().where(conditions));
    }

    boolean exists(WhereWrapper query);

    default boolean exists(Condition... conditions) {
        return exists(Wrapper.where().where(conditions));
    }

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
