package com.example.simple.sample.inheritance;

import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.annotation.ReflectMetadataLevel;

/** Sample: base class with inherited fields and methods exposed by generated reflector. */
@Reflect(metadata = ReflectMetadataLevel.METHOD, annotationMetadata = true)
@TestReflectTag("base")
public class BaseUser {
    private Long id;
    private int level;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String baseLabel(String prefix) {
        return prefix + id;
    }
}
