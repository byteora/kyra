package org.byteora.kyra.orm.runtime;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TypeConverterObjectFallbackTest {
    @Test
    void shouldUseUntypedJdbcAccessWhenResultTypeIsObject() throws Exception {
        AtomicBoolean typedAccess = new AtomicBoolean();
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getObject") && args.length == 1) {
                        return 42L;
                    }
                    if (method.getName().equals("getObject") && args.length == 2) {
                        typedAccess.set(true);
                        throw new AssertionError("Object fallback must not call ResultSet.getObject(index, Object.class)");
                    }
                    throw new UnsupportedOperationException(method.toString());
                }
        );

        Object value = new TypeConverter().cast(resultSet, 1, Object.class, "value", "value");

        assertEquals(42L, value);
        assertFalse(typedAccess.get());
    }
}
