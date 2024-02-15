package io.quarkiverse.quarkus.reactive.h2.client;

import io.quarkus.reactive.datasource.ReactiveDataSource;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;

/**
 * This interface is an integration point that allows users to use the {@link Vertx}, {@link PoolOptions} and
 * {@link JDBCConnectOptions} objects configured automatically by Quarkus, in addition to a custom strategy
 * for creating the final {@link JDBCPool}.
 *
 * Implementations of this class are meant to be used as CDI beans.
 * If a bean of this type is used without a {@link ReactiveDataSource} qualifier, then it's applied to the default datasource,
 * otherwise it applies to the datasource matching the name of the annotation.
 */
public interface H2PoolCreator {

    JDBCPool create(Input input);

    interface Input {

        Vertx vertx();

        PoolOptions poolOptions();

        JDBCConnectOptions h2ConnectOptionsList();
    }
}
