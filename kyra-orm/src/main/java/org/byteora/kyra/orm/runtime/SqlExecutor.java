package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.core.runtime.RuntimeTypes;

import java.lang.reflect.Type;
import java.util.List;

public interface SqlExecutor {
    <T> T selectOne(String sql, Object[] args, Class<T> resultType);

    /**
     * Generic result entry point. Existing executors that only implement the Class API fall back to
     * the raw type; implementations that support generic row mapping should override this method.
     */
    @SuppressWarnings("unchecked")
    default <T> T selectOne(String sql, Object[] args, Type resultType) {
        return selectOne(sql, args, (Class<T>) RuntimeTypes.rawClass(resultType));
    }

    <T> List<T> selectList(String sql, Object[] args, Class<T> resultType);

    @SuppressWarnings("unchecked")
    default <T> List<T> selectList(String sql, Object[] args, Type elementType) {
        return selectList(sql, args, (Class<T>) RuntimeTypes.rawClass(elementType));
    }

    int update(String sql, Object[] args);

    <T> T updateAndReturnGeneratedKey(String sql, Object[] args, Class<T> resultType);

    int[] executeBatch(String sql, List<Object[]> batchArgs);

    TypeConverter getTypeConverter();

    void setTypeConverter(TypeConverter typeConverter);

    IdGenerator getIdGenerator();

    void setIdGenerator(IdGenerator idGenerator);

    <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType);

    @SuppressWarnings("unchecked")
    default <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Type resultType) {
        return selectOne(sql, args, context, (Class<T>) RuntimeTypes.rawClass(resultType));
    }

    <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType);

    @SuppressWarnings("unchecked")
    default <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Type elementType) {
        return selectList(sql, args, context, (Class<T>) RuntimeTypes.rawClass(elementType));
    }

    int update(String sql, Object[] args, SqlExecutionContext context);

    int[] executeBatch(String sql, List<Object[]> batchArgs, SqlExecutionContext context);

    SqlPagingSupport getSqlPagingSupport();

    DbType getDbType();

    SqlGenerator getSqlGenerator();

    <T> List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper);
}
