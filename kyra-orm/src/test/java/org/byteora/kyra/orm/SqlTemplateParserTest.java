package org.byteora.kyra.orm;

import org.byteora.kyra.orm.runtime.BoundSql;
import org.byteora.kyra.orm.xml.SqlTemplateParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlTemplateParserTest {
    @Test
    void replacesHashPlaceholdersWithJdbcPlaceholders() {
        BoundSql boundSql = SqlTemplateParser.parse("select * from user where id = #{id} and user_name = #{user.userName}");

        assertEquals("select * from user where id = ? and user_name = ?", boundSql.getSql());
        assertEquals(java.util.List.of("id", "user.userName"), boundSql.getBindings());
    }
}
