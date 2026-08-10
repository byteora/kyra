package org.byteora.kyra.spring.boot;

import org.byteora.kyra.orm.mapper.BaseMapperImpl;
import org.byteora.kyra.orm.query.*;
import org.byteora.kyra.orm.runtime.SqlExecutor;
import org.byteora.kyra.orm.runtime.SqlExecutorException;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Sql {
    private static volatile SqlExecutor sqlExecutor;

    private Sql() {
    }

    public static SqlExecutor bind(SqlExecutor sqlExecutor) {
        Sql.sqlExecutor = sqlExecutor;
        return sqlExecutor;
    }

    public static void clear() {
        Sql.sqlExecutor = null;
    }

    public static QueryWrapper query() {
        return new QueryWrapper(sqlExecutor);
    }

    public static <T> QueryWrapper from(Table<T> table) {
        return query().selectAll().from(table);
    }

    public static <T> T select(Table<T> table, Condition... condition) {
        return query().selectAll().from(table).where(condition).one(table.type());
    }
    public static <T> T select(Table<T> table, Consumer<PredicateBuilder> consumer) {
        return query().selectAll().from(table).where(consumer).one(table.type());
    }
    public static <T> long count(Table<T> table, Condition... condition) {
        return query().selectAll().from(table).where(condition).count();
    }
    public static <T> long count(Table<T> table, Consumer<PredicateBuilder> consumer) {
        return query().selectAll().from(table).where(consumer).count();
    }
    public static <T> List<T> selectList(Table<T> table, Condition... condition) {
        return query().selectAll().from(table).where(condition).list(table.type());
    }
    public static <T> List<T> selectList(Table<T> table, Consumer<PredicateBuilder> consumer) {
        return query().selectAll().from(table).where(consumer).list(table.type());
    }

    public static <T> int insert(T entity) {
        return withMapper(entity, mapper -> mapper.insert(entity));
    }

    public static <T> int insert(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        T first = entities.iterator().next();
        return withMapper(first, mapper -> mapper.insert(entities));
    }

    public static <T> int updateById(T entity) {
        return withMapper(entity, mapper -> mapper.updateById(entity));
    }

    public static <T> int updateById(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        T first = entities.iterator().next();
        return withMapper(first, mapper -> mapper.updateById(entities));
    }

    public static <T> int deleteById(Class<T> type, Serializable id) {
        return withTypeMapper(type, mapper -> mapper.deleteById(id));
    }

    public static <T> int deleteByIds(Class<T> type, Collection<Serializable> ids) {
        return withTypeMapper(type, mapper -> mapper.deleteByIds(ids));
    }

    public static <T> int update(Table<T> table, UpdateWrapper updateWrapper) {
        return withTableMapper(table, mapper -> mapper.update(updateWrapper));
    }

    public static <T> int delete(Table<T> table, WhereWrapper whereWrapper) {
        return withTableMapper(table, mapper -> mapper.delete(whereWrapper));
    }

    private static <T, R> R withMapper(T entity, Function<BaseMapperImpl<T>, R> action) {
        if (entity == null) {
            throw new SqlExecutorException("Entity must not be null");
        }
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) entity.getClass();
        return withTypeMapper(type, action);
    }

    private static <T, R> R withTypeMapper(Class<T> type, Function<BaseMapperImpl<T>, R> action) {
        return withTableMapper(Tables.get(type), action);
    }

    private static <T, R> R withTableMapper(Table<T> table, Function<BaseMapperImpl<T>, R> action) {
        BaseMapperImpl<T> mapper = new BaseMapperImpl<>(sqlExecutor, table.type());
        return action.apply(mapper);
    }
}
