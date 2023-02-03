package io.quarkiverse.quarkus.reactive.h2.client.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ReactiveH2ClientResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/reactive-h2-client")
                .then()
                .statusCode(200)
                .body(is("Hello reactive-h2-client"));
    }
}
