package org.byteora.kyra.orm.xml;

public final class XmlParseException extends RuntimeException {
    public XmlParseException(String message) {
        super(message);
    }

    public XmlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
