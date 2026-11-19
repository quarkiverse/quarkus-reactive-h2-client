package io.quarkiverse.quarkus.reactive.h2.client.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;

import io.quarkiverse.quarkus.reactive.h2.client.H2PoolCreator;
import io.quarkiverse.quarkus.reactive.h2.client.runtime.DataSourcesReactiveH2Config;
import io.quarkiverse.quarkus.reactive.h2.client.runtime.H2PoolRecorder;
import io.quarkiverse.quarkus.reactive.h2.client.runtime.H2ServiceBindingConverter;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.deployment.devui.Name;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.core.Phase;
import io.quarkus.core.deployment.service.ServiceRegistrar;
import io.quarkus.datasource.common.runtime.DatabaseKind;
import io.quarkus.datasource.deployment.spi.DataSourceDefinedBuildItem;
import io.quarkus.datasource.deployment.spi.DefaultDataSourceDbKindBuildItem;
import io.quarkus.datasource.runtime.DataSourcesRuntimeConfig;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.devservices.datasource.common.DevServicesDatasourceConfigurationHandlerBuildItem;
import io.quarkus.reactive.datasource.runtime.DataSourcesReactiveRuntimeConfig;
import io.quarkus.reactive.datasource.spi.ReactivePoolBuildItem;
import io.quarkus.runtime.util.ProgrammingParadigm;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;

class ReactiveH2ClientProcessor {

    private static final String FEATURE = "reactive-h2-client";
    private static final String TYPE = "H2";
    private static final String HEALTH_CHECK_SQL = "SELECT 1";

    /**
     * Registers a {@link Pool} service for each reactive H2 datasource.
     * <p>
     * The reactive datasource extension exposes these services as {@link Pool} and Mutiny {@code Pool} beans
     * and includes them in its health check, using the SQL given in the {@link ReactivePoolBuildItem}.
     */
    @BuildStep
    ServiceStartBuildItem build(BuildProducer<FeatureBuildItem> feature,
            BuildProducer<ReactivePoolBuildItem> pools,
            ServiceRegistrar serviceRegistrar,
            List<DataSourceDefinedBuildItem> definedDataSources) {

        feature.produce(new FeatureBuildItem(FEATURE));

        for (DataSourceDefinedBuildItem dataSource : definedDataSources) {
            if (!dataSource.getParadigms().contains(ProgrammingParadigm.REACTIVE)
                    || !DatabaseKind.isH2(dataSource.getDbKind())) {
                continue;
            }
            String dataSourceName = dataSource.getName();

            serviceRegistrar.forService(Pool.class, dataSourceName)
                    .atPhase(Phase.DATA)
                    .require(DataSourcesRuntimeConfig.class)
                    .require(DataSourcesReactiveRuntimeConfig.class)
                    .require(DataSourcesReactiveH2Config.class)
                    .require(Vertx.class)
                    .require(IntSupplier.class, "io.quarkus.vertx.event-loop-count")
                    // H2PoolCreator beans are looked up when creating the pool
                    .require(BeanContainer.class)
                    // no pool for an inactive datasource
                    .optional()
                    .onStart((ctx, runtimeConfig, reactiveRuntimeConfig, h2Config, vertx, eventLoopCount,
                            beanContainer) -> {
                        Optional<Pool> pool = H2PoolRecorder.createPool(dataSourceName, runtimeConfig,
                                reactiveRuntimeConfig, h2Config, vertx, eventLoopCount.getAsInt());
                        // the service only counts as stopped once the connections are closed: an in-memory H2 database
                        // lives as long as a connection is open, a new application could otherwise still see it
                        pool.ifPresent(
                                p -> ctx.onStopAsync(stopContext -> p.close().onComplete(ar -> stopContext.stopComplete())));
                        return pool;
                    });
            pools.produce(new ReactivePoolBuildItem(dataSourceName, TYPE, HEALTH_CHECK_SQL));
        }

        return new ServiceStartBuildItem(FEATURE);
    }

    @BuildStep
    DevServicesDatasourceConfigurationHandlerBuildItem devDbHandler() {
        return DevServicesDatasourceConfigurationHandlerBuildItem.reactive(DatabaseKind.H2);
    }

    @BuildStep
    void unremoveableBeans(BuildProducer<UnremovableBeanBuildItem> producer) {
        producer.produce(UnremovableBeanBuildItem.beanTypes(H2PoolCreator.class));
    }

    @BuildStep
    void validateBeans(ValidationPhaseBuildItem validationPhase,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> errors) {
        // no two H2PoolCreator beans can be associated with the same datasource
        Map<String, Boolean> seen = new HashMap<>();
        for (BeanInfo beanInfo : validationPhase.getContext().beans()
                .matchBeanTypes(new H2PoolCreatorBeanClassPredicate())) {
            Set<Name> qualifiers = new TreeSet<>(); // use a TreeSet in order to get a predictable iteration order
            for (AnnotationInstance qualifier : beanInfo.getQualifiers()) {
                qualifiers.add(Name.from(qualifier));
            }
            String qualifiersStr = qualifiers.stream().map(Name::toString).collect(Collectors.joining("_"));
            if (seen.getOrDefault(qualifiersStr, false)) {
                errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                        new IllegalStateException(
                                "There can be at most one bean of type '" + H2PoolCreator.class.getName()
                                        + "' for each datasource.")));
            } else {
                seen.put(qualifiersStr, true);
            }
        }
    }

    @BuildStep
    void registerServiceBinding(Capabilities capabilities, BuildProducer<ServiceProviderBuildItem> serviceProvider,
            BuildProducer<DefaultDataSourceDbKindBuildItem> dbKind) {
        if (capabilities.isPresent(Capability.KUBERNETES_SERVICE_BINDING)) {
            serviceProvider.produce(
                    new ServiceProviderBuildItem("io.quarkus.kubernetes.service.binding.runtime.ServiceBindingConverter",
                            H2ServiceBindingConverter.class.getName()));
        }
        dbKind.produce(new DefaultDataSourceDbKindBuildItem(DatabaseKind.H2));
    }

    private static class H2PoolCreatorBeanClassPredicate implements Predicate<Set<Type>> {
        private static final Type H2_POOL_CREATOR = Type.create(DotName.createSimple(H2PoolCreator.class.getName()),
                Type.Kind.CLASS);

        @Override
        public boolean test(Set<Type> types) {
            return types.contains(H2_POOL_CREATOR);
        }
    }
}
