package io.quarkiverse.quarkus.reactive.h2.client.runtime;

import java.util.Map;

import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;
import io.smallrye.config.WithUnnamedKey;

@ConfigMapping(prefix = "quarkus.datasource")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface DataSourcesReactiveH2Config {

    /**
     * Additional named datasources.
     */
    @ConfigDocSection
    @ConfigDocMapKey("datasource-name")
    @WithParentName
    @WithDefaults
    @WithUnnamedKey(DataSourceUtil.DEFAULT_DATASOURCE_NAME)
    Map<String, DataSourceReactiveH2OuterNamedConfig> dataSources();

    @ConfigGroup
    public interface DataSourceReactiveH2OuterNamedConfig {

        /**
         * The H2-specific configuration.
         */
        public DataSourceReactiveH2OuterNestedNamedConfig reactive();
    }

    @ConfigGroup
    public interface DataSourceReactiveH2OuterNestedNamedConfig {

        /**
         * The H2-specific configuration.
         */
        public DataSourceReactiveH2Config h2();
    }
}
