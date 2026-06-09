package com.example.simple.config;

import org.byteora.kyra.orm.annotation.KyraScan;

/**
 * Entry point for the {@code simple} showcase module.
 * <p>
 * Package layout:
 * <ul>
 *     <li>{@code entity} / {@code dto} — ORM entities and query objects scanned by {@link KyraScan}</li>
 *     <li>{@code mapper} — mapper interfaces with XML under {@code src/main/resources/mapper}</li>
 *     <li>{@code common} — reusable mapper capability traits ({@code ReadMapper}, {@code WriteMapper}, …)</li>
 *     <li>{@code sample.reflect} / {@code sample.inheritance} — {@code @Reflect} feature samples (record, enum, inheritance)</li>
 * </ul>
 * Compile with {@code -Akyra.mapper=.../src/main/resources/mapper} so the annotation processor can locate XML.
 */
@KyraScan(
        entity = {"com.example.simple.entity"},
        mapper = {"com.example.simple.mapper"}
)
public class KyraSimpleConfig {
}
