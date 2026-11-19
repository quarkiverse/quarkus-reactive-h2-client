package io.quarkiverse.quarkus.reactive.h2.client.test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.sqlclient.Pool;

public class H2PoolProducerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(BeanUsingBareH2Client.class)
                    .addClasses(BeanUsingMutinyH2Client.class)
                    .addAsResource("application-default-datasource.properties", "application.properties"));

    @Inject
    BeanUsingBareH2Client beanUsingBare;

    @Inject
    BeanUsingMutinyH2Client beanUsingMutiny;

    @Test
    public void testVertxInjection() {
        beanUsingBare.verify()
                .thenCompose(v -> beanUsingMutiny.verify())
                .toCompletableFuture()
                .join();
    }

    @ApplicationScoped
    static class BeanUsingBareH2Client {

        @Inject
        Pool h2Client;

        public CompletionStage<Void> verify() {
            CompletableFuture<Void> cf = new CompletableFuture<>();
            h2Client.query("SELECT 1").execute().onComplete(ar -> cf.complete(null));
            return cf;
        }
    }

    @ApplicationScoped
    static class BeanUsingMutinyH2Client {

        @Inject
        io.vertx.mutiny.sqlclient.Pool mutinyH2Client;

        public CompletionStage<Void> verify() {
            return mutinyH2Client.query("SELECT 1").execute()
                    .onItem().ignore().andContinueWithNull()
                    .onFailure().recoverWithItem((Void) null)
                    .subscribeAsCompletionStage();
        }
    }
}
