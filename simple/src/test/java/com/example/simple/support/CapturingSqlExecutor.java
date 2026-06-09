package com.example.simple.support;

import com.example.simple.entity.User;
import org.byteora.kyra.orm.runtime.DbType;
import org.byteora.kyra.orm.runtime.IdGenerator;
import org.byteora.kyra.orm.runtime.RowMapper;
import org.byteora.kyra.orm.runtime.SqlExecutionContext;
import org.byteora.kyra.orm.runtime.SqlExecutor;
import org.byteora.kyra.orm.runtime.SqlGenerator;
import org.byteora.kyra.orm.runtime.SqlPagingSupport;
import org.byteora.kyra.orm.runtime.TypeConverter;

import java.util.List;

public final class CapturingSqlExecutor implements SqlExecutor {
    private TypeConverter typeConverter = new TypeConverter();
    private SqlExecutionContext capturedContext;

    public SqlExecutionContext capturedContext() {
        return capturedContext;
    }

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
    @SuppressWarnings("unchecked")
    public <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        this.capturedContext = context;
        return resultType == User.class ? (T) new User() : null;
    }

    @Override
    public <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(String sql, Object[] args, SqlExecutionContext context) {
        this.capturedContext = context;
        return 1;
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
