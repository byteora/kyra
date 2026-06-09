package org.byteora.kyra.core;

/**
 * Lets an {@code enum} control how it is encoded to and decoded from external values such as JSON
 * or database columns, instead of relying on {@link Enum#name()} or {@link Enum#ordinal()}.
 *
 * <p>An enum implements this interface so that serializers call {@link #getValue()} and
 * deserializers call {@link #parse(Object)} on any constant.
 *
 * <pre>{@code
 * enum Status implements IEnum<Status, Integer> {
 *     ON(1), OFF(0);
 *     private final int code;
 *     Status(int code) { this.code = code; }
 *     public Integer getValue() { return code; }
 *     public Status parse(Integer value) {
 *         for (Status s : values()) if (s.code == value) return s;
 *         return null;
 *     }
 * }
 * }</pre>
 *
 * @param <E> the enum type itself
 * @param <V> the encoded value type
 */
public interface IEnum<E, V> {
    /**
     * Resolves the constant that corresponds to the given encoded value. Returning {@code null}
     * signals that no constant matches.
     */
    E parse(V value);

    /**
     * Returns the encoded value this constant should be written as.
     */
    V getValue();
}
