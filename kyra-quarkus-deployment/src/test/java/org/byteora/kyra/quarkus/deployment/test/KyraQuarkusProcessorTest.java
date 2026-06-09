package org.byteora.kyra.quarkus.deployment;

import org.byteora.kyra.core.runtime.ReflectorInstaller;
import org.byteora.kyra.quarkus.deployment.test.config.TestMapperKyraConfig;
import org.byteora.kyra.quarkus.deployment.test.mapper.TestUserMapper;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KyraQuarkusProcessorTest {
    @Test
    void shouldDiscoverGeneratedMapperImplementation() throws Exception {
        KyraQuarkusProcessor processor = new KyraQuarkusProcessor();
        IndexView index = indexOf(
                TestMapperKyraConfig.class,
                TestUserMapper.class,
                Class.forName("org.byteora.kyra.quarkus.deployment.test.mapper.TestUserMapperImpl")
        );

        Set<String> mapperPackages = new LinkedHashSet<>();
        processor.collectKyraScanMapperPackages(index, mapperPackages);

        assertEquals(Set.of("org.byteora.kyra.quarkus.deployment.test.mapper"), mapperPackages);

        Set<String> mapperImplementations = processor.findMapperImplementations(index, mapperPackages);
        assertEquals(Set.of("org.byteora.kyra.quarkus.deployment.test.mapper.TestUserMapperImpl"), mapperImplementations);
    }

    @Test
    void shouldDiscoverGeneratedInstallerImplementations() throws Exception {
        KyraQuarkusProcessor processor = new KyraQuarkusProcessor();
        IndexView index = indexOf(TestReflectorInstaller.class);

        Set<String> installerImplementations = processor.findInstallerImplementations(index);

        assertEquals(Set.of(TestReflectorInstaller.class.getName()), installerImplementations);
    }

    @Test
    void shouldOnlyMatchConfiguredMapperPackages() throws Exception {
        KyraQuarkusProcessor processor = new KyraQuarkusProcessor();
        IndexView index = indexOf(
                TestMapperKyraConfig.class,
                TestUserMapper.class,
                Class.forName("org.byteora.kyra.quarkus.deployment.test.mapper.TestUserMapperImpl")
        );

        Set<String> mapperImplementations = processor.findMapperImplementations(
                index,
                Set.of("org.byteora.kyra.quarkus.deployment.test.other")
        );

        assertTrue(mapperImplementations.isEmpty());
    }

    private IndexView indexOf(Class<?>... types) throws IOException {
        Indexer indexer = new Indexer();
        for (Class<?> type : types) {
            String resourceName = type.getName().replace('.', '/') + ".class";
            try (InputStream inputStream = type.getClassLoader().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new IllegalStateException("Missing class resource: " + resourceName);
                }
                indexer.index(inputStream);
            }
        }
        return indexer.complete();
    }

    static final class TestReflectorInstaller implements ReflectorInstaller {
        @Override
        public void install() {
        }
    }
}
