package org.byteora.kyra.orm.query;

import org.byteora.kyra.core.TypeRef;
import org.byteora.kyra.orm.mapper.BaseMapperImpl;
import org.byteora.kyra.orm.runtime.SqlExecutor;
import org.byteora.kyra.orm.runtime.SqlExecutorException;

import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

/**
 * Entry point for type-safe SQL DSL operations bound to a {@link SqlExecutor}.
 */
public final class DSLContext {
    private final SqlExecutor sqlExecutor;

    public DSLContext(SqlExecutor sqlExecutor) {
        this.sqlExecutor = Objects.requireNonNull(sqlExecutor, "sqlExecutor");
    }

    public <T> EntityQuery<T> from(Table<T> table) {
        Objects.requireNonNull(table, "table");
        return new EntityQuery<>(query(table.type()).selectAll().from(table));
    }

    public <R> QueryWrapper<R> query(Class<R> resultType) {
        return new QueryWrapper<>(sqlExecutor, Objects.requireNonNull(resultType, "resultType"));
    }

    public <R> QueryWrapper<R> query(TypeRef<R> resultType) {
        Objects.requireNonNull(resultType, "resultType");
        return new QueryWrapper<>(sqlExecutor, resultType.type());
    }

    public <R> QueryWrapper<R> select(Class<R> resultType, SqlExpression... expressions) {
        return query(resultType).select(expressions);
    }

    public <R> QueryWrapper<R> select(TypeRef<R> resultType, SqlExpression... expressions) {
        return query(resultType).select(expressions);
    }

    public <T> int insert(T entity) {
        return withMapper(entity, mapper -> mapper.insert(entity));
    }

    public <T> int insert(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        T first = entities.iterator().next();
        return withMapper(first, mapper -> mapper.insert(entities));
    }

    public <T> int updateById(T entity) {
        return withMapper(entity, mapper -> mapper.updateById(entity));
    }

    public <T> int updateById(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        T first = entities.iterator().next();
        return withMapper(first, mapper -> mapper.updateById(entities));
    }

    public <T> int deleteById(Class<T> type, Serializable id) {
        return withTypeMapper(type, mapper -> mapper.deleteById(id));
    }

    public <T> int deleteByIds(Class<T> type, Collection<? extends Serializable> ids) {
        return withTypeMapper(type, mapper -> mapper.deleteByIds(ids));
    }

    public <T> int update(Table<T> table, UpdateWrapper updateWrapper) {
        Objects.requireNonNull(updateWrapper, "updateWrapper");
        return withTableMapper(table, mapper -> mapper.update(updateWrapper));
    }

    public <T> int delete(Table<T> table, WhereWrapper whereWrapper) {
        Objects.requireNonNull(whereWrapper, "whereWrapper");
        return withTableMapper(table, mapper -> mapper.delete(whereWrapper));
    }

    private <T, R> R withMapper(T entity, Function<BaseMapperImpl<T>, R> action) {
        if (entity == null) {
            throw new SqlExecutorException("Entity must not be null");
        }
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) entity.getClass();
        return withTypeMapper(type, action);
    }

    private <T, R> R withTypeMapper(Class<T> type, Function<BaseMapperImpl<T>, R> action) {
        return withTableMapper(Tables.get(Objects.requireNonNull(type, "type")), action);
    }

    private <T, R> R withTableMapper(Table<T> table, Function<BaseMapperImpl<T>, R> action) {
        Objects.requireNonNull(table, "table");
        BaseMapperImpl<T> mapper = new BaseMapperImpl<>(sqlExecutor, table.type());
        return action.apply(mapper);
    }
}
