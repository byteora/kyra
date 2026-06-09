package org.byteora.kyra.spring.boot.autoconfigure;

import org.byteora.kyra.orm.runtime.*;
import org.byteora.kyra.spring.boot.SpringTransactionSqlExecutor;
import org.byteora.kyra.spring.boot.Sql;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration
@ConditionalOnClass({DataSource.class, SpringTransactionSqlExecutor.class})
public class KyraAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public SqlPagingSupport sqlPagingSupport() {
        return new DefaultSqlPagingSupport();
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlGenerator sqlGenerator() {
        return new DefaultSqlGenerator();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public SqlBinding sqlBinding(SqlExecutor sqlExecutor) {
        Sql.bind(sqlExecutor);
        return new SqlBinding();
    }

    @Bean("sqlExecutor")
    @ConditionalOnMissingBean(SqlExecutor.class)
    public SqlExecutor sqlExecutor(DataSource dataSource,
                                   ObjectProvider<TypeConverter> typeConverter,
                                   ObjectProvider<SqlPagingSupport> sqlPagingSupport,
                                   ObjectProvider<SqlGenerator> sqlGenerator,
                                   ObjectProvider<List<SqlInterceptor>> interceptors,
                                   ObjectProvider<IdGenerator> idGenerators) {
        SpringTransactionSqlExecutor sqlExecutor = new SpringTransactionSqlExecutor(dataSource);
        sqlPagingSupport.ifAvailable(sqlExecutor::setSqlPagingSupport);
        sqlGenerator.ifAvailable(sqlExecutor::setSqlGenerator);
        typeConverter.ifAvailable(sqlExecutor::setTypeConverter);
        interceptors.ifAvailable(sqlExecutor::addInterceptor);
        idGenerators.ifAvailable(sqlExecutor::setIdGenerator);
        return sqlExecutor;
    }

    @Bean
    @ConditionalOnMissingBean
    public static KyraMapperBeanDefinitionRegistryPostProcessor kyraMapperBeanDefinitionRegistryPostProcessor() {
        return new KyraMapperBeanDefinitionRegistryPostProcessor();
    }

    public static final class SqlBinding implements AutoCloseable {
        @Override
        public void close() {
            Sql.clear();
        }
    }
}
