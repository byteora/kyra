package org.byteora.kyra.processor;

import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.runtime.GeneratedNames;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes("org.byteora.kyra.core.annotation.Reflect")
@SupportedOptions("kyra.module")
public class KyraProcessor extends AbstractProcessor {
    private Filer filer;
    private Messager messager;
    private Elements elements;
    private Types types;
    private ReflectorClassGenerator reflectorClassGenerator;
    private ReflectorIndexStore reflectorIndexStore;

    private final Map<String, ReflectSpec> reflectSpecs = new LinkedHashMap<>();
    private final Set<String> generatedReflectors = new LinkedHashSet<>();
    private final Map<String, List<VariableElement>> instanceFieldsCache = new LinkedHashMap<>();
    private boolean persistedReflectorIndexLoaded;
    private boolean installerGenerated;
    private String moduleNameOption;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.moduleNameOption = normalizeModuleName(processingEnv.getOptions().get("kyra.module"));
        this.reflectorIndexStore = new ReflectorIndexStore(filer);
        this.reflectorClassGenerator = new ReflectorClassGenerator(new ReflectorClassGenerator.Context() {
            @Override
            public Types types() {
                return KyraProcessor.this.types;
            }

            @Override
            public TypeElement objectTypeElement() {
                return elements.getTypeElement(Object.class.getCanonicalName());
            }

            @Override
            public TypeMirror voidType() {
                return KyraProcessor.this.types.getNoType(TypeKind.VOID);
            }

            @Override
            public ReflectSpec reflectSpec(TypeElement entityType) {
                return reflectSpecs.get(entityType.getQualifiedName().toString());
            }

            @Override
            public List<VariableElement> collectInstanceFields(TypeElement entityType) {
                return KyraProcessor.this.collectInstanceFields(entityType);
            }

            @Override
            public List<ExecutableElement> collectInvokableMethods(TypeElement entityType) {
                return KyraProcessor.this.collectInvokableMethods(entityType);
            }

            @Override
            public List<String> expandedFieldNames(TypeElement entityType) {
                return KyraProcessor.this.expandedFieldNames(entityType);
            }

            @Override
            public boolean hasReflectSpec(TypeElement typeElement) {
                return reflectSpecs.containsKey(typeElement.getQualifiedName().toString());
            }

            @Override
            public TypeElement directSuperType(TypeElement entityType) {
                return KyraProcessor.this.directSuperType(entityType);
            }

            @Override
            public boolean isJavaLangObject(TypeElement typeElement) {
                return KyraProcessor.this.isJavaLangObject(typeElement);
            }

            @Override
            public String fieldAlias(VariableElement field) {
                return AliasSupport.aliasValue(field);
            }

            @Override
            public String capitalize(String value) {
                return KyraProcessor.this.capitalize(value);
            }
        });
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        loadPersistedReflectorIndexIfNeeded();
        collectReflectSpecs(roundEnv);
        generateReflectors();
        if (!roundEnv.processingOver() && !installerGenerated) {
            generateInstallerAndService();
        }
        if (roundEnv.processingOver()) {
            persistReflectorIndexIfNeeded();
        }
        return false;
    }

    private void collectReflectSpecs(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Reflect.class)) {
            if (!isReflectTarget(element)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Reflect can only be used on classes, records, or enums", element);
                continue;
            }
            TypeElement typeElement = (TypeElement) element;
            Reflect reflect = typeElement.getAnnotation(Reflect.class);
            reflectSpecs.put(typeElement.getQualifiedName().toString(),
                    new ReflectSpec(typeElement, reflect.suffix(), reflect.metadata(), reflect.annotationMetadata()));
        }
    }

    private boolean isReflectTarget(Element element) {
        ElementKind kind = element.getKind();
        return kind == ElementKind.CLASS || kind == ElementKind.RECORD || kind == ElementKind.ENUM;
    }

    private void generateReflectors() {
        for (ReflectSpec spec : reflectSpecs.values()) {
            String typeName = spec.typeElement().getQualifiedName().toString();
            if (!generatedReflectors.add(typeName)) {
                continue;
            }
            try {
                writeReflectorClass(spec.typeElement(), spec.suffix());
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate reflector: " + ex.getMessage(), spec.typeElement());
            }
        }
    }

    private void writeReflectorClass(TypeElement entityType, String suffix) throws IOException {
        String qualifiedName = reflectorTypeName(entityType, suffix);
        JavaFileObject fileObject = filer.createClassFile(qualifiedName, entityType);
        try (OutputStream outputStream = fileObject.openOutputStream()) {
            outputStream.write(reflectorClassGenerator.buildReflectorClass(qualifiedName, entityType));
        }
        reflectorIndexStore.upsertReflector(entityType.getQualifiedName().toString(), qualifiedName);
    }

    private void generateInstallerAndService() {
        if (reflectorIndexStore.reflectors().isEmpty()) {
            return;
        }
        String installerTypeName = aggregateReflectorInstallerTypeName();
        try {
            writeReflectorInstaller(installerTypeName);
            reflectorIndexStore.writeService(installerTypeName, reflectSpecs.values().stream().map(ReflectSpec::typeElement).toList());
            installerGenerated = true;
        } catch (IOException ex) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate reflector installer: " + ex.getMessage());
        }
    }

    private void writeReflectorInstaller(String installerTypeName) throws IOException {
        JavaFileObject fileObject = filer.createSourceFile(installerTypeName,
                reflectSpecs.values().stream().map(ReflectSpec::typeElement).toArray(Element[]::new));
        try (Writer writer = fileObject.openWriter()) {
            writer.write(buildInstallerSource(installerTypeName, reflectorIndexStore.reflectors()));
        }
    }

    private String buildInstallerSource(String installerTypeName, List<ReflectorIndexStore.ReflectorRegistration> registrations) {
        int separator = installerTypeName.lastIndexOf('.');
        String packageName = separator < 0 ? "" : installerTypeName.substring(0, separator);
        String simpleName = separator < 0 ? installerTypeName : installerTypeName.substring(separator + 1);
        String packageBlock = packageName.isEmpty() ? "" : "package %s;%n%n".formatted(packageName);
        StringBuilder installAll = new StringBuilder();
        for (ReflectorIndexStore.ReflectorRegistration registration : registrations) {
            installAll.append(buildReflectorRegistration(registration.entityTypeName(), registration.reflectorTypeName()));
        }
        return new StringBuilder()
                .append(packageBlock)
                .append("@SuppressWarnings({\"rawtypes\", \"unchecked\"})\n")
                .append("public final class ").append(simpleName)
                .append(" implements org.byteora.kyra.core.runtime.ReflectorInstaller {\n")
                .append("    @Override\n")
                .append("    public void install() {\n")
                .append(installAll)
                .append("    }\n")
                .append("}\n")
                .toString();
    }

    private String buildReflectorRegistration(String entityTypeName, String reflectorTypeName) {
        return "            org.byteora.kyra.core.runtime.ReflectorRegistry.register(%s.class, new %s());%n"
                .formatted(entityTypeName, reflectorTypeName);
    }

    private String aggregateReflectorInstallerTypeName() {
        String moduleName = moduleNameOption;
        if (moduleName == null) {
            moduleName = reflectorIndexStore.reflectors().stream()
                    .map(registration -> packageNameOf(registration.entityTypeName()))
                    .filter(packageName -> !packageName.isBlank())
                    .findFirst()
                    .orElse("kyra");
        }
        return GeneratedNames.installerPackageName(moduleName) + "." + toPascalCase(moduleName) + "ReflectorInstaller";
    }

    private String normalizeModuleName(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) {
            return null;
        }
        return moduleName.trim();
    }

    private String toPascalCase(String value) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(ch));
                    capitalizeNext = false;
                } else {
                    result.append(ch);
                }
            } else {
                capitalizeNext = true;
            }
        }
        if (result.isEmpty() || !Character.isJavaIdentifierStart(result.charAt(0))) {
            result.insert(0, "Kyra");
        }
        return result.toString();
    }

    private List<String> expandedFieldNames(TypeElement entityType) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (VariableElement field : collectInstanceFields(entityType)) {
            names.add(field.getSimpleName().toString());
        }
        TypeElement superType = directSuperType(entityType);
        if (superType != null && !isJavaLangObject(superType) && reflectSpecs.containsKey(superType.getQualifiedName().toString())) {
            names.addAll(expandedFieldNames(superType));
        }
        return new ArrayList<>(names);
    }

    private List<VariableElement> collectInstanceFields(TypeElement entityType) {
        String qualifiedName = entityType.getQualifiedName().toString();
        List<VariableElement> cached = instanceFieldsCache.get(qualifiedName);
        if (cached != null) {
            return cached;
        }
        List<VariableElement> fields = new ArrayList<>();
        for (Element enclosedElement : entityType.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.FIELD && !enclosedElement.getModifiers().contains(Modifier.STATIC)) {
                VariableElement field = (VariableElement) enclosedElement;
                if (!isIgnoredGeneratedField(field)) {
                    fields.add(field);
                }
            }
        }
        List<VariableElement> result = List.copyOf(fields);
        instanceFieldsCache.put(qualifiedName, result);
        return result;
    }

    private List<ExecutableElement> collectInvokableMethods(TypeElement entityType) {
        List<ExecutableElement> methods = new ArrayList<>();
        for (Element enclosedElement : entityType.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD
                    && !enclosedElement.getModifiers().contains(Modifier.STATIC)
                    && enclosedElement.getModifiers().contains(Modifier.PUBLIC)) {
                ExecutableElement method = (ExecutableElement) enclosedElement;
                if (!isIgnoredGeneratedMethod(method)) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    private boolean isIgnoredGeneratedField(VariableElement field) {
        String fieldName = field.getSimpleName().toString();
        return fieldName.startsWith("$") || hasAnnotation(field, "lombok.Generated");
    }

    private boolean isIgnoredGeneratedMethod(ExecutableElement method) {
        if (isAccessorMethod(method)) {
            return false;
        }
        String methodName = method.getSimpleName().toString();
        if (methodName.startsWith("$")) {
            return true;
        }
        if (methodName.equals("canEqual") && method.getParameters().size() == 1) {
            return true;
        }
        if (methodName.equals("builder") && method.getParameters().isEmpty()) {
            return true;
        }
        if (methodName.equals("toBuilder") && method.getParameters().isEmpty()) {
            return true;
        }
        return hasAnnotation(method, "lombok.Generated") || isObjectDerivedMethod(method);
    }

    private boolean isAccessorMethod(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        int parameterCount = method.getParameters().size();
        if (parameterCount == 0) {
            return methodName.startsWith("get") && methodName.length() > 3
                    || methodName.startsWith("is") && methodName.length() > 2;
        }
        return parameterCount == 1 && methodName.startsWith("set") && methodName.length() > 3;
    }

    private boolean hasAnnotation(Element element, String annotationType) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().toString().equals(annotationType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isObjectDerivedMethod(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        List<? extends VariableElement> parameters = method.getParameters();
        if (methodName.equals("toString") && parameters.isEmpty()) {
            return true;
        }
        if (methodName.equals("hashCode") && parameters.isEmpty()) {
            return true;
        }
        return methodName.equals("equals")
                && parameters.size() == 1
                && parameters.getFirst().asType().toString().equals(Object.class.getCanonicalName());
    }

    private TypeElement directSuperType(TypeElement entityType) {
        TypeMirror superType = entityType.getSuperclass();
        return superType.getKind() == TypeKind.NONE ? null : asTypeElement(superType);
    }

    private TypeElement asTypeElement(TypeMirror typeMirror) {
        Element element = types.asElement(typeMirror);
        return element instanceof TypeElement typeElement ? typeElement : null;
    }

    private boolean isJavaLangObject(TypeElement typeElement) {
        return typeElement != null && typeElement.getQualifiedName().contentEquals(Object.class.getCanonicalName());
    }

    private String capitalize(String fieldName) {
        return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private String packageNameOf(TypeElement typeElement) {
        PackageElement packageElement = elements.getPackageOf(typeElement);
        return packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
    }

    private String packageNameOf(String qualifiedTypeName) {
        int separator = qualifiedTypeName.lastIndexOf('.');
        return separator < 0 ? "" : qualifiedTypeName.substring(0, separator);
    }

    private String generatedModelPackageName(TypeElement typeElement) {
        return GeneratedNames.packageName(packageNameOf(typeElement));
    }

    private String reflectorTypeName(TypeElement entityType, String suffix) {
        return generatedModelPackageName(entityType) + "."
                + GeneratedNames.simpleName(enclosingSimpleNames(entityType), entityType.getSimpleName().toString(), suffix);
    }

    private List<String> enclosingSimpleNames(TypeElement typeElement) {
        ArrayDeque<String> names = new ArrayDeque<>();
        Element enclosing = typeElement.getEnclosingElement();
        while (enclosing instanceof TypeElement enclosingType) {
            names.addFirst(enclosingType.getSimpleName().toString());
            enclosing = enclosingType.getEnclosingElement();
        }
        return List.copyOf(names);
    }

    private void loadPersistedReflectorIndexIfNeeded() {
        if (persistedReflectorIndexLoaded) {
            return;
        }
        persistedReflectorIndexLoaded = true;
        // Retain entries we cannot resolve in this round (entityType == null).
        // Under IDEA JPS incremental compilation only the changed sources are
        // in the current round, so unrelated entities may not resolve. Pruning
        // them here would shrink the index one-way and drop installer entries.
        reflectorIndexStore.load((entityTypeName, reflectorTypeName) ->
                reflectorTypeName != null && !reflectorTypeName.isBlank());
    }

    private void persistReflectorIndexIfNeeded() {
        try {
            reflectorIndexStore.write();
        } catch (IOException ex) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Failed to persist reflector metadata: " + ex.getMessage());
        }
    }
}
