package org.byteora.kyra.core;

import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumSupportTest {

    enum Direct implements IEnum<Integer> {
        ON(1), OFF(0);

        private final int code;

        Direct(int code) {
            this.code = code;
        }

        @Override
        public Integer getValue() {
            return code;
        }
    }

    interface SelectEnums<T extends Serializable> extends IEnum<T> {
    }

    enum Indirect implements SelectEnums<Integer> {
        A(10), B(20);

        private final int code;

        Indirect(int code) {
            this.code = code;
        }

        @Override
        public Integer getValue() {
            return code;
        }
    }

    interface MidEnums<Y extends Serializable> extends SelectEnums<Y> {
    }

    enum Deep implements MidEnums<String> {
        X("x"), Y("y");

        private final String code;

        Deep(String code) {
            this.code = code;
        }

        @Override
        public String getValue() {
            return code;
        }
    }

    @Test
    void resolvesValueTypeForDirectIEnum() {
        assertEquals(Integer.class, EnumSupport.valueType(Direct.class));
    }

    @Test
    void resolvesValueTypeThroughIntermediateInterface() {
        assertEquals(Integer.class, EnumSupport.valueType(Indirect.class));
    }

    @Test
    void resolvesValueTypeThroughMultipleLevels() {
        assertEquals(String.class, EnumSupport.valueType(Deep.class));
    }

    @Test
    void parseMatchesByValueForIndirectEnum() {
        assertEquals(Indirect.B, EnumSupport.parse(Indirect.class, 20));
        assertEquals(Deep.X, EnumSupport.parse(Deep.class, "x"));
    }

    @Test
    void toValueReadsEncodedValue() {
        assertEquals(1, EnumSupport.toValue(Direct.ON));
        assertEquals("y", EnumSupport.toValue(Deep.Y));
    }
}
