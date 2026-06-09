package org.byteora.kyra.quarkus;

import org.byteora.kyra.orm.runtime.SqlInterceptor;
import org.byteora.kyra.orm.runtime.jdbc.DefaultSqlExecutor;

import javax.sql.DataSource;
import java.util.Collection;

public class QuarkusSqlExecutor extends DefaultSqlExecutor {
    public QuarkusSqlExecutor(DataSource dataSource) {
        super(dataSource);
    }

    public void addInterceptor(Collection<SqlInterceptor> interceptors) {
        super.addInterceptor(interceptors);
    }
}
