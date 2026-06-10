package org.byteora.kyra.core.runtime;

import org.byteora.kyra.core.annotation.Reflect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ReflectorRegistryTest {
    @BeforeEach
    void setUp() {
        ReflectorRegistry.clear();
    }

    @Test
    void shouldResolveRegisteredReflectors() {
        ReflectorRegistry.installAll();

        Reflector<Alpha> alphaReflector = ReflectorRegistry.get(Alpha.class);
        Reflector<Beta> betaReflector = ReflectorRegistry.get(Beta.class);

        assertNotNull(alphaReflector);
        assertNotNull(betaReflector);
        assertSame(alphaReflector, ReflectorRegistry.get(Alpha.class));
        assertSame(betaReflector, ReflectorRegistry.get(Beta.class));
    }

    @Test
    void shouldReplaceRegistryEntries() {
        ReflectorRegistry.installAll();
        Reflector<Alpha> replacementReflector = GeneratedReflectors.load(Alpha.class);
        ReflectorRegistry.register(Alpha.class, replacementReflector);

        assertSame(replacementReflector, ReflectorRegistry.get(Alpha.class));
    }

    @Test
    void shouldReturnNullWhenTypeIsUnknown() {
        ReflectorRegistry.installAll();
        assertNull(ReflectorRegistry.get(Gamma.class));
    }

    @Test
    void shouldResolveByNameClassAndIndexToSameReflector() {
        ReflectorRegistry.installAll();

        Reflector<Alpha> alphaReflector = ReflectorRegistry.get(Alpha.class);
        Reflector<Beta> betaReflector = ReflectorRegistry.get(Beta.class);
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
        ReflectorRegistry.installAll();
        Reflector<Alpha> replacement = GeneratedReflectors.load(Alpha.class);
        int index = ReflectorRegistry.indexOf(Alpha.class);
        ReflectorRegistry.register(Alpha.class, replacement);

        assertEquals(index, ReflectorRegistry.indexOf(Alpha.class));
        assertSame(replacement, ReflectorRegistry.get(index));
        assertEquals(2, ReflectorRegistry.size());
    }

    @Test
    void shouldReturnNullForUnknownNameOrIndex() {
        ReflectorRegistry.installAll();
        assertNull(ReflectorRegistry.get("com.example.Unknown"));
        assertNull(ReflectorRegistry.get(99));
        assertEquals(-1, ReflectorRegistry.indexOf(Gamma.class));
    }

    @Reflect
    public static final class Alpha {
    }

    @Reflect
    public static final class Beta {
    }

    static final class Gamma {
    }
}
