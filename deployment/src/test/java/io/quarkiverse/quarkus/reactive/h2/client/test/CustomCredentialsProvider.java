package io.quarkiverse.quarkus.reactive.h2.client.test;

import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import io.quarkus.credentials.CredentialsProvider;

@ApplicationScoped
public class CustomCredentialsProvider implements CredentialsProvider {

    private static final Logger log = Logger.getLogger(CustomCredentialsProvider.class.getName());

    @Override
    public Map<String, String> getCredentials(String credentialsProviderName) {
        Map<String, String> properties = new HashMap<>();
        properties.put(USER_PROPERTY_NAME, "hibernate_orm_test");
        properties.put(PASSWORD_PROPERTY_NAME, "hibernate_orm_test");
        log.info("credentials provider returning " + properties);
        return properties;
    }
}
