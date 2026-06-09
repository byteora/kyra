package org.byteora.kyra.orm.dynamic;

public interface DynamicSqlNode {
    String render(DynamicSqlContext context);
}
