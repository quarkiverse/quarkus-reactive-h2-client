package io.quarkiverse.quarkus.reactive.h2.client.runtime;

import java.util.Optional;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface DataSourceReactiveH2Config {

    /**
     * Charset for connections.
     */
    Optional<String> charset();

    /**
     * Collation for connections.
     */
    Optional<String> collation();

    /**
     * Connection timeout in seconds
     */
    OptionalInt connectionTimeout();
}
