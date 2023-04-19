package io.quarkiverse.quarkus.reactive.h2.client.test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.vertx.jdbcclient.JDBCPool;

@Path("/dev")
public class DevModeResource {

    @Inject
    JDBCPool client;

    @GET
    @Path("/dbname")
    @Produces(MediaType.TEXT_PLAIN)
    public CompletionStage<Response> getErrorMessage() {
        CompletableFuture<Response> future = new CompletableFuture<>();
        client.query("SELECT CATALOG_NAME FROM INFORMATION_SCHEMA.SCHEMATA").execute(
                ar -> future.complete(Response.ok(ar.result().iterator().next().getString("CATALOG_NAME")).build()));
        return future;
    }
}
