package org.byteora.kyra.core.runtime;

import java.util.Arrays;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Registry of {@link Reflector} instances keyed by entity type.
 *
 * <p>Storage is an append-only, read-mostly structure. Instead of one or more
 * {@link java.util.Map}s (which allocate a node per entry and box integer
 * indices), reflectors are stored once in an {@code index -> Reflector} array
 * and two compact open-addressing tables map {@code Class -> index} and
 * {@code String (fully-qualified name) -> index} into it. All three lookup
 * entry points ({@link #get(Class)}, {@link #get(String)}, {@link #get(int)})
 * resolve to the same {@code Reflector} instance.
 *
 * <p>All reflectors are installed eagerly via {@link ReflectorInstaller}
 * services on the first registry access (or an explicit {@link #installAll()}).
 * Writes happen during that installation and are serialized; an immutable
 * {@code Table} snapshot is published through a {@code volatile} field so reads
 * are lock-free and safe.
 */
public final class ReflectorRegistry {
    private static volatile ReflectorInstaller[] installers;
    private static volatile Table table = Table.EMPTY;
    private static volatile boolean installedAll;

    private ReflectorRegistry() {
    }

    public static synchronized <T> void register(Class<T> type, Reflector<? extends T> reflector) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reflector, "reflector");
        Table current = table;
        int existing = current.indexOfClass(type);
        if (existing >= 0) {
            Reflector<?>[] reflectors = current.reflectors.clone();
            reflectors[existing] = reflector;
            table = new Table(current.types, reflectors,
                    current.classKeys, current.classSlots,
                    current.nameKeys, current.nameSlots, current.size);
            return;
        }
        int newSize = current.size + 1;
        Class<?>[] types = Arrays.copyOf(current.types, newSize);
        Reflector<?>[] reflectors = Arrays.copyOf(current.reflectors, newSize);
        types[current.size] = type;
        reflectors[current.size] = reflector;

        int capacity = capacityFor(newSize);
        Class<?>[] classKeys = new Class<?>[capacity];
        int[] classSlots = newSlots(capacity);
        String[] nameKeys = new String[capacity];
        int[] nameSlots = newSlots(capacity);
        for (int i = 0; i < newSize; i++) {
            insertClass(classKeys, classSlots, types[i], i);
            insertName(nameKeys, nameSlots, types[i].getName(), i);
        }
        table = new Table(types, reflectors, classKeys, classSlots, nameKeys, nameSlots, newSize);
    }

    public static synchronized void clear() {
        table = Table.EMPTY;
        installedAll = false;
    }

    @SuppressWarnings("unchecked")
    public static <T> Reflector<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        ensureInstalledAll();
        Table current = table;
        int index = current.indexOfClass(type);
        return index >= 0 ? (Reflector<T>) current.reflectors[index] : null;
    }

    public static Reflector<?> get(String className) {
        Objects.requireNonNull(className, "className");
        ensureInstalledAll();
        Table current = table;
        int index = current.indexOfName(className);
        return index >= 0 ? current.reflectors[index] : null;
    }

    public static Reflector<?> get(int index) {
        ensureInstalledAll();
        Table current = table;
        if (index < 0 || index >= current.size) {
            return null;
        }
        return current.reflectors[index];
    }

    public static int indexOf(Class<?> type) {
        Objects.requireNonNull(type, "type");
        ensureInstalledAll();
        return table.indexOfClass(type);
    }

    public static int size() {
        ensureInstalledAll();
        return table.size;
    }

    public static void installAll() {
        for (ReflectorInstaller installer : installers()) {
            installer.install();
        }
        installedAll = true;
    }

    private static void ensureInstalledAll() {
        if (installedAll) {
            return;
        }
        synchronized (ReflectorRegistry.class) {
            if (!installedAll) {
                installAll();
            }
        }
    }

    private static ReflectorInstaller[] installers() {
        ReflectorInstaller[] current = installers;
        if (current != null) {
            return current;
        }
        synchronized (ReflectorRegistry.class) {
            current = installers;
            if (current == null) {
                current = ServiceLoader.load(ReflectorInstaller.class)
                        .stream()
                        .map(ServiceLoader.Provider::get)
                        .toArray(ReflectorInstaller[]::new);
                installers = current;
            }
            return current;
        }
    }

    private static int capacityFor(int size) {
        int target = Math.max(2, (int) (size / 0.6f) + 1);
        int capacity = Integer.highestOneBit(target - 1) << 1;
        return Math.max(capacity, 2);
    }

    private static int[] newSlots(int capacity) {
        int[] slots = new int[capacity];
        Arrays.fill(slots, -1);
        return slots;
    }

    private static void insertClass(Class<?>[] keys, int[] slots, Class<?> type, int index) {
        int mask = keys.length - 1;
        int i = spread(System.identityHashCode(type)) & mask;
        while (keys[i] != null) {
            i = (i + 1) & mask;
        }
        keys[i] = type;
        slots[i] = index;
    }

    private static void insertName(String[] keys, int[] slots, String name, int index) {
        int mask = keys.length - 1;
        int i = spread(name.hashCode()) & mask;
        while (keys[i] != null) {
            i = (i + 1) & mask;
        }
        keys[i] = name;
        slots[i] = index;
    }

    private static int spread(int hash) {
        return (hash ^ (hash >>> 16)) & 0x7fffffff;
    }

    private static final class Table {
        static final Table EMPTY = new Table(
                new Class<?>[0], new Reflector<?>[0],
                new Class<?>[0], new int[0],
                new String[0], new int[0], 0);

        final Class<?>[] types;
        final Reflector<?>[] reflectors;
        final Class<?>[] classKeys;
        final int[] classSlots;
        final String[] nameKeys;
        final int[] nameSlots;
        final int size;

        Table(Class<?>[] types, Reflector<?>[] reflectors,
              Class<?>[] classKeys, int[] classSlots,
              String[] nameKeys, int[] nameSlots, int size) {
            this.types = types;
            this.reflectors = reflectors;
            this.classKeys = classKeys;
            this.classSlots = classSlots;
            this.nameKeys = nameKeys;
            this.nameSlots = nameSlots;
            this.size = size;
        }

        int indexOfClass(Class<?> type) {
            int length = classKeys.length;
            if (length == 0) {
                return -1;
            }
            int mask = length - 1;
            int i = spread(System.identityHashCode(type)) & mask;
            while (true) {
                Class<?> key = classKeys[i];
                if (key == null) {
                    return -1;
                }
                if (key == type) {
                    return classSlots[i];
                }
                i = (i + 1) & mask;
            }
        }

        int indexOfName(String name) {
            int length = nameKeys.length;
            if (length == 0) {
                return -1;
            }
            int mask = length - 1;
            int i = spread(name.hashCode()) & mask;
            while (true) {
                String key = nameKeys[i];
                if (key == null) {
                    return -1;
                }
                if (key.equals(name)) {
                    return nameSlots[i];
                }
                i = (i + 1) & mask;
            }
        }
    }
}
