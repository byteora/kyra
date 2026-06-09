package com.example.simple.support;

public final class GeneratedTypeNames {
    private GeneratedTypeNames() {
    }

    public static String reflectorTypeName(Class<?> entityType) {
        return generatedPackageName(entityType) + "." + entityType.getSimpleName() + "Reflector";
    }

    public static String tableTypeName(Class<?> entityType) {
        return generatedPackageName(entityType) + "." + entityType.getSimpleName() + "Table";
    }

    private static String generatedPackageName(Class<?> type) {
        Class<?> enclosingClass = type.getEnclosingClass();
        String packageName = "gen." + packageHash(packageName(type));
        if (enclosingClass != null) {
            packageName += "." + packageHash(enclosingClass.getCanonicalName());
        }
        return packageName;
    }

    private static String packageName(Class<?> type) {
        Package classPackage = type.getPackage();
        return classPackage == null ? "" : classPackage.getName();
    }

    private static String packageHash(String packageName) {
        long unsignedHash = Integer.toUnsignedLong(packageName.hashCode());
        if (unsignedHash == 0L) {
            return "a";
        }
        StringBuilder builder = new StringBuilder();
        while (unsignedHash > 0L) {
            int digit = (int) (unsignedHash % 26L);
            builder.append((char) ('a' + digit));
            unsignedHash /= 26L;
        }
        return builder.reverse().toString();
    }
}
