package org.byteora.kyra.processor;

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

final class ReflectorIndexStore {
    private static final String PACKAGE_NAME = "gen";
    private static final String FILE_NAME = "kyra-reflectors.idx";
    private static final String SERVICE_FILE = "META-INF/services/org.byteora.kyra.core.runtime.ReflectorInstaller";

    private final Filer filer;
    private final Map<String, ReflectorRegistration> reflectors = new LinkedHashMap<>();
    private boolean dirty;

    ReflectorIndexStore(Filer filer) {
        this.filer = filer;
    }

    void load(Validator validator) {
        reflectors.clear();
        dirty = false;
        for (StandardLocation location : List.of(StandardLocation.CLASS_OUTPUT, StandardLocation.SOURCE_OUTPUT)) {
            try {
                FileObject fileObject = filer.getResource(location, PACKAGE_NAME, FILE_NAME);
                try (BufferedReader reader = new BufferedReader(fileObject.openReader(true))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        ReflectorRegistration registration = parse(line);
                        if (registration == null) {
                            continue;
                        }
                        if (!validator.isValid(registration.entityTypeName(), registration.reflectorTypeName())) {
                            dirty = true;
                            continue;
                        }
                        reflectors.putIfAbsent(registration.entityTypeName(), registration);
                    }
                    return;
                }
            } catch (IOException ignored) {
            }
        }
    }

    boolean upsertReflector(String entityTypeName, String reflectorTypeName) {
        ReflectorRegistration previous = reflectors.put(entityTypeName,
                new ReflectorRegistration(entityTypeName, reflectorTypeName));
        if (previous != null && previous.reflectorTypeName().equals(reflectorTypeName)) {
            return false;
        }
        dirty = true;
        return true;
    }

    List<ReflectorRegistration> reflectors() {
        return new ArrayList<>(reflectors.values());
    }

    void write() throws IOException {
        if (!dirty) {
            return;
        }
        FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, PACKAGE_NAME, FILE_NAME);
        try (Writer writer = fileObject.openWriter()) {
            for (ReflectorRegistration reflector : reflectors.values()) {
                writer.write("REFLECTOR|");
                writer.write(reflector.entityTypeName());
                writer.write('|');
                writer.write(reflector.reflectorTypeName());
                writer.write('\n');
            }
        }
        dirty = false;
    }

    void writeService(String installerTypeName, List<? extends Element> originatingElements) throws IOException {
        if (reflectors.isEmpty()) {
            return;
        }
        Element[] elements = originatingElements.stream().filter(element -> element != null).toArray(Element[]::new);
        FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", SERVICE_FILE, elements);
        try (Writer writer = fileObject.openWriter()) {
            writer.write(installerTypeName);
            writer.write('\n');
        }
    }

    private ReflectorRegistration parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length != 4 && parts.length != 3) {
            return null;
        }
        String kind = parts[0].trim();
        String entityTypeName = parts[1].trim();
        String reflectorTypeName = parts[2].trim();
        if (!"REFLECTOR".equals(kind) || entityTypeName.isEmpty() || reflectorTypeName.isEmpty()) {
            return null;
        }
        return new ReflectorRegistration(entityTypeName, reflectorTypeName);
    }

    @FunctionalInterface
    interface Validator {
        boolean isValid(String entityTypeName, String reflectorTypeName);
    }

    record ReflectorRegistration(String entityTypeName, String reflectorTypeName) {
    }
}
