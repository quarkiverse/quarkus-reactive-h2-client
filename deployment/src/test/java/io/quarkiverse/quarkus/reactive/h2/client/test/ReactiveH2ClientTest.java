package io.quarkiverse.quarkus.reactive.h2.client.test;

import static io.restassured.RestAssured.given;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class ReactiveH2ClientTest {

    // Start unit test with extension loaded
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addAsResource("application-default-datasource.properties", "application.properties"));

    @Test
    public void testConnect() {
        given().when().get("/test")
                .then()
                .statusCode(200)
                .body(CoreMatchers.equalTo("OK"));
    }
}
