package io.quarkiverse.quarkus.reactive.h2.client.runtime;

import static io.quarkus.credentials.CredentialsProvider.PASSWORD_PROPERTY_NAME;
import static io.quarkus.credentials.CredentialsProvider.USER_PROPERTY_NAME;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import io.quarkiverse.quarkus.reactive.h2.client.H2PoolCreator;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.credentials.CredentialsProvider;
import io.quarkus.credentials.runtime.CredentialsProviderFinder;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.datasource.runtime.DataSourceRuntimeConfig;
import io.quarkus.datasource.runtime.DataSourceSupport;
import io.quarkus.datasource.runtime.DataSourcesRuntimeConfig;
import io.quarkus.reactive.datasource.ReactiveDataSource;
import io.quarkus.reactive.datasource.runtime.DataSourceReactiveRuntimeConfig;
import io.quarkus.reactive.datasource.runtime.DataSourcesReactiveRuntimeConfig;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;

@Recorder
public class H2PoolRecorder {

    private static final TypeLiteral<Instance<H2PoolCreator>> TYPE_LITERAL = new TypeLiteral<>() {
    };

    public Function<SyntheticCreationalContext<JDBCPool>, JDBCPool> configureH2Pool(RuntimeValue<Vertx> vertx,
            Supplier<Integer> eventLoopCount,
            String dataSourceName,
            DataSourcesRuntimeConfig dataSourcesRuntimeConfig,
            DataSourcesReactiveRuntimeConfig dataSourcesReactiveRuntimeConfig,
            DataSourcesReactiveH2Config dataSourcesReactiveH2Config,
            ShutdownContext shutdown) {
        return new Function<>() {
            @Override
            public JDBCPool apply(SyntheticCreationalContext<JDBCPool> context) {
                JDBCPool pool = initialize((VertxInternal) vertx.getValue(),
                        eventLoopCount.get(),
                        dataSourceName,
                        dataSourcesRuntimeConfig.dataSources().get(dataSourceName),
                        dataSourcesReactiveRuntimeConfig.getDataSourceReactiveRuntimeConfig(dataSourceName),
                        dataSourcesReactiveH2Config.dataSources().get(dataSourceName).reactive().h2(),
                        context);

                shutdown.addShutdownTask(pool::close);
                return pool;
            }
        };
    }

    public Function<SyntheticCreationalContext<io.vertx.mutiny.jdbcclient.JDBCPool>, io.vertx.mutiny.jdbcclient.JDBCPool> mutinyH2Pool(
            Function<SyntheticCreationalContext<JDBCPool>, JDBCPool> function) {
        return new Function<>() {
            @Override
            @SuppressWarnings("unchecked")
            public io.vertx.mutiny.jdbcclient.JDBCPool apply(SyntheticCreationalContext context) {
                return io.vertx.mutiny.jdbcclient.JDBCPool.newInstance(function.apply(context));
            }
        };
    }

    private JDBCPool initialize(VertxInternal vertx,
            Integer eventLoopCount,
            String dataSourceName,
            DataSourceRuntimeConfig dataSourceRuntimeConfig,
            DataSourceReactiveRuntimeConfig dataSourceReactiveRuntimeConfig,
            DataSourceReactiveH2Config dataSourceReactiveH2Config,
            SyntheticCreationalContext<JDBCPool> context) {
        if (context.getInjectedReference(DataSourceSupport.class).getInactiveNames().contains(dataSourceName)) {
            throw DataSourceUtil.dataSourceInactive(dataSourceName);
        }
        PoolOptions poolOptions = toPoolOptions(eventLoopCount, dataSourceRuntimeConfig, dataSourceReactiveRuntimeConfig,
                dataSourceReactiveH2Config);
        JDBCConnectOptions h2ConnectOptions = toJDBCConnectOptions(dataSourceName, dataSourceRuntimeConfig,
                dataSourceReactiveRuntimeConfig, dataSourceReactiveH2Config);

        if (dataSourceRuntimeConfig.credentialsProvider().isPresent()) {
            String beanName = dataSourceRuntimeConfig.credentialsProviderName().orElse(null);
            CredentialsProvider credentialsProvider = CredentialsProviderFinder.find(beanName);
            var userCreds = credentialsProvider.getCredentials(USER_PROPERTY_NAME);
            if ((userCreds != null) && (!userCreds.values().isEmpty())) {
                userCreds.values().forEach(h2ConnectOptions::setUser);
            }
            var passCreds = credentialsProvider.getCredentials(PASSWORD_PROPERTY_NAME);
            if ((passCreds != null) && (!passCreds.values().isEmpty())) {
                passCreds.values().forEach(h2ConnectOptions::setPassword);
            }
        }

        return createPool(vertx, poolOptions, h2ConnectOptions, dataSourceName, context);
    }

    private PoolOptions toPoolOptions(Integer eventLoopCount,
            DataSourceRuntimeConfig dataSourceRuntimeConfig,
            DataSourceReactiveRuntimeConfig dataSourceReactiveRuntimeConfig,
            DataSourceReactiveH2Config dataSourceReactiveH2Config) {
        PoolOptions poolOptions = new PoolOptions();

        poolOptions.setMaxSize(dataSourceReactiveRuntimeConfig.maxSize());

        if (dataSourceReactiveRuntimeConfig.idleTimeout().isPresent()) {
            var idleTimeout = Math.toIntExact(dataSourceReactiveRuntimeConfig.idleTimeout().get().toMillis());
            poolOptions.setIdleTimeout(idleTimeout).setIdleTimeoutUnit(TimeUnit.MILLISECONDS);
        }

        if (dataSourceReactiveRuntimeConfig.shared()) {
            poolOptions.setShared(true);
            dataSourceReactiveRuntimeConfig.name().ifPresent(poolOptions::setName);
        }

        if (dataSourceReactiveRuntimeConfig.eventLoopSize().isPresent()) {
            poolOptions.setEventLoopSize(Math.max(0, dataSourceReactiveRuntimeConfig.eventLoopSize().getAsInt()));
        } else if (eventLoopCount != null) {
            poolOptions.setEventLoopSize(Math.max(0, eventLoopCount));
        }

        if (dataSourceReactiveH2Config.connectionTimeout().isPresent()) {
            poolOptions.setConnectionTimeout(dataSourceReactiveH2Config.connectionTimeout().getAsInt());
            poolOptions.setConnectionTimeoutUnit(TimeUnit.SECONDS);
        }

        return poolOptions;
    }

    private JDBCConnectOptions toJDBCConnectOptions(String dataSourceName,
            DataSourceRuntimeConfig dataSourceRuntimeConfig,
            DataSourceReactiveRuntimeConfig dataSourceReactiveRuntimeConfig,
            DataSourceReactiveH2Config dataSourceReactiveH2Config) {
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

    private JDBCPool createPool(Vertx vertx, PoolOptions poolOptions, JDBCConnectOptions h2ConnectOptions,
            String dataSourceName, SyntheticCreationalContext<JDBCPool> context) {
        Instance<H2PoolCreator> instance;
        if (DataSourceUtil.isDefault(dataSourceName)) {
            instance = context.getInjectedReference(TYPE_LITERAL);
        } else {
            instance = context.getInjectedReference(TYPE_LITERAL,
                    new ReactiveDataSource.ReactiveDataSourceLiteral(dataSourceName));
        }
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
