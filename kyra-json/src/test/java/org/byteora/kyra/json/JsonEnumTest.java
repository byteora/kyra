package org.byteora.kyra.json;

import org.byteora.kyra.core.annotation.Reflect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JsonEnumTest {

    public enum Status implements JsonEnum<Status, Integer> {
        ON(1), OFF(0);

        private final int code;

        Status(int code) {
            this.code = code;
        }

        @Override
        public Integer getValue() {
            return code;
        }

        @Override
        public Status parse(Integer value) {
            for (Status status : values()) {
                if (status.code == value) {
                    return status;
                }
            }
            return null;
        }
    }

    public enum Level implements JsonEnum<Level, String> {
        LOW("low"), HIGH("high");

        private final String tag;

        Level(String tag) {
            this.tag = tag;
        }

        @Override
        public String getValue() {
            return tag;
        }

        @Override
        public Level parse(String value) {
            for (Level level : values()) {
                if (level.tag.equals(value)) {
                    return level;
                }
            }
            return null;
        }
    }

    @Reflect
    public static final class Holder {
        public Status status;
        public Level level;
    }

    @Test
    void serializesEnumUsingGetValue() {
        JsonMapper mapper = JsonMapper.builder().build();
        assertEquals("1", mapper.toJson(Status.ON));
        assertEquals("0", mapper.toJson(Status.OFF));
        assertEquals("\"high\"", mapper.toJson(Level.HIGH));
    }

    @Test
    void deserializesEnumUsingParse() {
        JsonMapper mapper = JsonMapper.builder().build();
        assertSame(Status.ON, mapper.fromJson("1", Status.class));
        assertSame(Status.OFF, mapper.fromJson("0", Status.class));
        assertSame(Level.LOW, mapper.fromJson("\"low\"", Level.class));
    }

    @Test
    void roundTripsEnumFields() {
        JsonMapper mapper = JsonMapper.builder().build();
        Holder holder = new Holder();
        holder.status = Status.ON;
        holder.level = Level.HIGH;

        String json = mapper.toJson(holder);
        assertEquals("{\"status\":1,\"level\":\"high\"}", json);

        Holder decoded = mapper.fromJson(json, Holder.class);
        assertSame(Status.ON, decoded.status);
        assertSame(Level.HIGH, decoded.level);
    }

    @Test
    void throwsWhenNoConstantMatches() {
        JsonMapper mapper = JsonMapper.builder().build();
        assertThrows(JsonException.class, () -> mapper.fromJson("9", Status.class));
    }
}
