package io.quarkiverse.quarkus.reactive.h2.client.runtime;

import static io.quarkus.credentials.CredentialsProvider.PASSWORD_PROPERTY_NAME;
import static io.quarkus.credentials.CredentialsProvider.USER_PROPERTY_NAME;
import static io.quarkus.reactive.datasource.runtime.ReactiveDataSourceUtil.qualifier;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.inject.Instance;

import io.quarkiverse.quarkus.reactive.h2.client.H2PoolCreator;
import io.quarkus.arc.Arc;
import io.quarkus.credentials.CredentialsProvider;
import io.quarkus.credentials.runtime.CredentialsProviderFinder;
import io.quarkus.datasource.runtime.DataSourceRuntimeConfig;
import io.quarkus.datasource.runtime.DataSourcesRuntimeConfig;
import io.quarkus.reactive.datasource.runtime.DataSourceReactiveRuntimeConfig;
import io.quarkus.reactive.datasource.runtime.DataSourcesReactiveRuntimeConfig;
import io.quarkus.reactive.datasource.runtime.ReactivePoolUtil;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

/**
 * Creates the H2 pools, called from the {@link Pool} services registered by the deployment processor.
 * <p>
 * The {@link Pool} and Mutiny {@code Pool} beans and the health check are provided by the reactive datasource extension.
 */
public final class H2PoolRecorder {

    private H2PoolRecorder() {
    }

    public static Optional<Pool> createPool(String dataSourceName, DataSourcesRuntimeConfig runtimeConfig,
            DataSourcesReactiveRuntimeConfig reactiveRuntimeConfig,
            DataSourcesReactiveH2Config reactiveH2RuntimeConfig, Vertx vertx, int eventLoopCount) {
        if (!ReactivePoolUtil.checkActive(dataSourceName, runtimeConfig, reactiveRuntimeConfig).value()) {
            return Optional.empty();
        }
        return Optional.of(initialize(vertx,
                eventLoopCount,
                dataSourceName,
                runtimeConfig.dataSources().get(dataSourceName),
                reactiveRuntimeConfig.dataSources().get(dataSourceName).reactive(),
                reactiveH2RuntimeConfig.dataSources().get(dataSourceName).reactive().h2()));
    }

    private static Pool initialize(Vertx vertx,
            Integer eventLoopCount,
            String dataSourceName,
            DataSourceRuntimeConfig dataSourceRuntimeConfig,
            DataSourceReactiveRuntimeConfig dataSourceReactiveRuntimeConfig,
            DataSourceReactiveH2Config dataSourceReactiveH2Config) {
        PoolOptions poolOptions = toPoolOptions(eventLoopCount, dataSourceReactiveRuntimeConfig,
                dataSourceReactiveH2Config);
        JDBCConnectOptions h2ConnectOptions = toJDBCConnectOptions(dataSourceRuntimeConfig,
                dataSourceReactiveRuntimeConfig);

        if (dataSourceRuntimeConfig.credentialsProvider().isPresent()) {
            String beanName = dataSourceRuntimeConfig.credentialsProviderName().orElse(null);
            CredentialsProvider credentialsProvider = CredentialsProviderFinder.find(beanName);
            Map<String, String> userCreds = credentialsProvider.getCredentials(USER_PROPERTY_NAME);
            if ((userCreds != null) && (!userCreds.values().isEmpty())) {
                userCreds.values().forEach(h2ConnectOptions::setUser);
            }
            Map<String, String> passCreds = credentialsProvider.getCredentials(PASSWORD_PROPERTY_NAME);
            if ((passCreds != null) && (!passCreds.values().isEmpty())) {
                passCreds.values().forEach(h2ConnectOptions::setPassword);
            }
        }

        return createPool(vertx, poolOptions, h2ConnectOptions, dataSourceName);
    }

    private static PoolOptions toPoolOptions(Integer eventLoopCount,
            DataSourceReactiveRuntimeConfig dataSourceReactiveRuntimeConfig,
            DataSourceReactiveH2Config dataSourceReactiveH2Config) {
        PoolOptions poolOptions = ReactivePoolUtil.toPoolOptions(eventLoopCount, dataSourceReactiveRuntimeConfig);

        if (dataSourceReactiveH2Config.connectionTimeout().isPresent()) {
            poolOptions.setConnectionTimeout(dataSourceReactiveH2Config.connectionTimeout().getAsInt());
            poolOptions.setConnectionTimeoutUnit(TimeUnit.SECONDS);
        }

        return poolOptions;
    }

    private static JDBCConnectOptions toJDBCConnectOptions(DataSourceRuntimeConfig dataSourceRuntimeConfig,
            DataSourceReactiveRuntimeConfig dataSourceReactiveRuntimeConfig) {
        JDBCConnectOptions h2ConnectOptions = new JDBCConnectOptions();
        if (dataSourceReactiveRuntimeConfig.url().isPresent()) {
            // Only one URL is supported by JDBCPool
            String url = dataSourceReactiveRuntimeConfig.url().get().get(0);
            // clean up the URL to make migrations easier
            if (url.startsWith("vertx-reactive:h2:")) {
                url = url.substring("vertx-reactive:".length());
            }
            h2ConnectOptions.setJdbcUrl("jdbc:" + url);
        }

        dataSourceRuntimeConfig.username().ifPresent(h2ConnectOptions::setUser);
        dataSourceRuntimeConfig.password().ifPresent(h2ConnectOptions::setPassword);

        // credentials provider
        if (dataSourceRuntimeConfig.credentialsProvider().isPresent()) {
            String beanName = dataSourceRuntimeConfig.credentialsProviderName().orElse(null);
            CredentialsProvider credentialsProvider = CredentialsProviderFinder.find(beanName);
            String name = dataSourceRuntimeConfig.credentialsProvider().get();
            Map<String, String> credentials = credentialsProvider.getCredentials(name);
            String user = credentials.get(USER_PROPERTY_NAME);
            String password = credentials.get(PASSWORD_PROPERTY_NAME);
            if (user != null) {
                h2ConnectOptions.setUser(user);
            }
            if (password != null) {
                h2ConnectOptions.setPassword(password);
            }
        }

        return h2ConnectOptions;
    }

    private static Pool createPool(Vertx vertx, PoolOptions poolOptions, JDBCConnectOptions h2ConnectOptions,
            String dataSourceName) {
        Instance<H2PoolCreator> instance = Arc.container().select(H2PoolCreator.class, qualifier(dataSourceName));
        if (instance.isResolvable()) {
            H2PoolCreator.Input input = new DefaultInput(vertx, poolOptions, h2ConnectOptions);
            return instance.get().create(input);
        }
        return JDBCPool.pool(vertx, h2ConnectOptions, poolOptions);
    }

    private static class DefaultInput implements H2PoolCreator.Input {
        private final Vertx vertx;
        private final PoolOptions poolOptions;
        private final JDBCConnectOptions h2ConnectOptions;

        public DefaultInput(Vertx vertx, PoolOptions poolOptions, JDBCConnectOptions h2ConnectOptions) {
            this.vertx = vertx;
            this.poolOptions = poolOptions;
            this.h2ConnectOptions = h2ConnectOptions;
        }

        @Override
        public Vertx vertx() {
            return vertx;
        }

        @Override
        public PoolOptions poolOptions() {
            return poolOptions;
        }

        @Override
        public JDBCConnectOptions h2ConnectOptionsList() {
            return h2ConnectOptions;
        }
    }
}
