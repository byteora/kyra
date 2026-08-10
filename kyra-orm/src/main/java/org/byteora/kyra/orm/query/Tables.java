package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.runtime.SqlExecutorException;

import java.util.Arrays;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Registry of {@link Table} instances keyed by Java type.
 *
 * <p>Mirrors {@code ReflectorRegistry}: reflectors/tables are installed eagerly
 * via {@link TableInstaller} services on the first registry access (or an
 * explicit {@link #installAll()}). Storage is an append-only, read-mostly
 * structure that keeps tables once in an {@code index -> Table} array with
 * a compact open-addressing {@code Class -> index} table, avoiding the per-entry
 * node allocation of a {@link java.util.Map}. The snapshot is immutable and
 * published through a {@code volatile} field so reads are lock-free.
 */
public final class Tables {
    private static volatile TableInstaller[] installers;
    private static volatile Snapshot snapshot = Snapshot.EMPTY;
    private static volatile boolean installedAll;

    private Tables() {
    }

    public static synchronized <T> void register(Class<T> type, Table<? extends T> table) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(table, "table");
        Snapshot current = snapshot;
        int existing = current.indexOfClass(type);
        if (existing >= 0) {
            Table<?>[] tables = current.tables.clone();
            tables[existing] = table;
            snapshot = new Snapshot(current.types, tables, current.classKeys, current.classSlots, current.size);
            return;
        }
        int newSize = current.size + 1;
        Class<?>[] types = Arrays.copyOf(current.types, newSize);
        Table<?>[] tables = Arrays.copyOf(current.tables, newSize);
        types[current.size] = type;
        tables[current.size] = table;

        int capacity = capacityFor(newSize);
        Class<?>[] classKeys = new Class<?>[capacity];
        int[] classSlots = newSlots(capacity);
        for (int i = 0; i < newSize; i++) {
            insertClass(classKeys, classSlots, types[i], i);
        }
        snapshot = new Snapshot(types, tables, classKeys, classSlots, newSize);
    }

    @SuppressWarnings("unchecked")
    public static <T> Table<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        ensureInstalledAll();
        Snapshot current = snapshot;
        int index = current.indexOfClass(type);
        if (index < 0) {
            throw new SqlExecutorException("No Table registered for type: " + type.getName());
        }
        return (Table<T>) current.tables[index];
    }

    public static int indexOf(Class<?> type) {
        Objects.requireNonNull(type, "type");
        ensureInstalledAll();
        return snapshot.indexOfClass(type);
    }

    public static int size() {
        ensureInstalledAll();
        return snapshot.size;
    }

    public static synchronized void clear() {
        snapshot = Snapshot.EMPTY;
        installedAll = false;
    }

    public static void installAll() {
        for (TableInstaller installer : installers()) {
            installer.install();
        }
        installedAll = true;
    }

    private static void ensureInstalledAll() {
        if (installedAll) {
            return;
        }
        synchronized (Tables.class) {
            if (!installedAll) {
                installAll();
            }
        }
    }

    private static TableInstaller[] installers() {
        TableInstaller[] current = installers;
        if (current != null) {
            return current;
        }
        synchronized (Tables.class) {
            current = installers;
            if (current == null) {
                current = ServiceLoader.load(TableInstaller.class)
                        .stream()
                        .map(ServiceLoader.Provider::get)
                        .toArray(TableInstaller[]::new);
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

    private static int spread(int hash) {
        return (hash ^ (hash >>> 16)) & 0x7fffffff;
    }

    private static final class Snapshot {
        static final Snapshot EMPTY = new Snapshot(new Class<?>[0], new Table<?>[0], new Class<?>[0], new int[0], 0);

        final Class<?>[] types;
        final Table<?>[] tables;
        final Class<?>[] classKeys;
        final int[] classSlots;
        final int size;

        Snapshot(Class<?>[] types, Table<?>[] tables, Class<?>[] classKeys, int[] classSlots, int size) {
            this.types = types;
            this.tables = tables;
            this.classKeys = classKeys;
            this.classSlots = classSlots;
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
    }
}
