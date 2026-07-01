package com.example.simple;

import com.example.simple.entity.StandaloneEntity;
import com.example.simple.entity.User;
import com.example.simple.support.GeneratedTypeNames;
import org.byteora.kyra.orm.query.Tables;
import org.byteora.kyra.core.runtime.ReflectorRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistryInstallationTest {
    @Test
    void serviceInstallersShouldInstallGeneratedTablesAndReflectors() throws Exception {
        ReflectorRegistry.clear();
        Tables.clear();
        try {
            assertThrows(ClassNotFoundException.class,
                    () -> Class.forName("gen.KyraSimpleConfigGenerated"));

            assertNotNull(Tables.get(User.class));
            assertNotNull(ReflectorRegistry.get(User.class));
            assertEquals(GeneratedTypeNames.tableTypeName(User.class), Tables.get(User.class).getClass().getName());
            assertEquals(GeneratedTypeNames.reflectorTypeName(User.class), ReflectorRegistry.get(User.class).getClass().getName());
        } finally {
            ReflectorRegistry.clear();
            Tables.clear();
        }
    }

    @Test
    void scannedEntityWithoutMapperShouldGenerateTableAndReflector() {
        ReflectorRegistry.clear();
        Tables.clear();
        try {
            assertNotNull(Tables.get(StandaloneEntity.class),
                    "scanned entity without a mapper should still register a Table");
            assertNotNull(ReflectorRegistry.get(StandaloneEntity.class),
                    "scanned entity without a mapper should still register a Reflector");
            assertEquals(GeneratedTypeNames.tableTypeName(StandaloneEntity.class),
                    Tables.get(StandaloneEntity.class).getClass().getName());
            assertEquals(GeneratedTypeNames.reflectorTypeName(StandaloneEntity.class),
                    ReflectorRegistry.get(StandaloneEntity.class).getClass().getName());
        } finally {
            ReflectorRegistry.clear();
            Tables.clear();
        }
    }
}
