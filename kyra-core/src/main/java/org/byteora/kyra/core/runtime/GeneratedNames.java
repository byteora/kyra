package org.byteora.kyra.core.runtime;

import java.util.List;

/**
 * Single source of truth for the fully-qualified names of classes emitted by the kyra annotation
 * processors. The compile-time generators and the runtime lookup ({@link GeneratedReflectors}) both
 * route through this class so the naming contract stays in lockstep.
 *
 * <p>Generated types live in a {@value #SUBPACKAGE} subpackage of the source type's package, e.g.
 * {@code com.example.entity.User} -> {@code com.example.entity.gen.UserReflector}. Nested source
 * types fold their enclosing simple names into the generated class name so a single subpackage stays
 * collision-free, e.g. {@code com.example.Outer.Inner} -> {@code com.example.gen.Outer_InnerReflector}.
 */
public final class GeneratedNames {
    public static final String SUBPACKAGE = "gen";

    private GeneratedNames() {
    }

    /**
     * Returns the generated subpackage for a source package: {@code "gen"} for the unnamed package,
     * otherwise {@code sourcePackage + ".gen"}.
     */
    public static String packageName(String sourcePackage) {
        return sourcePackage == null || sourcePackage.isEmpty() ? SUBPACKAGE : sourcePackage + "." + SUBPACKAGE;
    }

    /**
     * Builds the generated class simple name by prefixing each enclosing simple name (outermost
     * first) and appending the suffix, e.g. {@code (["Outer"], "Inner", "Reflector")} ->
     * {@code "Outer_InnerReflector"}.
     */
    public static String simpleName(List<String> enclosingSimpleNames, String simpleName, String suffix) {
        StringBuilder builder = new StringBuilder();
        for (String enclosing : enclosingSimpleNames) {
            builder.append(enclosing).append('_');
        }
        return builder.append(simpleName).append(suffix).toString();
    }

    public static String qualifiedName(String sourcePackage, List<String> enclosingSimpleNames,
                                       String simpleName, String suffix) {
        return packageName(sourcePackage) + "." + simpleName(enclosingSimpleNames, simpleName, suffix);
    }

    /**
     * Package for a module-level aggregate installer. Unlike per-type generated classes there is no
     * single source package, so installers live under a {@value #SUBPACKAGE} root keyed by the module
     * name (which may be a build module name such as {@code kyra-core}). The module name is flattened
     * to one legal identifier segment so hyphens and dots never produce an illegal package.
     */
    public static String installerPackageName(String moduleName) {
        return SUBPACKAGE + "." + sanitizeSegment(moduleName);
    }

    private static String sanitizeSegment(String value) {
        if (value == null || value.isEmpty()) {
            return "kyra";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean legal = builder.length() == 0
                    ? Character.isJavaIdentifierStart(ch)
                    : Character.isJavaIdentifierPart(ch);
            builder.append(legal ? ch : '_');
        }
        return builder.toString();
    }
}
