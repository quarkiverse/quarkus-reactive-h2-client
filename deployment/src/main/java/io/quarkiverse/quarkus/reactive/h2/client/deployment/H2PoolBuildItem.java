package io.quarkiverse.quarkus.reactive.h2.client.deployment;

import java.util.function.Function;

import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.vertx.jdbcclient.JDBCPool;

public final class H2PoolBuildItem extends MultiBuildItem {

    private final String dataSourceName;

    private final Function<SyntheticCreationalContext<JDBCPool>, JDBCPool> h2Pool;

    public H2PoolBuildItem(String dataSourceName, Function<SyntheticCreationalContext<JDBCPool>, JDBCPool> h2Pool) {
        this.dataSourceName = dataSourceName;
        this.h2Pool = h2Pool;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public Function<SyntheticCreationalContext<JDBCPool>, JDBCPool> getH2Pool() {
        return h2Pool;
    }

    public boolean isDefault() {
        return DataSourceUtil.isDefault(dataSourceName);
    }
}
