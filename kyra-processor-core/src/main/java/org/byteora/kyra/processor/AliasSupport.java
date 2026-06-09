package org.byteora.kyra.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import java.util.Map;

/**
 * Shared resolution of the {@code @org.byteora.kyra.core.annotation.Alias} annotation.
 *
 * <p>Lives in {@code kyra-processor-core} so every annotation processor (JSON, ORM, ...)
 * resolves aliases the same way without depending on the module that declares the annotation.
 */
public final class AliasSupport {
    private static final String ALIAS_ANNOTATION = "org.byteora.kyra.core.annotation.Alias";

    private AliasSupport() {
    }

    /**
     * Returns the non-blank {@code value()} of the {@code @Alias} annotation on the given element,
     * or {@code null} when the element is not annotated (or the value is blank).
     */
    public static String aliasValue(Element element) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (!annotationMirror.getAnnotationType().toString().equals(ALIAS_ANNOTATION)) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet()) {
                if (entry.getKey().getSimpleName().contentEquals("value")) {
                    Object value = entry.getValue().getValue();
                    if (value instanceof String alias && !alias.isBlank()) {
                        return alias;
                    }
                }
            }
        }
        return null;
    }
}
