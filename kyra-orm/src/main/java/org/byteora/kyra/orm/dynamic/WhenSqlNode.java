package org.byteora.kyra.orm.dynamic;

public final class WhenSqlNode {
    private final String test;
    private final DynamicSqlNode contents;

    public WhenSqlNode(String test, DynamicSqlNode contents) {
        this.test = test;
        this.contents = contents;
    }

    public String getTest() {
        return test;
    }

    public DynamicSqlNode getContents() {
        return contents;
    }
}
