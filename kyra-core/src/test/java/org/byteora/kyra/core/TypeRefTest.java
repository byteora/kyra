package org.byteora.kyra.core;

import org.byteora.kyra.core.runtime.RuntimeTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeRefTest {
    @Test
    void shouldCaptureNestedGenericType() {
        TypeRef<List<Map<String, Integer>>> typeRef = new TypeRef<>() {
        };

        ParameterizedType listType = (ParameterizedType) typeRef.type();
        ParameterizedType mapType = (ParameterizedType) listType.getActualTypeArguments()[0];

        assertEquals(List.class, RuntimeTypes.rawClass(listType));
        assertEquals(Map.class, mapType.getRawType());
        assertEquals(String.class, mapType.getActualTypeArguments()[0]);
        assertEquals(Integer.class, mapType.getActualTypeArguments()[1]);
    }
}
