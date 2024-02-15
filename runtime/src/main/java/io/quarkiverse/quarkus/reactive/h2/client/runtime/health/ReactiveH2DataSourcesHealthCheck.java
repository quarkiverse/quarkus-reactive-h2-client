package io.quarkiverse.quarkus.reactive.h2.client.runtime.health;

import java.util.Set;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;

import org.eclipse.microprofile.health.Readiness;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.datasource.runtime.DataSourceSupport;
import io.quarkus.reactive.datasource.runtime.ReactiveDatasourceHealthCheck;
import io.vertx.jdbcclient.JDBCPool;

@Readiness
@ApplicationScoped
class ReactiveH2DataSourcesHealthCheck extends ReactiveDatasourceHealthCheck {

    public ReactiveH2DataSourcesHealthCheck() {
        super("Reactive H2 connections health check", "SELECT 1");
    }

    @PostConstruct
    protected void init() {
        ArcContainer container = Arc.container();
        DataSourceSupport support = container.instance(DataSourceSupport.class).get();
        Set<String> excludedNames = support.getInactiveOrHealthCheckExcludedNames();
        for (InstanceHandle<JDBCPool> handle : container.select(JDBCPool.class, Any.Literal.INSTANCE).handles()) {
            String poolName = getPoolName(handle.getBean());
            if (!excludedNames.contains(poolName)) {
                addPool(poolName, handle.get());
            }
        }
    }
}
