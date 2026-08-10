package org.byteora.kyra.orm.processor;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GeneratedSupportIndexStore {
    private static final String PACKAGE_NAME = "gen";
    private static final String FILE_NAME = "kyra-generated.idx";
    private static final String REFLECTOR_INSTALLER_SERVICE_FILE = "META-INF/services/org.byteora.kyra.core.runtime.ReflectorInstaller";
    private static final String TABLE_INSTALLER_SERVICE_FILE = "META-INF/services/org.byteora.kyra.orm.query.TableInstaller";

    private final Filer filer;
    private final Map<String, ReflectRegistration> reflectors = new LinkedHashMap<>();
    private final Map<String, TableRegistration> tables = new LinkedHashMap<>();
    private boolean dirty;
    private String loadedLocation = "not-loaded";
    private int skippedEntries;

    GeneratedSupportIndexStore(Filer filer) {
        this.filer = filer;
    }

    void load(Validator validator) {
        reflectors.clear();
        tables.clear();
        dirty = false;
        loadedLocation = "missing";
        skippedEntries = 0;
        for (StandardLocation location : List.of(StandardLocation.CLASS_OUTPUT, StandardLocation.SOURCE_OUTPUT)) {
            try {
                FileObject fileObject = filer.getResource(location, PACKAGE_NAME, FILE_NAME);
                try (BufferedReader reader = new BufferedReader(fileObject.openReader(true))) {
                    loadedLocation = location.name();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Entry entry = parse(line);
                        if (entry == null) {
                            skippedEntries++;
                            continue;
                        }
                        if (!validator.isValid(entry.typeName(), entry.generatedTypeName())) {
                            skippedEntries++;
                            dirty = true;
                            continue;
                        }
                        if ("REFLECTOR".equals(entry.kind())) {
                            reflectors.putIfAbsent(entry.typeName(), new ReflectRegistration(entry.typeName(), entry.generatedTypeName()));
                        } else if ("TABLE".equals(entry.kind())) {
                            tables.putIfAbsent(entry.typeName(), new TableRegistration(entry.typeName(), entry.generatedTypeName()));
                        }
                    }
                    return;
                }
            } catch (IOException ignored) {
            }
        }
    }

    boolean upsertReflector(String typeName, String reflectorTypeName) {
        ReflectRegistration previous = reflectors.put(typeName, new ReflectRegistration(typeName, reflectorTypeName));
        if (previous != null && previous.reflectorTypeName().equals(reflectorTypeName)) {
            return false;
        }
        dirty = true;
        return true;
    }

    boolean upsertTable(String typeName, String tableTypeName) {
        TableRegistration previous = tables.put(typeName, new TableRegistration(typeName, tableTypeName));
        if (previous != null && previous.tableTypeName().equals(tableTypeName)) {
            return false;
        }
        dirty = true;
        return true;
    }

    List<ReflectRegistration> reflectors() {
        return new ArrayList<>(reflectors.values());
    }

    List<TableRegistration> tables() {
        return new ArrayList<>(tables.values());
    }

    boolean isDirty() {
        return dirty;
    }

    String loadedLocation() {
        return loadedLocation;
    }

    int skippedEntries() {
        return skippedEntries;
    }

    void write(List<? extends Element> originatingElements) throws IOException {
        if (!dirty) {
            return;
        }
        Element[] elements = originatingElements.stream()
                .filter(element -> element != null)
                .toArray(Element[]::new);
        FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, PACKAGE_NAME, FILE_NAME, elements);
        try (Writer writer = fileObject.openWriter()) {
            for (ReflectRegistration reflector : reflectors.values()) {
                writer.write("REFLECTOR|");
                writer.write(reflector.typeName());
                writer.write('|');
                writer.write(reflector.reflectorTypeName());
                writer.write('\n');
            }
            for (TableRegistration table : tables.values()) {
                writer.write("TABLE|");
                writer.write(table.typeName());
                writer.write('|');
                writer.write(table.tableTypeName());
                writer.write('\n');
            }
        }
        dirty = false;
    }

    void writeTableInstallerService(String installerTypeName, List<? extends Element> originatingElements) throws IOException {
        if (tables.isEmpty()) {
            return;
        }
        Element[] elements = originatingElements.stream()
                .filter(element -> element != null)
                .toArray(Element[]::new);
        FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", TABLE_INSTALLER_SERVICE_FILE, elements);
        try (Writer writer = fileObject.openWriter()) {
            writer.write(installerTypeName);
            writer.write('\n');
        }
    }

    void writeReflectorInstallerService(String installerTypeName, List<? extends Element> originatingElements) throws IOException {
        if (reflectors.isEmpty()) {
            return;
        }
        Element[] elements = originatingElements.stream()
                .filter(element -> element != null)
                .toArray(Element[]::new);
        FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", REFLECTOR_INSTALLER_SERVICE_FILE, elements);
        try (Writer writer = fileObject.openWriter()) {
            writer.write(installerTypeName);
            writer.write('\n');
        }
    }

    private Entry parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length != 3 && parts.length != 4) {
            return null;
        }
        String kind = parts[0].trim();
        String typeName = parts[1].trim();
        String generatedTypeName = parts[2].trim();
        if (kind.isEmpty() || typeName.isEmpty() || generatedTypeName.isEmpty()) {
            return null;
        }
        return new Entry(kind, typeName, generatedTypeName);
    }

    @FunctionalInterface
    interface Validator {
        boolean isValid(String typeName, String generatedTypeName);
    }

    private record Entry(String kind, String typeName, String generatedTypeName) {
    }

    record ReflectRegistration(String typeName, String reflectorTypeName) {
    }

    record TableRegistration(String typeName, String tableTypeName) {
    }
}
