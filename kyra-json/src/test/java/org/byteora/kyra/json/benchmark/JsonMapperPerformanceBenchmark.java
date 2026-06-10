package org.byteora.kyra.json.benchmark;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.json.TypeRef;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JsonMapperPerformanceBenchmark {
    static final int LIST_SIZE = 1000;

    @Benchmark
    public String kyraSerializeUser(BenchmarkState state) {
        return state.kyraMapper.toJson(state.user);
    }

    @Benchmark
    public String jacksonSerializeUser(BenchmarkState state) {
        try {
            return state.jacksonMapper.writeValueAsString(state.user);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Benchmark
    public BenchmarkUser kyraDeserializeUser(BenchmarkState state) {
        return state.kyraMapper.fromJson(state.userJson, BenchmarkUser.class);
    }

    @Benchmark
    public BenchmarkUser jacksonDeserializeUser(BenchmarkState state) {
        try {
            return state.jacksonMapper.readValue(state.userJson, BenchmarkUser.class);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Benchmark
    public String kyraSerializeUserList(BenchmarkState state) {
        return state.kyraMapper.toJson(state.users);
    }

    @Benchmark
    public String jacksonSerializeUserList(BenchmarkState state) {
        try {
            return state.jacksonMapper.writeValueAsString(state.users);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Benchmark
    public List<BenchmarkUser> kyraDeserializeUserList(BenchmarkState state) {
        return state.kyraMapper.fromJson(state.userListJson, new TypeRef<List<BenchmarkUser>>() {
        });
    }

    @Benchmark
    public List<BenchmarkUser> jacksonDeserializeUserList(BenchmarkState state) {
        try {
            return state.jacksonMapper.readValue(state.userListJson, new TypeReference<List<BenchmarkUser>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Benchmark
    public BenchmarkUser kyraDeserializeUserBytes(BenchmarkState state) {
        return state.kyraMapper.fromBytes(state.userJsonBytes, BenchmarkUser.class);
    }

    @Benchmark
    public BenchmarkUser jacksonDeserializeUserBytes(BenchmarkState state) {
        try {
            return state.jacksonMapper.readValue(state.userJsonBytes, BenchmarkUser.class);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Benchmark
    public byte[] kyraSerializeUserBytes(BenchmarkState state) {
        return state.kyraMapper.toBytes(state.user);
    }

    @Benchmark
    public byte[] jacksonSerializeUserBytes(BenchmarkState state) {
        try {
            return state.jacksonMapper.writeValueAsBytes(state.user);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        org.byteora.kyra.json.JsonMapper kyraMapper;
        JsonMapper jacksonMapper;
        BenchmarkUser user;
        List<BenchmarkUser> users;
        String userJson;
        String userListJson;
        byte[] userJsonBytes;

        @Setup(Level.Trial)
        public void setUp() {
            kyraMapper = org.byteora.kyra.json.JsonMapper.builder().build();
            jacksonMapper = JsonMapper.builder()
                    .changeDefaultVisibility(vc -> vc
                            .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                            .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                            .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                            .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                            .withCreatorVisibility(JsonAutoDetect.Visibility.NONE))
                    .build();

            user = sampleUser(1);
            users = new ArrayList<>(LIST_SIZE);
            for (int i = 0; i < LIST_SIZE; i++) {
                users.add(sampleUser(i));
            }
            userJson = kyraMapper.toJson(user);
            userListJson = kyraMapper.toJson(users);
            userJsonBytes = userJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        private static BenchmarkUser sampleUser(int index) {
            BenchmarkUser user = new BenchmarkUser();
            user.id = UUID.fromString("00000000-0000-0000-0000-%012d".formatted(index));
            user.name = "user-" + index;
            user.age = 20 + (index % 50);
            user.role = index % 2 == 0 ? Role.ADMIN : Role.USER;
            user.score = index * 1.5;
            return user;
        }
    }

    public enum Role {
        USER,
        ADMIN
    }

    @Reflect
    public static final class BenchmarkUser {
        public UUID id;
        public String name;
        public int age;
        public Role role;
        public double score;
    }
}
