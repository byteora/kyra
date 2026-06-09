package com.example.simple.sample.inheritance;

import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.annotation.ReflectMetadataLevel;

/** Sample: base class with inherited fields and methods exposed by generated reflector. */
@Reflect(metadata = ReflectMetadataLevel.METHOD, annotationMetadata = true)
@TestReflectTag("base")
public class BaseUser {
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String baseLabel(String prefix) {
        return prefix + id;
    }
}
