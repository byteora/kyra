package org.byteora.kyra.json;

import org.byteora.kyra.core.runtime.RuntimeTypes;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.Map;

final class Types {
    private Types() {
    }

    static Class<?> rawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof GenericArrayType arrayType) {
            Class<?> componentType = rawType(arrayType.getGenericComponentType());
            return java.lang.reflect.Array.newInstance(componentType, 0).getClass();
        }
        if (type instanceof TypeVariable<?> typeVariable) {
            return rawType(typeVariable.getBounds()[0]);
        }
        if (type instanceof WildcardType wildcardType) {
            Type[] upperBounds = wildcardType.getUpperBounds();
            return upperBounds.length == 0 ? Object.class : rawType(upperBounds[0]);
        }
        return Object.class;
    }

    static Map<TypeVariable<?>, Type> typeVariables(Type type) {
        // Fast path: a plain class with no type parameters and no parameterized supertype binds
        // nothing, so we can skip allocating (and populating) a map entirely.
        if (type instanceof Class<?> clazz && clazz.getTypeParameters().length == 0
                && !hasParameterizedSupertype(clazz)) {
            return Map.of();
        }
        Map<TypeVariable<?>, Type> variables = new HashMap<>();
        collectTypeVariables(type, variables);
        return variables.isEmpty() ? Map.of() : variables;
    }

    private static boolean hasParameterizedSupertype(Class<?> clazz) {
        Type supertype = clazz.getGenericSuperclass();
        while (supertype != null) {
            if (supertype instanceof ParameterizedType) {
                return true;
            }
            Class<?> raw = rawType(supertype);
            if (raw == null || raw == Object.class) {
                break;
            }
            supertype = raw.getGenericSuperclass();
        }
        return false;
    }

    private static void collectTypeVariables(Type type, Map<TypeVariable<?>, Type> variables) {
        Class<?> rawType;
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> raw) {
            rawType = raw;
            TypeVariable<?>[] parameters = raw.getTypeParameters();
            Type[] arguments = parameterizedType.getActualTypeArguments();
            for (int i = 0; i < parameters.length && i < arguments.length; i++) {
                // Resolve the argument against bindings gathered so far so that a child type
                // variable used as a supertype argument (e.g. class Mid<U> extends Base<U>)
                // is substituted with the concrete type.
                variables.put(parameters[i], resolve(arguments[i], variables));
            }
        } else if (type instanceof Class<?> clazz) {
            rawType = clazz;
        } else {
            return;
        }
        // Type variables declared by a parameterized superclass are bound here too, so that
        // fields inherited from a generic base (e.g. class Sub extends Base<Integer>) resolve.
        Type genericSuperclass = rawType.getGenericSuperclass();
        if (genericSuperclass != null && rawType(genericSuperclass) != Object.class) {
            collectTypeVariables(genericSuperclass, variables);
        }
    }

    static Type resolve(Type type, Map<TypeVariable<?>, Type> variables) {
        if (type instanceof TypeVariable<?> typeVariable) {
            return resolveTypeVariable(typeVariable, variables);
        }
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> rawType) {
            Type[] arguments = parameterizedType.getActualTypeArguments();
            Type[] resolved = new Type[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                resolved[i] = resolve(arguments[i], variables);
            }
            return RuntimeTypes.parameterized(rawType, parameterizedType.getOwnerType(), resolved);
        }
        if (type instanceof GenericArrayType arrayType) {
            return RuntimeTypes.array(resolve(arrayType.getGenericComponentType(), variables));
        }
        if (type instanceof WildcardType wildcardType) {
            // Substitute type variables that appear inside wildcard bounds, e.g. List<? extends T>,
            // so that the element type resolves to the concrete argument instead of the bound.
            return RuntimeTypes.wildcard(
                    resolveEach(wildcardType.getUpperBounds(), variables),
                    resolveEach(wildcardType.getLowerBounds(), variables));
        }
        return type;
    }

    private static Type resolveTypeVariable(TypeVariable<?> typeVariable, Map<TypeVariable<?>, Type> variables) {
        // Follow type-variable -> type-variable bindings (e.g. class Mid<U> extends Base<U>) until a
        // concrete type is reached. The map can hold self-referential bindings such as
        // T -> SimpleTypeVariable("T"), because processor-generated variables match keys only by
        // name; bounding the walk by the number of bindings guarantees termination (you cannot follow
        // more distinct links than there are entries without repeating one). An unresolvable variable
        // is returned as-is so callers fall back to its bound, or to the runtime value's class.
        Type current = typeVariable;
        for (int remaining = variables.size(); remaining > 0 && current instanceof TypeVariable<?> variable; remaining--) {
            Type next = lookupTypeVariable(variable, variables);
            if (next == null || next == current) {
                break;
            }
            current = next;
        }
        return current instanceof TypeVariable<?> ? current : resolve(current, variables);
    }

    private static Type[] resolveEach(Type[] types, Map<TypeVariable<?>, Type> variables) {
        Type[] resolved = new Type[types.length];
        for (int i = 0; i < types.length; i++) {
            resolved[i] = resolve(types[i], variables);
        }
        return resolved;
    }

    private static Type lookupTypeVariable(TypeVariable<?> typeVariable, Map<TypeVariable<?>, Type> variables) {
        Type direct = variables.get(typeVariable);
        if (direct != null) {
            return direct;
        }
        // Processor-generated type variables (RuntimeTypes.typeVariable) are not identity- or
        // hashCode-compatible with the JDK reflection type variables used as map keys, so fall
        // back to matching by name. Each map is built from a single declaration's type
        // parameters, where names are unique.
        String name = typeVariable.getName();
        for (Map.Entry<TypeVariable<?>, Type> entry : variables.entrySet()) {
            if (entry.getKey().getName().equals(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    static Type arrayElementType(Type type) {
        if (type instanceof Class<?> clazz && clazz.isArray()) {
            return clazz.getComponentType();
        }
        if (type instanceof GenericArrayType arrayType) {
            return arrayType.getGenericComponentType();
        }
        return Object.class;
    }

    static Type collectionElementType(Type type) {
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length == 1) {
            return parameterizedType.getActualTypeArguments()[0];
        }
        return Object.class;
    }

    static Type mapKeyType(Type type) {
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length == 2) {
            return parameterizedType.getActualTypeArguments()[0];
        }
        return String.class;
    }

    static Type mapValueType(Type type) {
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length == 2) {
            return parameterizedType.getActualTypeArguments()[1];
        }
        return Object.class;
    }
}
