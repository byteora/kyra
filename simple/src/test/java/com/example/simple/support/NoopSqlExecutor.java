package com.example.simple.support;

import org.byteora.kyra.orm.runtime.DbType;
import org.byteora.kyra.orm.runtime.IdGenerator;
import org.byteora.kyra.orm.runtime.RowMapper;
import org.byteora.kyra.orm.runtime.SqlExecutionContext;
import org.byteora.kyra.orm.runtime.SqlExecutor;
import org.byteora.kyra.orm.runtime.SqlGenerator;
import org.byteora.kyra.orm.runtime.SqlPagingSupport;
import org.byteora.kyra.orm.runtime.TypeConverter;

import java.util.List;

public final class NoopSqlExecutor implements SqlExecutor {
    private TypeConverter typeConverter = new TypeConverter();

    @Override
    public <T> T selectOne(String sql, Object[] args, Class<T> resultType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<T> selectList(String sql, Object[] args, Class<T> resultType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(String sql, Object[] args) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T updateAndReturnGeneratedKey(String sql, Object[] args, Class<T> resultType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int[] executeBatch(String sql, List<Object[]> batchArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeConverter getTypeConverter() {
        return typeConverter;
    }

    @Override
    public void setTypeConverter(TypeConverter typeConverter) {
        this.typeConverter = typeConverter;
    }

    @Override
    public IdGenerator getIdGenerator() {
        return null;
    }

    @Override
    public void setIdGenerator(IdGenerator idGenerator) {
    }

    @Override
    public <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(String sql, Object[] args, SqlExecutionContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int[] executeBatch(String sql, List<Object[]> batchArgs, SqlExecutionContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SqlPagingSupport getSqlPagingSupport() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DbType getDbType() {
        return DbType.H2;
    }

    @Override
    public SqlGenerator getSqlGenerator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper) {
        throw new UnsupportedOperationException();
    }
}
