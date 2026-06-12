package org.byteora.kyra.core;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
        for (Type genericInterface : enumClass.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType
                    && parameterizedType.getRawType() == IEnum.class) {
                return parameterizedType.getActualTypeArguments()[0];
            }
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
