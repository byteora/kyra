package org.byteora.kyra.core;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class EnumSupport {

    /**
     * Caches the resolved {@code IEnum<V>} value type per enum class. The value type is fixed for a
     * given class, so we avoid repeating the {@code getGenericInterfaces()} reflection scan on every
     * row read (ORM) or JSON value decode.
     */
    private static final ConcurrentHashMap<Class<?>, Type> VALUE_TYPE_CACHE = new ConcurrentHashMap<>();

    private EnumSupport() {
    }

    public static boolean isIEnum(Class<?> type) {
        return type != null && type.isEnum() && IEnum.class.isAssignableFrom(type);
    }

    public static Object toValue(Object enumConstant) {
        return ((IEnum<?>) enumConstant).getValue();
    }

    public static Type valueType(Class<?> enumClass) {
        return VALUE_TYPE_CACHE.computeIfAbsent(enumClass, EnumSupport::resolveValueType);
    }

    private static Type resolveValueType(Class<?> enumClass) {
        // Walk the whole interface hierarchy, not just the directly declared interfaces: an enum may
        // reach IEnum through an intermediate interface (e.g. SelectEnums<T> extends IEnum<T>, then
        // XxEnum implements SelectEnums<Integer>). Type arguments are threaded down each level so the
        // binding declared at the enum (T -> Integer) resolves IEnum's V. The superclass loop also
        // covers enum constants with bodies, whose runtime class is an anonymous Enum subclass.
        for (Class<?> current = enumClass; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Type genericInterface : current.getGenericInterfaces()) {
                Type found = findIEnumValueType(genericInterface, Map.of());
                if (found != null) {
                    return normalize(found);
                }
            }
        }
        return Object.class;
    }

    private static Type findIEnumValueType(Type type, Map<TypeVariable<?>, Type> inherited) {
        Class<?> raw;
        Type[] actualArguments;
        if (type instanceof ParameterizedType parameterizedType) {
            raw = (Class<?>) parameterizedType.getRawType();
            actualArguments = parameterizedType.getActualTypeArguments();
        } else if (type instanceof Class<?> rawClass) {
            raw = rawClass;
            actualArguments = null;
        } else {
            return null;
        }
        if (raw == IEnum.class) {
            return actualArguments != null && actualArguments.length == 1
                    ? resolveArgument(actualArguments[0], inherited)
                    : Object.class;
        }
        if (!IEnum.class.isAssignableFrom(raw)) {
            return null;
        }
        Map<TypeVariable<?>, Type> bindings = bindings(raw, actualArguments, inherited);
        for (Type superInterface : raw.getGenericInterfaces()) {
            Type found = findIEnumValueType(superInterface, bindings);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Map<TypeVariable<?>, Type> bindings(Class<?> raw, Type[] actualArguments,
                                                       Map<TypeVariable<?>, Type> inherited) {
        TypeVariable<?>[] parameters = raw.getTypeParameters();
        if (actualArguments == null || parameters.length == 0) {
            return Map.of();
        }
        Map<TypeVariable<?>, Type> bindings = new HashMap<>();
        for (int i = 0; i < parameters.length && i < actualArguments.length; i++) {
            bindings.put(parameters[i], resolveArgument(actualArguments[i], inherited));
        }
        return bindings;
    }

    private static Type resolveArgument(Type argument, Map<TypeVariable<?>, Type> inherited) {
        if (argument instanceof TypeVariable<?> variable) {
            Type bound = inherited.get(variable);
            return bound != null ? bound : argument;
        }
        return argument;
    }

    private static Type normalize(Type type) {
        if (type instanceof Class<?>) {
            return type;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return parameterizedType;
        }
        if (type instanceof TypeVariable<?> variable) {
            Type[] bounds = variable.getBounds();
            return bounds.length > 0 ? normalize(bounds[0]) : Object.class;
        }
        return Object.class;
    }

    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> E parse(Class<E> enumType, Object value) {
        E[] constants = enumType.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new IllegalArgumentException("Enum " + enumType.getName() + " has no constants");
        }
        for (E constant : constants) {
            if (Objects.equals(((IEnum<Object>) constant).getValue(), value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("No " + enumType.getSimpleName() + " constant matches value: " + value);
    }
}
