package io.quarkiverse.quarkus.reactive.h2.client.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import io.quarkiverse.quarkus.reactive.h2.client.H2PoolCreator;
import io.quarkiverse.quarkus.reactive.h2.client.runtime.H2PoolRecorder;
import io.quarkiverse.quarkus.reactive.h2.client.runtime.H2ServiceBindingConverter;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem.ExtendedBeanConfigurator;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.deployment.devui.Name;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.datasource.common.runtime.DatabaseKind;
import io.quarkus.datasource.deployment.spi.DefaultDataSourceDbKindBuildItem;
import io.quarkus.datasource.deployment.spi.DevServicesDatasourceConfigurationHandlerBuildItem;
import io.quarkus.datasource.runtime.DataSourceBuildTimeConfig;
import io.quarkus.datasource.runtime.DataSourceSupport;
import io.quarkus.datasource.runtime.DataSourcesBuildTimeConfig;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.reactive.datasource.ReactiveDataSource;
import io.quarkus.reactive.datasource.deployment.VertxPoolBuildItem;
import io.quarkus.reactive.datasource.runtime.DataSourceReactiveBuildTimeConfig;
import io.quarkus.reactive.datasource.runtime.DataSourcesReactiveBuildTimeConfig;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;
import io.quarkus.vertx.core.deployment.EventLoopCountBuildItem;
import io.quarkus.vertx.deployment.VertxBuildItem;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;

class ReactiveH2ClientProcessor {

    private static final String FEATURE = "reactive-h2-client";

    private static final ParameterizedType POOL_INJECTION_TYPE = ParameterizedType.create(DotName.createSimple(Instance.class),
            new Type[] { ClassType.create(DotName.createSimple(H2PoolCreator.class.getName())) }, null);
    private static final AnnotationInstance[] EMPTY_ANNOTATIONS = new AnnotationInstance[0];
    private static final DotName REACTIVE_DATASOURCE = DotName.createSimple(ReactiveDataSource.class);

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    ServiceStartBuildItem build(BuildProducer<FeatureBuildItem> feature,
            BuildProducer<H2PoolBuildItem> h2Pool,
            BuildProducer<VertxPoolBuildItem> vertxPool,
            H2PoolRecorder recorder,
            VertxBuildItem vertx,
            EventLoopCountBuildItem eventLoopCount,
            ShutdownContextBuildItem shutdown,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<ExtensionSslNativeSupportBuildItem> sslNativeSupport,
            DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig,
            DataSourcesReactiveBuildTimeConfig dataSourcesReactiveBuildTimeConfig,
            List<DefaultDataSourceDbKindBuildItem> defaultDataSourceDbKindBuildItems,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {

        feature.produce(new FeatureBuildItem(FEATURE));

        for (String dataSourceName : dataSourcesBuildTimeConfig.dataSources().keySet()) {
            createPoolIfDefined(recorder, vertx, eventLoopCount, shutdown, h2Pool, syntheticBeans, dataSourceName,
                    dataSourcesBuildTimeConfig, dataSourcesReactiveBuildTimeConfig,
                    defaultDataSourceDbKindBuildItems, curateOutcomeBuildItem);
        }

        vertxPool.produce(new VertxPoolBuildItem());
        return new ServiceStartBuildItem(FEATURE);
    }

    @BuildStep
    List<DevServicesDatasourceConfigurationHandlerBuildItem> devDbHandler() {
        return List.of(DevServicesDatasourceConfigurationHandlerBuildItem.reactive(DatabaseKind.H2));
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

    /**
     * The health check needs to be produced in a separate method to avoid a circular dependency (the Vert.x instance creation
     * consumes the AdditionalBeanBuildItems).
     */
    @BuildStep
    void addHealthCheck(
            Capabilities capabilities,
            BuildProducer<HealthBuildItem> healthChecks,
            DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig,
            DataSourcesReactiveBuildTimeConfig dataSourcesReactiveBuildTimeConfig,
            List<DefaultDataSourceDbKindBuildItem> defaultDataSourceDbKindBuildItems,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {
        if (!capabilities.isPresent(Capability.SMALLRYE_HEALTH)) {
            return;
        }

        if (!hasPools(dataSourcesBuildTimeConfig, dataSourcesReactiveBuildTimeConfig, defaultDataSourceDbKindBuildItems,
                curateOutcomeBuildItem)) {
            return;
        }

        healthChecks.produce(
                new HealthBuildItem("io.quarkiverse.quarkus.reactive.h2.client.runtime.health.ReactiveH2DataSourcesHealthCheck",
                        dataSourcesBuildTimeConfig.healthEnabled()));
    }

    private void createPoolIfDefined(H2PoolRecorder recorder,
            VertxBuildItem vertx,
            EventLoopCountBuildItem eventLoopCount,
            ShutdownContextBuildItem shutdown,
            BuildProducer<H2PoolBuildItem> h2Pool,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            String dataSourceName,
            DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig,
            DataSourcesReactiveBuildTimeConfig dataSourcesReactiveBuildTimeConfig,
            List<DefaultDataSourceDbKindBuildItem> defaultDataSourceDbKindBuildItems,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {

        if (!isReactiveH2PoolDefined(dataSourcesBuildTimeConfig, dataSourcesReactiveBuildTimeConfig, dataSourceName,
                defaultDataSourceDbKindBuildItems, curateOutcomeBuildItem)) {
            return;
        }

        Function<SyntheticCreationalContext<JDBCPool>, JDBCPool> poolFunction = recorder.configureH2Pool(vertx.getVertx(),
                eventLoopCount.getEventLoopCount(),
                dataSourceName,
                shutdown);
        h2Pool.produce(new H2PoolBuildItem(dataSourceName, poolFunction));

        ExtendedBeanConfigurator h2PoolBeanConfigurator = SyntheticBeanBuildItem.configure(JDBCPool.class)
                .defaultBean()
                .addType(Pool.class)
                .scope(ApplicationScoped.class)
                .addInjectionPoint(POOL_INJECTION_TYPE, injectionPointAnnotations(dataSourceName))
                .addInjectionPoint(ClassType.create(DataSourceSupport.class))
                .createWith(poolFunction)
                .unremovable()
                .setRuntimeInit();

        addQualifiers(h2PoolBeanConfigurator, dataSourceName);

        syntheticBeans.produce(h2PoolBeanConfigurator.done());

        ExtendedBeanConfigurator mutinyH2PoolConfigurator = SyntheticBeanBuildItem
                .configure(io.vertx.mutiny.jdbcclient.JDBCPool.class)
                .defaultBean()
                .addType(io.vertx.mutiny.sqlclient.Pool.class)
                .scope(ApplicationScoped.class)
                .addInjectionPoint(POOL_INJECTION_TYPE, injectionPointAnnotations(dataSourceName))
                .addInjectionPoint(ClassType.create(DataSourceSupport.class))
                .createWith(recorder.mutinyH2Pool(poolFunction))
                .unremovable()
                .setRuntimeInit();

        addQualifiers(mutinyH2PoolConfigurator, dataSourceName);

        syntheticBeans.produce(mutinyH2PoolConfigurator.done());
    }

    private AnnotationInstance[] injectionPointAnnotations(String dataSourceName) {
        if (DataSourceUtil.isDefault(dataSourceName)) {
            return EMPTY_ANNOTATIONS;
        }
        return new AnnotationInstance[] {
                AnnotationInstance.builder(REACTIVE_DATASOURCE).add("value", dataSourceName).build() };
    }

    private static boolean isReactiveH2PoolDefined(DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig,
            DataSourcesReactiveBuildTimeConfig dataSourcesReactiveBuildTimeConfig, String dataSourceName,
            List<DefaultDataSourceDbKindBuildItem> defaultDataSourceDbKindBuildItems,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {
        DataSourceBuildTimeConfig dataSourceBuildTimeConfig = dataSourcesBuildTimeConfig
                .dataSources().get(dataSourceName);
        DataSourceReactiveBuildTimeConfig dataSourceReactiveBuildTimeConfig = dataSourcesReactiveBuildTimeConfig
                .getDataSourceReactiveBuildTimeConfig(dataSourceName);

        Optional<String> dbKind = DefaultDataSourceDbKindBuildItem.resolve(dataSourceBuildTimeConfig.dbKind(),
                defaultDataSourceDbKindBuildItems,
                !DataSourceUtil.isDefault(dataSourceName) || dataSourceBuildTimeConfig.devservices().enabled()
                        .orElse(dataSourcesBuildTimeConfig.dataSources().isEmpty()),
                curateOutcomeBuildItem);

        return dbKind.filter(s -> DatabaseKind.isH2(s) && dataSourceReactiveBuildTimeConfig.enabled()).isPresent();
    }

    private boolean hasPools(DataSourcesBuildTimeConfig dataSourcesBuildTimeConfig,
            DataSourcesReactiveBuildTimeConfig dataSourcesReactiveBuildTimeConfig,
            List<DefaultDataSourceDbKindBuildItem> defaultDataSourceDbKindBuildItems,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {
        if (isReactiveH2PoolDefined(dataSourcesBuildTimeConfig, dataSourcesReactiveBuildTimeConfig,
                DataSourceUtil.DEFAULT_DATASOURCE_NAME, defaultDataSourceDbKindBuildItems, curateOutcomeBuildItem)) {
            return true;
        }

        for (String dataSourceName : dataSourcesBuildTimeConfig.dataSources().keySet()) {
            if (isReactiveH2PoolDefined(dataSourcesBuildTimeConfig, dataSourcesReactiveBuildTimeConfig,
                    dataSourceName, defaultDataSourceDbKindBuildItems, curateOutcomeBuildItem)) {
                return true;
            }
        }

        return false;
    }

    private static void addQualifiers(ExtendedBeanConfigurator configurator, String dataSourceName) {
        if (DataSourceUtil.isDefault(dataSourceName)) {
            configurator.addQualifier(DotNames.DEFAULT);
        } else {
            configurator.addQualifier().annotation(DotNames.NAMED).addValue("value", dataSourceName).done();
            configurator.addQualifier().annotation(ReactiveDataSource.class).addValue("value", dataSourceName)
                    .done();
        }
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
