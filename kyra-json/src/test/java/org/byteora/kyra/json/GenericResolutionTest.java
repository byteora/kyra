package org.byteora.kyra.json;

import org.byteora.kyra.core.annotation.Reflect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class GenericResolutionTest {

    @Reflect
    public static class GenericBase<T> {
        public T value;
        public String label;
    }

    @Reflect
    public static final class IntBox extends GenericBase<Integer> {
    }

    @Reflect
    public static class Mid<U> extends GenericBase<U> {
    }

    @Reflect
    public static final class Leaf extends Mid<Integer> {
    }

    @Reflect
    public record Pair<A, B>(A first, B second) {
    }

    @Reflect
    public record AliasedGeneric<T>(String id, @org.byteora.kyra.core.annotation.Alias("val") T value) {
    }

    @Test
    void aliasedGenericRecordComponentRoundTrip() {
        JsonMapper mapper = JsonMapper.builder().build();
        AliasedGeneric<Integer> in = new AliasedGeneric<>("1", 123);
        String json = mapper.toJson(in);
        AliasedGeneric<Integer> out = mapper.fromJson(json, new TypeRef<AliasedGeneric<Integer>>() {
        });
        assertEquals("{\"id\":\"1\",\"val\":123}", json);
        assertInstanceOf(Integer.class, out.value());
        assertEquals(123, out.value());
    }

    @Reflect
    public static final class Holder<T> {
        public List<T> items;
    }

    @Reflect
    public static final class MapHolder<T> {
        public Map<String, T> byKey;
    }

    @Reflect
    public static final class Outer<T> {
        public Holder<T> holder;
    }

    @Test
    void inheritedTypeVariable() {
        JsonMapper mapper = JsonMapper.builder().build();
        IntBox box = mapper.fromJson("{\"value\":7,\"label\":\"x\"}", IntBox.class);
        assertInstanceOf(Integer.class, box.value);
        assertEquals(7, box.value);
        assertEquals("x", box.label);
    }

    @Test
    void transitivelyInheritedTypeVariable() {
        JsonMapper mapper = JsonMapper.builder().build();
        Leaf leaf = mapper.fromJson("{\"value\":7,\"label\":\"x\"}", Leaf.class);
        assertInstanceOf(Integer.class, leaf.value);
        assertEquals(7, leaf.value);
    }

    @Test
    void twoTypeParamsRecord() {
        JsonMapper mapper = JsonMapper.builder().build();
        Pair<Integer, String> pair = mapper.fromJson("{\"first\":7,\"second\":\"hi\"}",
                new TypeRef<Pair<Integer, String>>() {
                });
        assertInstanceOf(Integer.class, pair.first());
        assertEquals(7, pair.first());
        assertEquals("hi", pair.second());
    }

    @Test
    void listOfTypeVariable() {
        JsonMapper mapper = JsonMapper.builder().build();
        Holder<Integer> holder = mapper.fromJson("{\"items\":[1,2,3]}",
                new TypeRef<Holder<Integer>>() {
                });
        assertInstanceOf(Integer.class, holder.items.get(0));
        assertEquals(List.of(1, 2, 3), holder.items);
    }

    @Test
    void mapOfTypeVariable() {
        JsonMapper mapper = JsonMapper.builder().build();
        MapHolder<Integer> holder = mapper.fromJson("{\"byKey\":{\"a\":1}}",
                new TypeRef<MapHolder<Integer>>() {
                });
        assertInstanceOf(Integer.class, holder.byKey.get("a"));
        assertEquals(1, holder.byKey.get("a"));
    }

    @Test
    void nestedGenericTypeVariable() {
        JsonMapper mapper = JsonMapper.builder().build();
        Outer<Integer> outer = mapper.fromJson("{\"holder\":{\"items\":[1,2]}}",
                new TypeRef<Outer<Integer>>() {
                });
        assertInstanceOf(Integer.class, outer.holder.items.get(0));
        assertEquals(List.of(1, 2), outer.holder.items);
    }

    @Reflect
    public static final class UpperWildConcrete {
        public List<? extends Integer> items;
    }

    @Reflect
    public static final class WildOfVar<T> {
        public List<? extends T> items;
    }

    @Reflect
    public static final class GenArr<T> {
        public T[] arr;
    }

    @Reflect
    public static final class Bounded<T extends Number> {
        public T num;
    }

    @Test
    void upperBoundWildcardConcrete() {
        JsonMapper mapper = JsonMapper.builder().build();
        UpperWildConcrete holder = mapper.fromJson("{\"items\":[1,2]}", UpperWildConcrete.class);
        assertInstanceOf(Integer.class, holder.items.get(0));
        assertEquals(List.of(1, 2), holder.items);
    }

    @Test
    void wildcardOfTypeVariable() {
        JsonMapper mapper = JsonMapper.builder().build();
        WildOfVar<Integer> holder = mapper.fromJson("{\"items\":[1,2]}",
                new TypeRef<WildOfVar<Integer>>() {
                });
        assertInstanceOf(Integer.class, holder.items.get(0));
        assertEquals(List.of(1, 2), holder.items);
    }

    @Test
    void genericArrayTypeVariable() {
        JsonMapper mapper = JsonMapper.builder().build();
        GenArr<Integer> holder = mapper.fromJson("{\"arr\":[1,2]}",
                new TypeRef<GenArr<Integer>>() {
                });
        assertInstanceOf(Integer.class, holder.arr[0]);
        assertEquals(2, holder.arr.length);
        assertEquals(1, holder.arr[0]);
    }

    @Test
    void boundedTypeVariableResolved() {
        JsonMapper mapper = JsonMapper.builder().build();
        Bounded<Integer> holder = mapper.fromJson("{\"num\":7}",
                new TypeRef<Bounded<Integer>>() {
                });
        assertInstanceOf(Integer.class, holder.num);
        assertEquals(7, holder.num);
    }
}
