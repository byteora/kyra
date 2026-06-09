package org.byteora.kyra.json;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field whose content is flattened into the enclosing JSON object instead of being
 * written as a nested object. This annotation is only honored by the JSON mapper.
 *
 * <p>Two shapes are supported:
 * <ul>
 *     <li>a {@code Map<String, V>} field is treated as dynamic ("any") properties: each entry is
 *     written at the parent level, and any unclaimed properties are collected back into the map on
 *     read;</li>
 *     <li>any other object (including a generic type variable) is "unwrapped": its own fields are
 *     written at the parent level, and matching properties are routed back to it on read.</li>
 * </ul>
 *
 * <p>The annotation is read at runtime through the generic annotation-metadata channel, so the
 * enclosing type must be annotated with {@code @Reflect(annotationMetadata = true)} for flattening
 * to take effect. Otherwise the field is serialized as a normal nested value.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Flatten {
    /**
     * Prefix added to every flattened property name, used to disambiguate multiple flattened
     * fields that would otherwise collide.
     */
    String prefix() default "";
}
