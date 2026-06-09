package org.byteora.kyra.core.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReflectorRegistryTest {
    @BeforeEach
    void setUp() {
        ReflectorRegistry.clear();
    }

    @Test
    void shouldResolveRegisteredReflectors() {
        AlphaReflector alphaReflector = new AlphaReflector();
        BetaReflector betaReflector = new BetaReflector();
        ReflectorRegistry.register(Alpha.class, alphaReflector);
        ReflectorRegistry.register(Beta.class, betaReflector);

        assertSame(alphaReflector, ReflectorRegistry.get(Alpha.class));
        assertSame(betaReflector, ReflectorRegistry.get(Beta.class));
        assertSame(alphaReflector, ReflectorRegistry.get(Alpha.class));
    }

    @Test
    void shouldReplaceRegistryEntries() {
        AlphaReflector directReflector = new AlphaReflector();
        AlphaReflector replacementReflector = new AlphaReflector();
        ReflectorRegistry.register(Alpha.class, directReflector);
        ReflectorRegistry.register(Alpha.class, replacementReflector);

        assertSame(replacementReflector, ReflectorRegistry.get(Alpha.class));
    }

    @Test
    void shouldReturnNullWhenTypeIsUnknown() {
        assertNull(ReflectorRegistry.get(Alpha.class));
    }

    @Test
    void shouldResolveByNameClassAndIndexToSameReflector() {
        AlphaReflector alphaReflector = new AlphaReflector();
        BetaReflector betaReflector = new BetaReflector();
        ReflectorRegistry.register(Alpha.class, alphaReflector);
        ReflectorRegistry.register(Beta.class, betaReflector);

        int alphaIndex = ReflectorRegistry.indexOf(Alpha.class);
        int betaIndex = ReflectorRegistry.indexOf(Beta.class);
        assertEquals(0, alphaIndex);
        assertEquals(1, betaIndex);

        assertSame(alphaReflector, ReflectorRegistry.get(Alpha.class.getName()));
        assertSame(betaReflector, ReflectorRegistry.get(Beta.class.getName()));
        assertSame(alphaReflector, ReflectorRegistry.get(alphaIndex));
        assertSame(betaReflector, ReflectorRegistry.get(betaIndex));
        assertEquals(2, ReflectorRegistry.size());
    }

    @Test
    void shouldKeepIndexStableAcrossReplacement() {
        AlphaReflector first = new AlphaReflector();
        AlphaReflector replacement = new AlphaReflector();
        ReflectorRegistry.register(Alpha.class, first);
        int index = ReflectorRegistry.indexOf(Alpha.class);
        ReflectorRegistry.register(Alpha.class, replacement);

        assertEquals(index, ReflectorRegistry.indexOf(Alpha.class));
        assertSame(replacement, ReflectorRegistry.get(index));
        assertEquals(1, ReflectorRegistry.size());
    }

    @Test
    void shouldReturnNullForUnknownNameOrIndex() {
        assertNull(ReflectorRegistry.get("com.example.Unknown"));
        assertNull(ReflectorRegistry.get(0));
        assertEquals(-1, ReflectorRegistry.indexOf(Alpha.class));
    }

    static final class Alpha {
    }

    static final class Beta {
    }

    static final class AlphaReflector implements Reflector<Alpha> {
        @Override
        public Alpha newInstance() {
            return new Alpha();
        }

        @Override
        public ClassInfo getClassInfo() {
            return null;
        }

        @Override
        public Object invoke(Alpha target, int index, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(Alpha target, int index, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(Alpha target, int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getFields() {
            return new String[0];
        }

        @Override
        public FieldInfo getField(int index) {
            return null;
        }

        @Override
        public String[] getMethods() {
            return new String[0];
        }

        @Override
        public MethodInfo[] getMethod(int index) {
            return new MethodInfo[0];
        }

        @Override
        public MethodInfo[] getMethod(String name) {
            return new MethodInfo[0];
        }
    }

    static final class BetaReflector implements Reflector<Beta> {
        @Override
        public Beta newInstance() {
            return new Beta();
        }

        @Override
        public ClassInfo getClassInfo() {
            return null;
        }

        @Override
        public Object invoke(Beta target, int index, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(Beta target, int index, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(Beta target, int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getFields() {
            return new  String[0];
        }

        @Override
        public FieldInfo getField(int index) {
            return null;
        }

        @Override
        public String[] getMethods() {
            return new String[0];
        }

        @Override
        public MethodInfo[] getMethod(int index) {
            return new MethodInfo[0];
        }

        @Override
        public MethodInfo[] getMethod(String name) {
            return new MethodInfo[0];
        }
    }
}
