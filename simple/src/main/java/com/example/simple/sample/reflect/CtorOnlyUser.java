package com.example.simple.sample.reflect;

import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.annotation.ReflectMetadataLevel;

/** Sample: constructor-only type without public getters/setters. */
@Reflect(metadata = ReflectMetadataLevel.METHOD)
public class CtorOnlyUser {
    private String name;

    public CtorOnlyUser(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }
}
