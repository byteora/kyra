package org.byteora.kyra.orm.query;

public final class Wrapper {
    private Wrapper() {
    }

    public static WhereWrapper where() {
        return new WhereWrapper();
    }

    public static UpdateWrapper update() {
        return new UpdateWrapper();
    }
}
