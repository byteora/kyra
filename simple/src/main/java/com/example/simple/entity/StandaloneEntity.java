package com.example.simple.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * Scanned entity with no corresponding mapper. Verifies that {@code @KyraScan} generates both a
 * {@code xxxTable} and a {@code xxxReflector} even when no mapper references the entity.
 */
@Getter
@Setter
public class StandaloneEntity {
    private Long id;
    private String label;
}
