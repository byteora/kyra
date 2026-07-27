package org.byteora.kyra.core;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Captures a generic {@link Type} for APIs whose type cannot be represented by a {@link Class}.
 *
 * <pre>{@code
 * TypeRef<Pair<String, Long>> type = new TypeRef<>() {};
 * }</pre>
 */
public abstract class TypeRef<T> {
    private final Type type;

    protected TypeRef() {
        Type superType = getClass().getGenericSuperclass();
        if (!(superType instanceof ParameterizedType parameterizedType)) {
            throw new IllegalStateException("TypeRef must be created with a generic type parameter");
        }
        this.type = parameterizedType.getActualTypeArguments()[0];
    }

    public final Type type() {
        return type;
    }
}
