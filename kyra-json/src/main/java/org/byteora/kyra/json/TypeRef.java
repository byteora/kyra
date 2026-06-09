package org.byteora.kyra.json;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

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
