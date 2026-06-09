package org.byteora.kyra.core;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class EnumSupport {
    private EnumSupport() {
    }

    public static boolean isIEnum(Class<?> type) {
        return type != null && type.isEnum() && IEnum.class.isAssignableFrom(type);
    }

    public static Object toValue(Object enumConstant) {
        return ((IEnum<?, ?>) enumConstant).getValue();
    }

    public static Type valueType(Class<?> enumClass) {
        for (Type genericInterface : enumClass.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType
                    && parameterizedType.getRawType() == IEnum.class) {
                return parameterizedType.getActualTypeArguments()[1];
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
        E result = ((IEnum<E, Object>) constants[0]).parse(value);
        if (result == null) {
            throw new IllegalArgumentException("No " + enumType.getSimpleName() + " constant matches value: " + value);
        }
        return result;
    }
}
