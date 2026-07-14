package org.byteora.kyra.quarkus.runtime;

import org.byteora.kyra.orm.runtime.DefaultSqlGenerator;
import org.byteora.kyra.orm.runtime.DefaultSqlPagingSupport;
import org.byteora.kyra.orm.runtime.SqlExecutor;
import org.byteora.kyra.orm.runtime.SqlGenerator;
import org.byteora.kyra.orm.runtime.SqlInterceptor;
import org.byteora.kyra.orm.runtime.SqlPagingSupport;
import org.byteora.kyra.orm.runtime.TypeConverter;
import org.byteora.kyra.quarkus.QuarkusSqlExecutor;
import org.byteora.kyra.json.JsonMapper;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

@Dependent
public class KyraQuarkusProducer {
    @Produces
    @Singleton
    @DefaultBean
    public JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }

    @Produces
    @Singleton
    @DefaultBean
    public SqlPagingSupport sqlPagingSupport() {
        return new DefaultSqlPagingSupport();
    }

    @Produces
    @Singleton
    @DefaultBean
    public SqlGenerator sqlGenerator() {
        return new DefaultSqlGenerator();
    }

    @Produces
    @Singleton
    @DefaultBean
    public SqlExecutor sqlExecutor(Instance<DataSource> dataSource,
                                   Instance<TypeConverter> typeConverter,
                                   Instance<SqlPagingSupport> sqlPagingSupport,
                                   Instance<SqlGenerator> sqlGenerator,
                                   Instance<SqlInterceptor> interceptors) {
        if (!dataSource.isResolvable()) {
            throw new IllegalStateException("No DataSource bean is available for Kyra SqlExecutor");
        }
        QuarkusSqlExecutor sqlExecutor = new QuarkusSqlExecutor(dataSource.get());
        if (typeConverter.isResolvable()) {
            sqlExecutor.setTypeConverter(typeConverter.get());
        }
        if (sqlPagingSupport.isResolvable()) {
            sqlExecutor.setSqlPagingSupport(sqlPagingSupport.get());
        }
        if (sqlGenerator.isResolvable()) {
            sqlExecutor.setSqlGenerator(sqlGenerator.get());
        }
        for (SqlInterceptor interceptor : interceptors) {
            sqlExecutor.addInterceptor(interceptor);
        }
        return sqlExecutor;
    }
}
