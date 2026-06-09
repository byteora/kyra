package org.byteora.kyra.orm.xml;

public enum SqlCommandType {
    SELECT,
    INSERT,
    UPDATE,
    DELETE;

    public static SqlCommandType fromElementName(String name) {
        return SqlCommandType.valueOf(name.toUpperCase());
    }
}
