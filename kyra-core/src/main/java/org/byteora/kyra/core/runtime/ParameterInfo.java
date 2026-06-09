package org.byteora.kyra.core.runtime;

import java.lang.reflect.Type;

public record ParameterInfo(String name, Type type, AnnotationMeta[] annotations) {
}
