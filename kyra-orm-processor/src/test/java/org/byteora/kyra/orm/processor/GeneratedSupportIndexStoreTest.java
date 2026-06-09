package org.byteora.kyra.orm.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedSupportIndexStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldDropInvalidEntriesWhenLoadingAndRewriteOnlyValidOnes() throws Exception {
        GeneratedSupportIndexStore store = new GeneratedSupportIndexStore(new TestFiler(tempDir));
        store.upsertReflector("com.example.User", "gen.demo.UserReflector");
        store.upsertTable("com.example.User", "gen.demo.UserTable");
        store.upsertReflector("missing.User", "gen.demo.MissingReflector");
        store.write(List.of());
        store.writeReflectorInstallerService("gen.demo.DemoReflectorInstaller", List.of());

        GeneratedSupportIndexStore reloaded = new GeneratedSupportIndexStore(new TestFiler(tempDir));
        reloaded.load((entityTypeName, generatedTypeName) -> !entityTypeName.startsWith("missing."));

        assertEquals(1, reloaded.reflectors().size());
        assertEquals("com.example.User", reloaded.reflectors().getFirst().entityTypeName());
        assertEquals(1, reloaded.tables().size());
        assertTrue(reloaded.isDirty());

        reloaded.write(List.of());
        GeneratedSupportIndexStore rewritten = new GeneratedSupportIndexStore(new TestFiler(tempDir));
        rewritten.load((entityTypeName, generatedTypeName) -> true);

        assertEquals(1, rewritten.reflectors().size());
        assertEquals("gen.demo.UserReflector", rewritten.reflectors().getFirst().reflectorTypeName());
        assertEquals(1, rewritten.tables().size());
        assertEquals("gen.demo.UserTable", rewritten.tables().getFirst().tableTypeName());
        assertEquals(
                "gen.demo.DemoReflectorInstaller\n",
                Files.readString(tempDir.resolve("class-output/META-INF/services/org.byteora.kyra.core.runtime.ReflectorInstaller"))
        );
    }

    @Test
    void shouldRetainEntriesWhenValidatorKeepsUnresolvedTypes() throws Exception {
        GeneratedSupportIndexStore store = new GeneratedSupportIndexStore(new TestFiler(tempDir));
        store.upsertReflector("com.example.User", "gen.demo.UserReflector");
        store.upsertReflector("com.example.Order", "gen.demo.OrderReflector");
        store.upsertTable("com.example.User", "gen.demo.UserTable");
        store.write(List.of());

        // Mirrors the processor validator under IDEA JPS incremental builds:
        // an entity that cannot be resolved this round (treated as null ->
        // returns true) must be retained, not pruned, so the index never
        // shrinks one-way across incremental rounds.
        GeneratedSupportIndexStore reloaded = new GeneratedSupportIndexStore(new TestFiler(tempDir));
        reloaded.load((entityTypeName, generatedTypeName) -> true);

        assertEquals(2, reloaded.reflectors().size());
        assertEquals(1, reloaded.tables().size());
        assertFalse(reloaded.isDirty());

        reloaded.write(List.of());
        GeneratedSupportIndexStore rewritten = new GeneratedSupportIndexStore(new TestFiler(tempDir));
        rewritten.load((entityTypeName, generatedTypeName) -> true);

        assertEquals(2, rewritten.reflectors().size());
        assertEquals(1, rewritten.tables().size());
    }
}
