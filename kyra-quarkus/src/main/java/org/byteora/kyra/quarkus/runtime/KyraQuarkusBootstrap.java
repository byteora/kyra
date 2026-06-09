package org.byteora.kyra.quarkus.runtime;

import org.byteora.kyra.orm.runtime.SqlExecutor;
import org.byteora.kyra.quarkus.Sql;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

@Startup
@ApplicationScoped
public class KyraQuarkusBootstrap {
    private final Instance<SqlExecutor> sqlExecutor;

    public KyraQuarkusBootstrap(Instance<SqlExecutor> sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    @PostConstruct
    void init() {
        if (sqlExecutor.isResolvable()) {
            Sql.bind(sqlExecutor.get());
        }
    }

    @PreDestroy
    void destroy() {
        Sql.clear();
    }
}
