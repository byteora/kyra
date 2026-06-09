package com.example.simple;

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
}
