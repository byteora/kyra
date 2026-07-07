package com.example.simple.support;

import org.byteora.kyra.core.runtime.GeneratedNames;

import java.util.ArrayDeque;
import java.util.List;

public final class GeneratedTypeNames {
    private GeneratedTypeNames() {
    }

    public static String reflectorTypeName(Class<?> entityType) {
        return generatedTypeName(entityType, "Reflector");
    }

    public static String tableTypeName(Class<?> entityType) {
        return generatedTypeName(entityType, "Table");
    }

    private static String generatedTypeName(Class<?> entityType, String suffix) {
        return GeneratedNames.qualifiedName(packageName(entityType), enclosingSimpleNames(entityType),
                entityType.getSimpleName(), suffix);
    }

    private static List<String> enclosingSimpleNames(Class<?> type) {
        ArrayDeque<String> names = new ArrayDeque<>();
        for (Class<?> enclosing = type.getEnclosingClass(); enclosing != null; enclosing = enclosing.getEnclosingClass()) {
            names.addFirst(enclosing.getSimpleName());
        }
        return List.copyOf(names);
    }

    private static String packageName(Class<?> type) {
        Package classPackage = type.getPackage();
        return classPackage == null ? "" : classPackage.getName();
    }
}
