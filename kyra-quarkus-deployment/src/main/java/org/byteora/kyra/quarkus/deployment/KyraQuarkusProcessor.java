package org.byteora.kyra.quarkus.deployment;

import org.byteora.kyra.orm.annotation.KyraScan;
import org.byteora.kyra.orm.query.TableInstaller;
import org.byteora.kyra.core.runtime.ReflectorInstaller;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import jakarta.enterprise.context.Dependent;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

class KyraQuarkusProcessor {
    private static final DotName KYRA_SCAN = DotName.createSimple(KyraScan.class.getName());
    private static final DotName REFLECTOR_INSTALLER = DotName.createSimple(ReflectorInstaller.class.getName());
    private static final DotName TABLE_INSTALLER = DotName.createSimple(TableInstaller.class.getName());
    private static final DotName DEPENDENT_SCOPE = DotName.createSimple(Dependent.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("kyra-quarkus");
    }

    @BuildStep
    void registerGeneratedMappers(CombinedIndexBuildItem combinedIndex,
                                  BuildProducer<AdditionalBeanBuildItem> additionalBeans,
                                  BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
                                  BuildProducer<ServiceProviderBuildItem> serviceProviders) {
        IndexView index = combinedIndex.getIndex();
        Set<String> mapperPackages = new LinkedHashSet<>();
        collectKyraScanMapperPackages(index, mapperPackages);
        Set<String> reflectorInstallers = findInstallerImplementations(index, REFLECTOR_INSTALLER);
        Set<String> tableInstallers = findInstallerImplementations(index, TABLE_INSTALLER);
        Set<String> installerImplementations = new LinkedHashSet<>();
        installerImplementations.addAll(reflectorInstallers);
        installerImplementations.addAll(tableInstallers);
        if (!installerImplementations.isEmpty()) {
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(installerImplementations.toArray(String[]::new))
                    .methods()
                    .build());
        }
        if (!reflectorInstallers.isEmpty()) {
            serviceProviders.produce(new ServiceProviderBuildItem(ReflectorInstaller.class.getName(), reflectorInstallers));
        }
        if (!tableInstallers.isEmpty()) {
            serviceProviders.produce(new ServiceProviderBuildItem(TableInstaller.class.getName(), tableInstallers));
        }

        Set<String> mapperImplementations = findMapperImplementations(index, mapperPackages);
        if (mapperImplementations.isEmpty()) {
            return;
        }

        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder()
                .setDefaultScope(DEPENDENT_SCOPE)
                .setUnremovable();
        for (String mapperImplementation : mapperImplementations) {
            builder.addBeanClass(mapperImplementation);
        }
        additionalBeans.produce(builder.build());
    }

    void collectKyraScanMapperPackages(IndexView index, Set<String> mapperPackages) {
        for (AnnotationInstance annotation : index.getAnnotations(KYRA_SCAN)) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            AnnotationValue mapperValue = annotation.value("mapper");
            if (mapperValue == null) {
                continue;
            }
            for (String mapperPackage : mapperValue.asStringArray()) {
                if (!mapperPackage.isBlank()) {
                    mapperPackages.add(mapperPackage);
                }
            }
        }
    }

    Set<String> findMapperImplementations(IndexView index, Set<String> mapperPackages) {
        LinkedHashSet<String> mapperImplementations = new LinkedHashSet<>();
        if (mapperPackages.isEmpty()) {
            return mapperImplementations;
        }
        for (ClassInfo classInfo : index.getKnownClasses()) {
            if (!Modifier.isInterface(classInfo.flags())) {
                continue;
            }
            String interfaceName = classInfo.name().toString();
            if (!matchesMapperPackage(interfaceName, mapperPackages)) {
                continue;
            }
            String implClassName = interfaceName + "Impl";
            if (index.getClassByName(DotName.createSimple(implClassName)) != null) {
                mapperImplementations.add(implClassName);
            }
        }
        return mapperImplementations;
    }

    Set<String> findInstallerImplementations(IndexView index) {
        LinkedHashSet<String> installerImplementations = new LinkedHashSet<>();
        collectInstallerImplementations(index, REFLECTOR_INSTALLER, installerImplementations);
        collectInstallerImplementations(index, TABLE_INSTALLER, installerImplementations);
        return installerImplementations;
    }

    Set<String> findInstallerImplementations(IndexView index, DotName installerType) {
        LinkedHashSet<String> installerImplementations = new LinkedHashSet<>();
        collectInstallerImplementations(index, installerType, installerImplementations);
        return installerImplementations;
    }

    @SuppressWarnings("deprecation")
    private void collectInstallerImplementations(IndexView index, DotName installerType, Set<String> installerImplementations) {
        for (ClassInfo classInfo : index.getKnownDirectImplementors(installerType)) {
            if (Modifier.isInterface(classInfo.flags()) || Modifier.isAbstract(classInfo.flags())) {
                continue;
            }
            installerImplementations.add(classInfo.name().toString());
        }
    }

    private boolean matchesMapperPackage(String className, Set<String> mapperPackages) {
        for (String mapperPackage : mapperPackages) {
            if (className.startsWith(mapperPackage + ".")) {
                return true;
            }
        }
        return false;
    }
}
