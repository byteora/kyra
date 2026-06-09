package org.byteora.kyra.core.runtime;

import java.lang.reflect.Type;

public record ClassInfo(Class<?> type, Type superType, int modifiers, AnnotationMeta[] annotations, ParameterInfo[] params) {
}
