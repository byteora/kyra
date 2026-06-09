package org.byteora.kyra.processor;

import org.byteora.kyra.core.annotation.ReflectMetadataLevel;

import javax.lang.model.element.TypeElement;

public final class ReflectSpec {
    private final TypeElement typeElement;
    private final String suffix;
    private final ReflectMetadataLevel metadataLevel;
    private final boolean annotationMetadata;
    private final boolean generateClass;

    public ReflectSpec(TypeElement typeElement, String suffix, ReflectMetadataLevel metadataLevel, boolean annotationMetadata) {
        this(typeElement, suffix, metadataLevel, annotationMetadata, true);
    }

    public ReflectSpec(TypeElement typeElement, String suffix, ReflectMetadataLevel metadataLevel, boolean annotationMetadata, boolean generateClass) {
        this.typeElement = typeElement;
        this.suffix = suffix;
        this.metadataLevel = metadataLevel;
        this.annotationMetadata = annotationMetadata;
        this.generateClass = generateClass;
    }

    public TypeElement typeElement() {
        return typeElement;
    }

    public String suffix() {
        return suffix;
    }

    public ReflectMetadataLevel metadataLevel() {
        return metadataLevel;
    }

    public boolean annotationMetadata() {
        return annotationMetadata;
    }

    public boolean generateClass() {
        return generateClass;
    }
}
