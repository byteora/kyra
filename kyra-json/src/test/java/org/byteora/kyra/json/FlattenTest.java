package org.byteora.kyra.json;

import org.byteora.kyra.core.annotation.Reflect;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FlattenTest {

    @Reflect(annotationMetadata = true)
    public static final class Point {
        public int x;
        public int y;
    }

    @Reflect(annotationMetadata = true)
    public static final class Envelope {
        public int code;
        @Flatten
        public Map<String, Object> ext;
        @Flatten
        public Point body;
    }

    @Reflect(annotationMetadata = true)
    public static class Msg<T> {
        public int code;
        @Flatten
        public Map<String, Object> ext;
        @Flatten
        public T payload;
    }

    @Reflect(annotationMetadata = true)
    public static final class TwoPoints {
        @Flatten(prefix = "a_")
        public Point first;
        @Flatten(prefix = "b_")
        public Point second;
    }

    @Test
    void flattensMapAndObjectIntoParent() {
        JsonMapper mapper = JsonMapper.builder().build();
        Envelope envelope = new Envelope();
        envelope.code = 1;
        envelope.ext = new LinkedHashMap<>();
        envelope.ext.put("a", 1);
        envelope.ext.put("b", 2);
        envelope.body = new Point();
        envelope.body.x = 3;
        envelope.body.y = 4;

        String json = mapper.toJson(envelope);
        assertEquals("{\"code\":1,\"a\":1,\"b\":2,\"x\":3,\"y\":4}", json);
    }

    @Test
    void readsFlattenedMapAndObject() {
        JsonMapper mapper = JsonMapper.builder().build();
        Envelope envelope = mapper.fromJson("{\"code\":1,\"a\":1,\"b\":2,\"x\":3,\"y\":4}", Envelope.class);

        assertEquals(1, envelope.code);
        assertInstanceOf(Point.class, envelope.body);
        assertEquals(3, envelope.body.x);
        assertEquals(4, envelope.body.y);
        assertEquals(2, envelope.ext.size());
        assertEquals(1L, envelope.ext.get("a"));
        assertEquals(2L, envelope.ext.get("b"));
    }

    @Test
    void flattensGenericPayloadRoundTrip() {
        JsonMapper mapper = JsonMapper.builder().build();
        Msg<Point> msg = new Msg<>();
        msg.code = 1;
        msg.ext = new LinkedHashMap<>();
        msg.ext.put("a", 1);
        msg.payload = new Point();
        msg.payload.x = 2;
        msg.payload.y = 3;

        String json = mapper.toJson(msg);
        assertEquals("{\"code\":1,\"a\":1,\"x\":2,\"y\":3}", json);

        Msg<Point> decoded = mapper.fromJson(json, new TypeRef<Msg<Point>>() {
        });
        assertEquals(1, decoded.code);
        assertInstanceOf(Point.class, decoded.payload);
        assertEquals(2, decoded.payload.x);
        assertEquals(3, decoded.payload.y);
        assertEquals(1L, decoded.ext.get("a"));
    }

    @Test
    void prefixDisambiguatesCollidingFields() {
        JsonMapper mapper = JsonMapper.builder().build();
        TwoPoints points = new TwoPoints();
        points.first = new Point();
        points.first.x = 1;
        points.first.y = 2;
        points.second = new Point();
        points.second.x = 3;
        points.second.y = 4;

        String json = mapper.toJson(points);
        assertEquals("{\"a_x\":1,\"a_y\":2,\"b_x\":3,\"b_y\":4}", json);

        TwoPoints decoded = mapper.fromJson(json, TwoPoints.class);
        assertEquals(1, decoded.first.x);
        assertEquals(2, decoded.first.y);
        assertEquals(3, decoded.second.x);
        assertEquals(4, decoded.second.y);
    }

    @Test
    void nullFlattenedFieldsAreOmitted() {
        JsonMapper mapper = JsonMapper.builder().build();
        Envelope envelope = new Envelope();
        envelope.code = 5;

        String json = mapper.toJson(envelope);
        assertEquals("{\"code\":5}", json);

        Envelope decoded = mapper.fromJson(json, Envelope.class);
        assertEquals(5, decoded.code);
        assertNull(decoded.body);
        assertEquals(0, decoded.ext.size());
    }
}
