package org.byteora.kyra.orm.xml;

import org.byteora.kyra.orm.dynamic.DynamicSqlNode;

public record SqlNodeDefinition(String id, SqlCommandType commandType, String resultType, String parameterType,
                                DynamicSqlNode rootSqlNode) {
}
