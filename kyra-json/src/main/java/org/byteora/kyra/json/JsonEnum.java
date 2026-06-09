package org.byteora.kyra.json;

/**
 * Lets an {@code enum} control its own JSON representation by mapping each constant to a custom
 * value rather than its {@link Enum#name() name}.
 *
 * <p>An enum implements this interface so that the JSON mapper serializes a constant by calling
 * {@link #getValue()} and deserializes a value by calling {@link #parse(Object)} on any constant.
 *
 * <pre>{@code
 * enum Status implements JsonEnum<Status, Integer> {
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
 * @param <V> the JSON value type the enum is encoded as
 */
public interface JsonEnum<E, V> {
    /**
     * Resolves the constant that corresponds to the given JSON value. Returning {@code null}
     * signals that no constant matches, which the mapper reports as an error.
     */
    E parse(V value);

    /**
     * Returns the JSON value this constant should be serialized as.
     */
    V getValue();
}
