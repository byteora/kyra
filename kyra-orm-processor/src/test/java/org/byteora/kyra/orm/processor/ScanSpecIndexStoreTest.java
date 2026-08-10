package org.byteora.kyra.orm.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScanSpecIndexStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldRoundTripScanConfigMetadataViaClassOutput() throws Exception {
        ScanSpecIndexStore store = new ScanSpecIndexStore(new TestFiler(tempDir));

        store.write(List.of(
                new ScanSpecIndexStore.ScanConfigMetadata(
                        "com.example.Config",
                        List.of("com.example.entity"),
                        List.of("com.example.mapper"),
                        List.of("com.example.entity.User"),
                        List.of("com.example.mapper.UserMapper")
                )
        ), List.of());
        assertEquals(
                "com.example.Config|com.example.entity|com.example.mapper|com.example.entity.User|com.example.mapper.UserMapper\n",
                Files.readString(tempDir.resolve("class-output/gen/kyra-scan.idx"))
        );

        List<ScanSpecIndexStore.ScanConfigMetadata> loaded = store.read();

        assertEquals(1, loaded.size());
        assertEquals("com.example.Config", loaded.getFirst().configQualifiedName());
        assertEquals(List.of("com.example.entity"), loaded.getFirst().tablePackages());
        assertEquals(List.of("com.example.mapper"), loaded.getFirst().mapperPackages());
        assertEquals(List.of("com.example.entity.User"), loaded.getFirst().typeNames());
        assertEquals(List.of("com.example.mapper.UserMapper"), loaded.getFirst().mapperTypeNames());
    }
}
