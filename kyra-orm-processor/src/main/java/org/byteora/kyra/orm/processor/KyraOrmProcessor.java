package org.byteora.kyra.orm.processor;

import org.byteora.kyra.orm.annotation.KyraScan;
import org.byteora.kyra.orm.annotation.MapperCapability;
import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.annotation.ReflectMetadataLevel;
import org.byteora.kyra.orm.dynamic.BindSqlNode;
import org.byteora.kyra.orm.dynamic.ChooseSqlNode;
import org.byteora.kyra.orm.dynamic.DynamicSqlNode;
import org.byteora.kyra.orm.dynamic.ForEachSqlNode;
import org.byteora.kyra.orm.dynamic.IfSqlNode;
import org.byteora.kyra.orm.dynamic.MixedSqlNode;
import org.byteora.kyra.orm.dynamic.TextSqlNode;
import org.byteora.kyra.orm.dynamic.TrimSqlNode;
import org.byteora.kyra.orm.dynamic.WhenSqlNode;
import org.byteora.kyra.orm.xml.MapperXmlDefinition;
import org.byteora.kyra.orm.xml.SqlCommandType;
import org.byteora.kyra.orm.xml.SqlNodeDefinition;
import org.byteora.kyra.processor.AliasSupport;
import org.byteora.kyra.processor.AsmUtils;
import org.byteora.kyra.processor.ReflectorClassGenerator;
import org.byteora.kyra.processor.ReflectSpec;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
        "org.byteora.kyra.orm.annotation.KyraScan",
        "org.byteora.kyra.orm.annotation.MapperCapability",
        "org.byteora.kyra.core.annotation.Reflect"
})
@SupportedOptions({"kyra.mapper", "kyra.debug", "kyra.module"})
public class KyraOrmProcessor extends AbstractProcessor {
    static final String SQL_EXECUTOR = "org.byteora.kyra.orm.runtime.SqlExecutor";
    private static final String GENERATED_PACKAGE_PREFIX = "gen";
    private static final String LIST_TYPE = "java.util.List";
    private static final String PAGE_TYPE = "org.byteora.kyra.orm.query.Page";
    private static final String PAGING_TYPE = "org.byteora.kyra.orm.query.Paging";
    private static final String ID_ANNOTATION = "org.byteora.kyra.orm.annotation.ID";
    static final String ID_STRATEGY_TYPE = "org.byteora.kyra.orm.annotation.IdStrategy";
    static final String ID_GENERATOR_TYPE = "org.byteora.kyra.orm.runtime.IdGenerator";

    private Filer filer;
    private Messager messager;
    private Elements elements;
    private Types types;
    private List<String> mapperXmlRoots = List.of();
    private boolean debugEnabled;
    private long debugStartNanos;
    private long debugStartMillis;
    private long entityElapsedNanos;
    private long mapperElapsedNanos;
    private int debugRound;
    private boolean mapperOptionWarningPrinted;
    private MapperXmlLoader xmlLoader;
    private GeneratedSupportIndexStore generatedSupportIndexStore;
    private ScanSpecIndexStore scanSpecIndexStore;
    private ReflectorClassGenerator reflectorClassGenerator;
    private MapperImplClassGenerator mapperImplClassGenerator;
    private boolean persistedScanSpecsLoaded;
    private boolean persistedSupportIndexLoaded;
    private boolean scanSpecIndexDirty;
    private boolean reflectorInstallerGenerated;
    private boolean tableInstallerGenerated;
    private String moduleNameOption;

    private final Map<String, ReflectSpec> reflectSpecs = new LinkedHashMap<>();
    private final Map<String, MapperCapabilitySpec> mapperCapabilitySpecs = new LinkedHashMap<>();
    private final List<ScanSpec> scanSpecs = new ArrayList<>();
    private final Set<String> generatedReflectors = new HashSet<>();
    private final Set<String> generatedMeta = new HashSet<>();
    private final Set<String> generatedMappers = new HashSet<>();
    private final Map<String, Boolean> mapperCapabilityPresenceCache = new LinkedHashMap<>();
    private final Map<String, List<TypeElement>> mapperCapabilityEntityTypesCache = new LinkedHashMap<>();
    private final Set<String> expandedAutoReflectTypes = new HashSet<>();
    private final Map<String, List<VariableElement>> instanceFieldsCache = new LinkedHashMap<>();
    private static final DateTimeFormatter DEBUG_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.mapperXmlRoots = parseMapperXmlRoots(processingEnv.getOptions().get("kyra.mapper"));
        this.debugEnabled = isDebugEnabled(processingEnv.getOptions().get("kyra.debug"));
        this.moduleNameOption = normalizeModuleName(processingEnv.getOptions().get("kyra.module"));
        this.xmlLoader = new MapperXmlLoader();
        this.generatedSupportIndexStore = new GeneratedSupportIndexStore(filer);
        this.scanSpecIndexStore = new ScanSpecIndexStore(filer);
        this.reflectorClassGenerator = new ReflectorClassGenerator(new ReflectorClassGenerator.Context() {
            @Override
            public Types types() {
                return KyraOrmProcessor.this.types;
            }

            @Override
            public TypeElement objectTypeElement() {
                return elements.getTypeElement(Object.class.getCanonicalName());
            }

            @Override
            public TypeMirror voidType() {
                return KyraOrmProcessor.this.types.getNoType(TypeKind.VOID);
            }

            @Override
            public ReflectSpec reflectSpec(TypeElement entityType) {
                return reflectSpecs.get(entityType.getQualifiedName().toString());
            }

            @Override
            public List<VariableElement> collectInstanceFields(TypeElement entityType) {
                return KyraOrmProcessor.this.collectInstanceFields(entityType);
            }

            @Override
            public List<ExecutableElement> collectInvokableMethods(TypeElement entityType) {
                return KyraOrmProcessor.this.collectInvokableMethods(entityType);
            }

            @Override
            public List<String> expandedFieldNames(TypeElement entityType) {
                return KyraOrmProcessor.this.expandedFieldNames(entityType);
            }

            @Override
            public boolean hasReflectSpec(TypeElement typeElement) {
                return reflectSpecs.containsKey(typeElement.getQualifiedName().toString());
            }

            @Override
            public TypeElement directSuperType(TypeElement entityType) {
                return KyraOrmProcessor.this.directSuperType(entityType);
            }

            @Override
            public boolean isJavaLangObject(TypeElement typeElement) {
                return KyraOrmProcessor.this.isJavaLangObject(typeElement);
            }

            @Override
            public String fieldAlias(VariableElement field) {
                return KyraOrmProcessor.this.aliasValue(field);
            }

            @Override
            public String capitalize(String value) {
                return KyraOrmProcessor.this.capitalize(value);
            }
        });
        this.mapperImplClassGenerator = new MapperImplClassGenerator(new MapperImplClassGenerator.Context() {
            @Override
            public Types types() {
                return KyraOrmProcessor.this.types;
            }

            @Override
            public boolean isListReturn(TypeMirror returnType) {
                return KyraOrmProcessor.this.isListReturn(returnType);
            }

            @Override
            public boolean isPageReturn(TypeMirror returnType) {
                return KyraOrmProcessor.this.isPageReturn(returnType);
            }

            @Override
            public TypeMirror extractListElementType(TypeMirror returnType) {
                return KyraOrmProcessor.this.extractListElementType(returnType);
            }

            @Override
            public TypeMirror extractPageElementType(TypeMirror returnType) {
                return KyraOrmProcessor.this.extractPageElementType(returnType);
            }

            @Override
            public boolean isPagingType(TypeMirror typeMirror) {
                return KyraOrmProcessor.this.isPagingType(typeMirror);
            }

            @Override
            public String statementFieldName(String statementId) {
                return KyraOrmProcessor.this.statementFieldName(statementId);
            }
        });
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (debugEnabled && debugStartNanos == 0L) {
            debugStartNanos = System.nanoTime();
            debugStartMillis = System.currentTimeMillis();
        }
        debugRound++;
        debug("round start processingOver=" + roundEnv.processingOver()
                + ", annotations=" + annotations.stream().map(TypeElement::getQualifiedName).map(CharSequence::toString).sorted().toList()
                + ", rootElements=" + roundEnv.getRootElements().stream().map(Element::getSimpleName).map(CharSequence::toString).sorted().toList());
        loadPersistedScanSpecsIfNeeded();
        loadPersistedSupportIndexIfNeeded();
        mapperCapabilityPresenceCache.clear();
        mapperCapabilityEntityTypesCache.clear();
        collectReflectSpecs(roundEnv);
        long mapperStart = System.nanoTime();
        collectMapperCapabilitySpecs(roundEnv);
        if (collectScanSpecs(roundEnv)) {
            scanSpecIndexDirty = true;
        }
        collectMapperReflectSpecs(roundEnv);
        registerScannedEntityReflectSpecs();
        registerBuiltinReflectSpecs();
        generateReflectors();
        if (!roundEnv.processingOver() && !reflectorInstallerGenerated) {
            generateReflectorInstallerAndService();
        }
        mapperElapsedNanos += System.nanoTime() - mapperStart;

        long entityStart = System.nanoTime();
        generateMeta();
        if (!roundEnv.processingOver() && !tableInstallerGenerated) {
            generateTableInstallerAndService();
        }
        entityElapsedNanos += System.nanoTime() - entityStart;

        if (!roundEnv.processingOver() && !scanSpecs.isEmpty()) {
            mapperStart = System.nanoTime();
            generateSupportsAndMappers();
            mapperElapsedNanos += System.nanoTime() - mapperStart;
        }
        if (roundEnv.processingOver()) {
            persistScanSpecsIfNeeded();
            persistGeneratedSupportIndexIfNeeded();
        }
        if (debugEnabled && roundEnv.processingOver() && debugStartNanos != 0L) {
            printDebugSummary();
        }
        debug("round end processingOver=" + roundEnv.processingOver()
                + ", reflectSpecs=" + reflectSpecs.size()
                + ", generatedReflectors=" + generatedReflectors.size()
                + ", indexDirty=" + generatedSupportIndexStore.isDirty()
                + ", reflectorInstallerGenerated=" + reflectorInstallerGenerated
                + ", tableInstallerGenerated=" + tableInstallerGenerated);
        return false;
    }

    private void collectReflectSpecs(RoundEnvironment roundEnv) {
        List<String> collected = new ArrayList<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(Reflect.class)) {
            if (!(element instanceof TypeElement typeElement) || !isReflectTarget(typeElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Reflect can only be used on classes, records, or enums", element);
                continue;
            }
            Reflect reflect = typeElement.getAnnotation(Reflect.class);
            reflectSpecs.put(typeElement.getQualifiedName().toString(),
                    new ReflectSpec(typeElement, reflect.suffix(), reflect.metadata(), reflect.annotationMetadata()));
            collected.add(typeElement.getQualifiedName().toString());
        }
        debug("collected @Reflect count=" + collected.size() + ", types=" + collected);
    }

    private void registerBuiltinReflectSpecs() {
        TypeElement pageType = elements.getTypeElement(PAGE_TYPE);
        if (pageType != null) {
            registerReflectType(pageType, new HashSet<>());
            debug("registered builtin reflect type " + PAGE_TYPE);
        } else {
            debug("builtin reflect type missing from elements: " + PAGE_TYPE);
        }
    }

    private void collectMapperCapabilitySpecs(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(MapperCapability.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@MapperCapability can only be used on classes", element);
                continue;
            }
            TypeElement implType = (TypeElement) element;
            TypeElement contractType = mapperCapabilityContractType(implType);
            if (contractType == null || contractType.getKind() != ElementKind.INTERFACE) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@MapperCapability value must be an interface", implType);
                continue;
            }
            mapperCapabilitySpecs.put(contractType.getQualifiedName().toString(), new MapperCapabilitySpec(contractType, implType));
        }
        registerBuiltinMapperCapability("org.byteora.kyra.orm.mapper.BaseMapper", "org.byteora.kyra.orm.mapper.BaseMapperImpl");
    }

    private void registerBuiltinMapperCapability(String contractTypeName, String implTypeName) {
        if (mapperCapabilitySpecs.containsKey(contractTypeName)) {
            return;
        }
        TypeElement contractType = elements.getTypeElement(contractTypeName);
        TypeElement implType = elements.getTypeElement(implTypeName);
        if (contractType == null || implType == null) {
            return;
        }
        mapperCapabilitySpecs.put(contractTypeName, new MapperCapabilitySpec(contractType, implType));
    }

    private boolean collectScanSpecs(RoundEnvironment roundEnv) {
        boolean changed = false;
        for (Element element : roundEnv.getElementsAnnotatedWith(KyraScan.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@KyraScan can only be used on classes", element);
                continue;
            }
            TypeElement configType = (TypeElement) element;
            warnIfMapperOptionMissing(configType);
            KyraScan scan = configType.getAnnotation(KyraScan.class);
            changed |= upsertScanSpec(new ScanSpec(configType, mapperXmlRoots, List.of(scan.entity()), List.of(scan.mapper())));
        }
        for (Element rootElement : roundEnv.getRootElements()) {
            changed |= collectScanTypes(rootElement);
        }
        // Enumerate entities/mappers from the compilation classpath so we
        // don't rely solely on javac's current round. This makes incremental
        // builds (and future Maven support) find types defined in unchanged
        // source files or in dependency jars.
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        for (ScanSpec scanSpec : scanSpecs) {
            packages.addAll(scanSpec.entityPackages);
            packages.addAll(scanSpec.mapperPackages);
        }
        for (String pkg : packages) {
            changed |= enumeratePackageTypes(pkg);
        }
        return changed;
    }

    private boolean enumeratePackageTypes(String pkg) {
        if (pkg == null || pkg.isBlank()) {
            return false;
        }
        PackageElement packageElement = elements.getPackageElement(pkg);
        if (packageElement == null) {
            return false;
        }
        boolean changed = false;
        for (Element enclosed : packageElement.getEnclosedElements()) {
            if (enclosed.getKind().isClass() || enclosed.getKind().isInterface()) {
                changed |= collectScanTypes(enclosed);
            }
        }
        return changed;
    }

    private void generateReflectors() {
        debug("generateReflectors start reflectSpecs=" + reflectSpecs.size()
                + ", persistedIndexReflectors=" + generatedSupportIndexStore.reflectors().size()
                + ", alreadyGenerated=" + generatedReflectors.size()
                + ", indexDirty=" + generatedSupportIndexStore.isDirty());
        for (ReflectSpec spec : reflectSpecs.values()) {
            if (!spec.generateClass()) {
                debug("skip reflector class generation for " + spec.typeElement().getQualifiedName() + " because generateClass=false");
                continue;
            }
            if (!generatedReflectors.add(spec.typeElement().getQualifiedName().toString())) {
                debug("skip reflector class generation for " + spec.typeElement().getQualifiedName() + " because it was already generated in this processing run");
                continue;
            }
            try {
                writeReflectorClass(spec.typeElement(), spec.suffix());
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate reflector: " + ex.getMessage(), spec.typeElement());
            }
        }
        for (GeneratedSupportIndexStore.ReflectRegistration registration : generatedSupportIndexStore.reflectors()) {
            TypeElement entityType = elements.getTypeElement(registration.entityTypeName());
            if (entityType == null || !generatedReflectors.add(registration.entityTypeName())) {
                debug("skip persisted reflector regeneration for " + registration.entityTypeName()
                        + ", entityFound=" + (entityType != null)
                        + ", alreadyGenerated=" + generatedReflectors.contains(registration.entityTypeName()));
                continue;
            }
            try {
                writeReflectorClassByName(entityType, registration.reflectorTypeName());
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to regenerate reflector: " + ex.getMessage(), entityType);
            }
        }
    }

    /**
     * Scanned entities always get a {@code xxxTable} meta class via {@link #generateMeta()}, but a
     * reflector is only produced for types present in {@link #reflectSpecs}. Mapper collection adds
     * reflect specs for entities reachable from a mapper; an entity in a scanned {@code entity}
     * package with no mapper would otherwise get a table but no reflector. Register every scanned
     * entity as a reflect type here so its reflector is generated regardless of mapper presence.
     */
    private void registerScannedEntityReflectSpecs() {
        for (ScanSpec scanSpec : scanSpecs) {
            for (String entityTypeName : scanSpec.entityTypeNames) {
                TypeElement entityType = elements.getTypeElement(entityTypeName);
                if (entityType == null || !shouldCollectScannedType(entityType) || !isReflectTarget(entityType)) {
                    continue;
                }
                registerReflectType(entityType, new HashSet<>());
            }
        }
    }

    private void generateMeta() {
        for (ScanSpec scanSpec : scanSpecs) {
            for (String entityTypeName : scanSpec.entityTypeNames) {
                TypeElement entityType = elements.getTypeElement(entityTypeName);
                if (entityType == null || !shouldCollectScannedType(entityType) || !generatedMeta.add(entityTypeName)) {
                    continue;
                }
                try {
                    writeMetaClass(entityType);
                } catch (IOException ex) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Failed to generate meta class: " + ex.getMessage(), entityType);
                }
            }
        }
        for (GeneratedSupportIndexStore.TableRegistration registration : generatedSupportIndexStore.tables()) {
            TypeElement entityType = elements.getTypeElement(registration.entityTypeName());
            if (entityType == null || !shouldCollectScannedType(entityType) || !generatedMeta.add(registration.entityTypeName())) {
                continue;
            }
            try {
                writeMetaClass(entityType);
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Failed to regenerate meta class: " + ex.getMessage(), entityType);
            }
        }
    }

    private void collectMapperReflectSpecs(RoundEnvironment roundEnv) {
        for (ScanSpec scanSpec : scanSpecs) {
            try {
                Map<String, MapperXmlDefinition> xmlDefinitions = loadMapperXmlDefinitions(scanSpec);
                for (MapperXmlDefinition xmlDefinition : xmlDefinitions.values()) {
                    TypeElement mapperType = elements.getTypeElement(xmlDefinition.getNamespace());
                    if (mapperType == null) {
                        throw new ProcessorException("Mapper interface not found for namespace: " + xmlDefinition.getNamespace());
                    }
                    collectMapperReflectSpecs(mapperType, xmlDefinition);
                }
                for (TypeElement mapperType : findMapperTypes(scanSpec)) {
                    registerMapperCapabilityReflectSpecs(mapperType);
                }
            } catch (IOException ex) {
                printScanSpecMessage(Diagnostic.Kind.ERROR, "Failed to collect mapper reflect specs: " + ex.getMessage(), scanSpec);
            } catch (ProcessorException ex) {
                printScanSpecMessage(Diagnostic.Kind.ERROR, ex.getMessage(), scanSpec);
            }
        }
    }

    private void collectMapperReflectSpecs(TypeElement mapperType, MapperXmlDefinition xmlDefinition) {
        for (MapperMethodSpec mapperMethod : mapperMethods(mapperType, xmlDefinition)) {
            registerMapperMethodReflectTypes(mapperMethod.method(), mapperMethod.statement());
        }
    }

    private void registerMapperCapabilityReflectSpecs(TypeElement mapperType) {
        for (TypeElement entityType : mapperCapabilityEntityTypes(mapperType)) {
            registerReflectType(entityType, new HashSet<>());
        }
    }

    private List<TypeElement> findMapperTypes(ScanSpec scanSpec) {
        if (scanSpec.mapperTypeNames.isEmpty()) {
            return List.of();
        }
        List<TypeElement> mapperTypes = new ArrayList<>();
        for (String mapperTypeName : scanSpec.mapperTypeNames) {
            TypeElement mapperType = elements.getTypeElement(mapperTypeName);
            if (mapperType != null && shouldCollectScannedType(mapperType)) {
                mapperTypes.add(mapperType);
            }
        }
        return mapperTypes;
    }

    private boolean collectScanTypes(Element element) {
        if (!(element instanceof TypeElement typeElement) || !shouldCollectScannedType(typeElement)) {
            return false;
        }
        boolean changed = false;
        String qualifiedName = typeElement.getQualifiedName().toString();
        for (ScanSpec scanSpec : scanSpecs) {
            if (typeElement.getKind() == ElementKind.INTERFACE && matchesPackage(typeElement, scanSpec.mapperPackages)) {
                changed |= scanSpec.mapperTypeNames.add(qualifiedName);
            }
            if (typeElement.getKind().isClass() && matchesPackage(typeElement, scanSpec.entityPackages)) {
                changed |= scanSpec.entityTypeNames.add(qualifiedName);
            }
        }
        return changed;
    }

    private boolean shouldCollectScannedType(TypeElement typeElement) {
        return typeElement.getEnclosingElement().getKind() == ElementKind.PACKAGE
                && !isGeneratedType(typeElement)
                && !hasAnnotation(typeElement, "lombok.Generated");
    }

    private boolean shouldRetainPersistedEntityTypeName(String entityTypeName) {
        TypeElement entityType = elements.getTypeElement(entityTypeName);
        return entityType == null || shouldCollectScannedType(entityType);
    }

    private boolean shouldRetainPersistedMapperTypeName(String mapperTypeName) {
        TypeElement mapperType = elements.getTypeElement(mapperTypeName);
        return mapperType == null || shouldCollectScannedType(mapperType);
    }

    private boolean matchesPackage(TypeElement typeElement, List<String> packages) {
        String packageName = packageNameOf(typeElement);
        return packages.stream().anyMatch(pkg -> packageName.equals(pkg) || packageName.startsWith(pkg + "."));
    }

    private boolean isGeneratedType(TypeElement typeElement) {
        String simpleName = typeElement.getSimpleName().toString();
        return simpleName.endsWith("Reflector")
                || simpleName.endsWith("Generated")
                || simpleName.endsWith("Impl")
                || simpleName.endsWith("Table");
    }

    private void generateSupportsAndMappers() {
        for (ScanSpec scanSpec : scanSpecs) {
            try {
                Map<String, MapperXmlDefinition> xmlDefinitions = loadMapperXmlDefinitions(scanSpec);
                for (MapperXmlDefinition xmlDefinition : xmlDefinitions.values()) {
                    TypeElement mapperType = elements.getTypeElement(xmlDefinition.getNamespace());
                    if (mapperType == null) {
                        throw new ProcessorException("Mapper interface not found for namespace: " + xmlDefinition.getNamespace());
                    }
                    if (!generatedMappers.add(xmlDefinition.getNamespace())) {
                        continue;
                    }
                    writeMapperImpl(mapperType, xmlDefinition);
                }
                for (TypeElement mapperType : findMapperTypes(scanSpec)) {
                    String mapperQualifiedName = mapperType.getQualifiedName().toString();
                    if (generatedMappers.contains(mapperQualifiedName) || xmlDefinitions.containsKey(mapperQualifiedName)) {
                        continue;
                    }
                    if (!hasMapperCapability(mapperType)) {
                        continue;
                    }
                    if (hasDeclaredAbstractMethods(mapperType)) {
                        throw new ProcessorException("Mapper declares abstract methods but no xml found: " + mapperQualifiedName);
                    }
                    generatedMappers.add(mapperQualifiedName);
                    writeMapperImpl(mapperType, new MapperXmlDefinition(mapperQualifiedName, Map.of()));
                }
            } catch (IOException ex) {
                printScanSpecMessage(Diagnostic.Kind.ERROR, "Failed to process scan config: " + ex.getMessage(), scanSpec);
            } catch (ProcessorException ex) {
                printScanSpecMessage(Diagnostic.Kind.ERROR, ex.getMessage(), scanSpec);
            }
        }
    }

    private Map<String, MapperXmlDefinition> loadMapperXmlDefinitions(ScanSpec scanSpec) throws IOException {
        if (scanSpec.xmlDefinitions != null) {
            return scanSpec.xmlDefinitions;
        }
        scanSpec.xmlDefinitions = xmlLoader.load(scanSpec.xmlRoots);
        return scanSpec.xmlDefinitions;
    }

    private List<String> parseMapperXmlRoots(String optionValue) {
        if (optionValue == null || optionValue.isBlank()) {
            return List.of();
        }
        return List.of(optionValue.split(","))
                .stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private boolean isDebugEnabled(String optionValue) {
        if (optionValue == null) {
            return false;
        }
        return optionValue.equalsIgnoreCase("true")
                || optionValue.equalsIgnoreCase("1")
                || optionValue.equalsIgnoreCase("yes")
                || optionValue.equalsIgnoreCase("on");
    }

    private void debug(String message) {
        if (debugEnabled) {
            messager.printMessage(Diagnostic.Kind.NOTE, "kyra.debug round=" + debugRound + " " + message);
        }
    }

    private List<String> summarizeReflectors(List<GeneratedSupportIndexStore.ReflectRegistration> registrations) {
        int limit = 30;
        List<String> values = registrations.stream()
                .limit(limit)
                .map(registration -> registration.entityTypeName() + " -> " + registration.reflectorTypeName())
                .toList();
        if (registrations.size() <= limit) {
            return values;
        }
        List<String> summary = new ArrayList<>(values);
        summary.add("... +" + (registrations.size() - limit) + " more");
        return summary;
    }

    private void printDebugSummary() {
        long endMillis = System.currentTimeMillis();
        long totalElapsedNanos = System.nanoTime() - debugStartNanos;
        String message = "kyra.debug start=" + formatClockTime(debugStartMillis)
                + ", end=" + formatClockTime(endMillis)
                + ", total=" + formatElapsed(totalElapsedNanos)
                + ", entity=" + formatElapsed(entityElapsedNanos)
                + ", mapper=" + formatElapsed(mapperElapsedNanos);
        messager.printMessage(Diagnostic.Kind.NOTE, message);
    }

    private String formatClockTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(DEBUG_TIME_FORMATTER);
    }

    private String formatElapsed(long elapsedNanos) {
        long elapsedMillis = elapsedNanos / 1_000_000L;
        long minutes = elapsedMillis / 60_000L;
        long seconds = (elapsedMillis % 60_000L) / 1_000L;
        long millis = elapsedMillis % 1_000L;
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }

    private void warnIfMapperOptionMissing(TypeElement configType) {
        if (mapperOptionWarningPrinted || !mapperXmlRoots.isEmpty()) {
            return;
        }
        mapperOptionWarningPrinted = true;
        messager.printMessage(
                Diagnostic.Kind.WARNING,
                "Missing compiler option 'kyra.mapper'. Configure mapper xml paths, for example: -Akyra.mapper=${project.projectDir}/src/main/resources/mapper",
                configType
        );
    }

    private void writeMetaClass(TypeElement entityType) throws IOException {
        String packageName = generatedModelPackageName(entityType);
        String generatedSimpleName = tableSimpleName(entityType);
        String qualifiedName = packageName.isEmpty() ? generatedSimpleName : packageName + "." + generatedSimpleName;
        writeSourceFile(qualifiedName, entityType, buildMetaSource(packageName, generatedSimpleName, entityType));
        generatedSupportIndexStore.upsertTable(entityType.getQualifiedName().toString(), qualifiedName);
    }

    private void generateTableInstallerAndService() {
        if (generatedSupportIndexStore.tables().isEmpty()) {
            debug("skip table installer generation because table registrations are empty");
            return;
        }
        String installerTypeName = aggregateTableInstallerTypeName();
        debug("generate table installer " + installerTypeName
                + ", registrations=" + generatedSupportIndexStore.tables().size()
                + ", serviceOrigins=" + scanSpecs.stream().map(scanSpec -> scanSpec.configQualifiedName).toList()
                + ", tables=" + generatedSupportIndexStore.tables().stream().map(GeneratedSupportIndexStore.TableRegistration::entityTypeName).toList());
        try {
            writeClassFile(installerTypeName,
                    scanSpecs.stream().map(ScanSpec::configType).filter(element -> element != null).findFirst().orElse(null),
                    buildTableInstallerClass(installerTypeName, generatedSupportIndexStore.tables()));
            generatedSupportIndexStore.writeTableInstallerService(
                    installerTypeName,
                    scanSpecs.stream().map(ScanSpec::configType).filter(element -> element != null).toList()
            );
            tableInstallerGenerated = true;
            debug("generated table installer and service " + installerTypeName);
        } catch (IOException ex) {
            debug("failed to generate table installer " + installerTypeName
                    + ", exception=" + ex.getClass().getName()
                    + ", message=" + ex.getMessage());
            messager.printMessage(Diagnostic.Kind.WARNING, "Failed to generate table installer: " + ex.getMessage());
        }
    }

    private void generateReflectorInstallerAndService() {
        if (generatedSupportIndexStore.reflectors().isEmpty()) {
            debug("skip reflector installer generation because reflector registrations are empty");
            return;
        }
        String installerTypeName = aggregateReflectorInstallerTypeName();
        debug("generate reflector installer " + installerTypeName
                + ", registrations=" + generatedSupportIndexStore.reflectors().size()
                + ", serviceOrigins=" + scanSpecs.stream().map(scanSpec -> scanSpec.configQualifiedName).toList()
                + ", reflectors=" + summarizeReflectors(generatedSupportIndexStore.reflectors()));
        try {
            writeClassFile(installerTypeName,
                    scanSpecs.stream().map(ScanSpec::configType).filter(element -> element != null).findFirst().orElse(null),
                    buildReflectorInstallerClass(installerTypeName, generatedSupportIndexStore.reflectors()));
            generatedSupportIndexStore.writeReflectorInstallerService(
                    installerTypeName,
                    scanSpecs.stream().map(ScanSpec::configType).filter(element -> element != null).toList()
            );
            reflectorInstallerGenerated = true;
            debug("generated reflector installer and service " + installerTypeName);
        } catch (IOException ex) {
            debug("failed to generate reflector installer " + installerTypeName
                    + ", exception=" + ex.getClass().getName()
                    + ", message=" + ex.getMessage());
            messager.printMessage(Diagnostic.Kind.WARNING, "Failed to generate reflector installer: " + ex.getMessage());
        }
    }

    private byte[] buildReflectorInstallerClass(String installerTypeName, List<GeneratedSupportIndexStore.ReflectRegistration> registrations) {
        String classInternalName = AsmUtils.internalName(installerTypeName);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, classInternalName, null, "java/lang/Object",
                new String[]{"org/byteora/kyra/core/runtime/ReflectorInstaller"});
        writeNoArgConstructor(cw, classInternalName);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "install", "()V", null, null);
        mv.visitCode();
        for (GeneratedSupportIndexStore.ReflectRegistration registration : registrations) {
            emitReflectorRegistration(mv, registration);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private String aggregateReflectorInstallerTypeName() {
        String moduleName = moduleNameOption;
        if (moduleName == null) {
            moduleName = generatedSupportIndexStore.reflectors().stream()
                    .map(registration -> packageNameOf(registration.entityTypeName()))
                    .filter(packageName -> !packageName.isBlank())
                    .findFirst()
                    .orElse("kyra");
        }
        return GENERATED_PACKAGE_PREFIX + "." + packageHashSegment(moduleName) + "." + toPascalCase(moduleName) + "ReflectorInstaller";
    }

    private void emitReflectorRegistration(MethodVisitor mv, GeneratedSupportIndexStore.ReflectRegistration registration) {
        pushClassLiteral(mv, registration.entityTypeName());
        String reflectorInternalName = AsmUtils.internalName(registration.reflectorTypeName());
        mv.visitTypeInsn(Opcodes.NEW, reflectorInternalName);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, reflectorInternalName, "<init>", "()V", false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "org/byteora/kyra/core/runtime/ReflectorRegistry",
                "register",
                "(Ljava/lang/Class;Lorg/byteora/kyra/core/runtime/Reflector;)V",
                false);
    }

    private byte[] buildTableInstallerClass(String installerTypeName, List<GeneratedSupportIndexStore.TableRegistration> registrations) {
        String classInternalName = AsmUtils.internalName(installerTypeName);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, classInternalName, null, "java/lang/Object",
                new String[]{"org/byteora/kyra/orm/query/TableInstaller"});
        writeNoArgConstructor(cw, classInternalName);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "install", "()V", null, null);
        mv.visitCode();
        for (GeneratedSupportIndexStore.TableRegistration registration : registrations) {
            emitTableRegistration(mv, registration);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private String aggregateTableInstallerTypeName() {
        String moduleName = moduleNameOption;
        if (moduleName == null) {
            moduleName = generatedSupportIndexStore.tables().stream()
                    .map(registration -> packageNameOf(registration.entityTypeName()))
                    .filter(packageName -> !packageName.isBlank())
                    .findFirst()
                    .orElse("kyra");
        }
        return GENERATED_PACKAGE_PREFIX + "." + packageHashSegment(moduleName) + "." + toPascalCase(moduleName) + "TableInstaller";
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

    private void writeReflectorClass(TypeElement entityType, String suffix) throws IOException {
        String qualifiedName = reflectorTypeName(entityType, suffix);
        writeClassFile(qualifiedName, entityType, reflectorClassGenerator.buildReflectorClass(qualifiedName, entityType));
        boolean changed = generatedSupportIndexStore.upsertReflector(entityType.getQualifiedName().toString(), qualifiedName);
        debug("wrote reflector class " + qualifiedName
                + " for " + entityType.getQualifiedName()
                + ", indexChanged=" + changed
                + ", indexDirty=" + generatedSupportIndexStore.isDirty());
    }

    private void writeReflectorClassByName(TypeElement entityType, String qualifiedName) throws IOException {
        writeClassFile(qualifiedName, entityType, reflectorClassGenerator.buildReflectorClass(qualifiedName, entityType));
        boolean changed = generatedSupportIndexStore.upsertReflector(entityType.getQualifiedName().toString(), qualifiedName);
        debug("rewrote persisted reflector class " + qualifiedName
                + " for " + entityType.getQualifiedName()
                + ", indexChanged=" + changed
                + ", indexDirty=" + generatedSupportIndexStore.isDirty());
    }

    private void writeMapperImpl(TypeElement mapperType, MapperXmlDefinition xmlDefinition) throws IOException {
        String qualifiedName = mapperImplTypeName(mapperType);
        List<MapperMethodSpec> mapperMethods = mapperMethods(mapperType, xmlDefinition);
        writeClassFile(
                qualifiedName,
                mapperType,
                mapperImplClassGenerator.buildMapperImplClass(
                        qualifiedName,
                        mapperType,
                        mapperMethods,
                        mapperCapabilityDelegates(mapperType, mapperMethods)
                )
        );
    }

    private void writeSourceFile(String qualifiedName, Element originatingElement, String source) throws IOException {
        JavaFileObject fileObject = originatingElement == null
                ? filer.createSourceFile(qualifiedName)
                : filer.createSourceFile(qualifiedName, originatingElement);
        try (Writer writer = fileObject.openWriter()) {
            writer.write(source);
        }
    }

    private void writeClassFile(String qualifiedName, Element originatingElement, byte[] bytecode) throws IOException {
        JavaFileObject fileObject = originatingElement == null
                ? filer.createClassFile(qualifiedName)
                : filer.createClassFile(qualifiedName, originatingElement);
        try (OutputStream outputStream = fileObject.openOutputStream()) {
            outputStream.write(bytecode);
        }
    }

    private String buildMetaSource(String packageName, String generatedSimpleName, TypeElement entityType) {
        String entityTypeName = entityType.getQualifiedName().toString();
        String tableName = resolveTableName(entityType);
        String tableConstantName = "TABLE";
        List<VariableElement> tableFields = collectTableFields(entityType);
        VariableElement idField = resolveIdField(entityType, tableFields);
        IdGenerationSpec idGeneration = resolveIdGeneration(entityType, idField);

        StringBuilder source = new StringBuilder();
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
        source.append("import org.byteora.kyra.orm.query.Column;\n");
        source.append("import org.byteora.kyra.orm.query.EntityTable;\n\n");
        source.append("public final class ").append(generatedSimpleName)
                .append(" extends EntityTable<").append(entityTypeName).append("> {\n");
        source.append("    public static final ").append(generatedSimpleName).append(' ')
                .append(tableConstantName).append(" = new ").append(generatedSimpleName)
                .append("(\"").append(escapeJava(tableName)).append("\", null);\n\n");
        if (idGeneration.generatorInstantiation() != null) {
            source.append("    private static final ").append(ID_GENERATOR_TYPE).append(" ID_GENERATOR = ")
                    .append(idGeneration.generatorInstantiation()).append(";\n\n");
        }
        source.append("    public static ").append(generatedSimpleName).append(" as(String alias) {\n");
        source.append("        return new ").append(generatedSimpleName).append("(")
                .append(tableConstantName).append(".tableName(), alias);\n");
        source.append("    }\n\n");
        source.append("    public ").append(generatedSimpleName).append(" alias(String alias) {\n");
        source.append("        return new ").append(generatedSimpleName).append("(this.tableName(), alias);\n");
        source.append("    }\n\n");
        for (VariableElement field : tableFields) {
            String fieldName = field.getSimpleName().toString();
            String fieldType = renderRuntimeCastType(field.asType());
            source.append("    public final Column<").append(entityTypeName).append(", ")
                    .append(fieldType).append("> ")
                    .append(fieldName).append(" = column(\"")
                    .append(escapeJava(resolveColumnName(field))).append("\", ")
                    .append(fieldType).append(".class);\n");
        }
        source.append("\n    @Override\n");
        source.append("    public Column<").append(entityTypeName).append(", ?> idColumn() {\n");
        if (idField != null) {
            source.append("        return ").append(idField.getSimpleName()).append(";\n");
        } else {
            source.append("        throw new IllegalStateException(\"No id field configured for table: \" + tableName());\n");
        }
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public ").append(ID_STRATEGY_TYPE).append(" idStrategy() {\n");
        source.append("        return ").append(ID_STRATEGY_TYPE).append('.').append(idGeneration.strategy()).append(";\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public ").append(ID_GENERATOR_TYPE).append(" idGenerator() {\n");
        source.append("        return ").append(idGeneration.generatorInstantiation() == null ? "null" : "ID_GENERATOR").append(";\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public String fieldName(String column) {\n");
        source.append("        return switch (column) {\n");
        for (VariableElement field : tableFields) {
            source.append("            case \"").append(escapeJava(resolveColumnName(field))).append("\" -> \"")
                    .append(field.getSimpleName()).append("\";\n");
        }
        source.append("            default -> column;\n");
        source.append("        };\n");
        source.append("    }\n\n");
        source.append("    @Override\n");
        source.append("    public String columnName(String field) {\n");
        source.append("        return switch (field) {\n");
        for (VariableElement field : tableFields) {
            source.append("            case \"").append(field.getSimpleName()).append("\" -> \"")
                    .append(escapeJava(resolveColumnName(field))).append("\";\n");
        }
        source.append("            default -> field;\n");
        source.append("        };\n");
        source.append("    }\n");
        source.append("\n    private ").append(generatedSimpleName).append("(String tableName, String alias) {\n");
        source.append("        super(").append(entityTypeName).append(".class, tableName, alias);\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
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

    private AnnotationValue annotationValue(AnnotationMirror annotationMirror, ExecutableElement method) {
        AnnotationValue value = annotationMirror.getElementValues().get(method);
        if (value != null) {
            return value;
        }
        AnnotationValue defaultValue = method.getDefaultValue();
        if (defaultValue == null) {
            throw new ProcessorException("Missing annotation value for " + method.getSimpleName() + " on " + annotationMirror);
        }
        return defaultValue;
    }

    private void emitTableRegistration(MethodVisitor mv, GeneratedSupportIndexStore.TableRegistration registration) {
        pushClassLiteral(mv, registration.entityTypeName());
        String tableInternalName = AsmUtils.internalName(registration.tableTypeName());
        mv.visitFieldInsn(Opcodes.GETSTATIC, tableInternalName, "TABLE", "L" + tableInternalName + ";");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "org/byteora/kyra/orm/query/Tables",
                "register",
                "(Ljava/lang/Class;Lorg/byteora/kyra/orm/query/EntityTable;)V",
                false);
    }

    private void writeNoArgConstructor(ClassWriter cw, String classInternalName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void pushClassLiteral(MethodVisitor mv, String typeName) {
        TypeElement typeElement = elements.getTypeElement(typeName);
        String internalName = typeElement == null ? AsmUtils.internalName(typeName) : AsmUtils.internalName(typeElement);
        mv.visitLdcInsn(org.objectweb.asm.Type.getObjectType(internalName));
    }

    private boolean isListReturn(TypeMirror returnType) {
        if (!(returnType instanceof DeclaredType declaredType)) {
            return false;
        }
        Element element = declaredType.asElement();
        return element instanceof TypeElement typeElement
                && typeElement.getQualifiedName().contentEquals(LIST_TYPE)
                && declaredType.getTypeArguments().size() == 1;
    }

    private boolean isPageReturn(TypeMirror returnType) {
        if (!(returnType instanceof DeclaredType declaredType)) {
            return false;
        }
        Element element = declaredType.asElement();
        return element instanceof TypeElement typeElement
                && typeElement.getQualifiedName().contentEquals(PAGE_TYPE)
                && declaredType.getTypeArguments().size() == 1;
    }

    private TypeMirror extractListElementType(TypeMirror returnType) {
        DeclaredType declaredType = (DeclaredType) returnType;
        return declaredType.getTypeArguments().get(0);
    }

    private TypeMirror extractPageElementType(TypeMirror returnType) {
        DeclaredType declaredType = (DeclaredType) returnType;
        return declaredType.getTypeArguments().get(0);
    }

    private void registerMapperMethodReflectTypes(ExecutableElement method, SqlNodeDefinition statement) {
        if (statement.commandType() == SqlCommandType.SELECT) {
            TypeMirror returnType = method.getReturnType();
            if (isPageReturn(returnType)) {
                TypeMirror elementType = ((DeclaredType) returnType).getTypeArguments().getFirst();
                validateMapperTypeSupport(elementType, mapperTypeContext(method, "return"));
                registerReflectTypes(elementType);
            } else if (isListReturn(returnType)) {
                TypeMirror elementType = ((DeclaredType) returnType).getTypeArguments().getFirst();
                validateMapperTypeSupport(elementType, mapperTypeContext(method, "return"));
                registerReflectTypes(elementType);
            } else if (returnType.getKind() != TypeKind.VOID && !returnType.getKind().isPrimitive()) {
                validateMapperTypeSupport(returnType, mapperTypeContext(method, "return"));
                registerReflectTypes(returnType);
            }
        }
        for (VariableElement parameter : method.getParameters()) {
            validateMapperTypeSupport(parameter.asType(), mapperTypeContext(method, "parameter '" + parameter.getSimpleName() + "'"));
            registerReflectTypes(parameter.asType());
        }
    }

    private void registerReflectTypes(TypeMirror typeMirror) {
        registerReflectTypes(typeMirror, new HashSet<>());
    }

    private void registerReflectTypes(TypeMirror typeMirror, Set<String> visiting) {
        if (typeMirror == null) {
            return;
        }
        if (typeMirror instanceof DeclaredType declaredType) {
            TypeElement typeElement = asTypeElement(typeMirror);
            if (typeElement != null && shouldAutoReflect(typeElement)) {
                registerReflectType(typeElement, visiting);
            }
            for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
                registerReflectTypes(typeArgument, visiting);
            }
            return;
        }
        if (typeMirror instanceof ArrayType arrayType) {
            registerReflectTypes(arrayType.getComponentType(), visiting);
            return;
        }
        if (typeMirror instanceof WildcardType wildcardType) {
            registerReflectTypes(wildcardType.getExtendsBound(), visiting);
            registerReflectTypes(wildcardType.getSuperBound(), visiting);
        }
    }

    private void registerReflectType(TypeElement typeElement, Set<String> visiting) {
        String qualifiedName = typeElement.getQualifiedName().toString();
        if (expandedAutoReflectTypes.contains(qualifiedName)) {
            return;
        }
        if (!visiting.add(qualifiedName)) {
            return;
        }
        Reflect reflect = typeElement.getAnnotation(Reflect.class);
        ReflectSpec reflectSpec = reflect == null
                ? new ReflectSpec(typeElement, "Reflector", ReflectMetadataLevel.BASIC, false)
                : new ReflectSpec(typeElement, reflect.suffix(), reflect.metadata(), reflect.annotationMetadata());
        reflectSpecs.putIfAbsent(qualifiedName, reflectSpec);
        TypeElement superType = directSuperType(typeElement);
        if (superType != null && !isJavaLangObject(superType) && shouldAutoReflect(superType)) {
            registerReflectType(superType, visiting);
        }
        for (VariableElement field : collectInstanceFields(typeElement)) {
            registerReflectTypes(field.asType(), visiting);
        }
        expandedAutoReflectTypes.add(qualifiedName);
        visiting.remove(qualifiedName);
    }

    private boolean shouldAutoReflect(TypeElement typeElement) {
        if (typeElement == null || !isReflectTarget(typeElement) || isGeneratedType(typeElement)) {
            return false;
        }
        String packageName = packageNameOf(typeElement);
        return !packageName.startsWith("java.")
                && !packageName.startsWith("javax.")
                && !packageName.startsWith("jakarta.");
    }

    private boolean isReflectTarget(TypeElement typeElement) {
        return typeElement.getKind() == ElementKind.CLASS
                || typeElement.getKind() == ElementKind.RECORD
                || typeElement.getKind() == ElementKind.ENUM;
    }

    private void validateMapperTypeSupport(TypeMirror typeMirror, String usage) {
        if (typeMirror == null || typeMirror.getKind() == TypeKind.VOID || typeMirror.getKind().isPrimitive()) {
            return;
        }
        if (typeMirror instanceof ArrayType arrayType) {
            validateMapperTypeSupport(arrayType.getComponentType(), usage);
            return;
        }
        if (typeMirror instanceof WildcardType wildcardType) {
            validateMapperTypeSupport(wildcardType.getExtendsBound(), usage);
            validateMapperTypeSupport(wildcardType.getSuperBound(), usage);
            return;
        }
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return;
        }
        TypeElement typeElement = asTypeElement(typeMirror);
        if (typeElement == null) {
            return;
        }
        if (isSupportedMapperJavaType(typeElement, declaredType)) {
            for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
                validateMapperTypeSupport(typeArgument, usage);
            }
            return;
        }
        String packageName = packageNameOf(typeElement);
        if (packageName.startsWith("java.") || packageName.startsWith("javax.") || packageName.startsWith("jakarta.")) {
            throw new ProcessorException("Unsupported mapper " + usage + " type: " + typeElement.getQualifiedName());
        }
    }

    private boolean isSupportedMapperJavaType(TypeElement typeElement, DeclaredType declaredType) {
        String qualifiedName = typeElement.getQualifiedName().toString();
        if (qualifiedName.equals("java.lang.String")
                || qualifiedName.equals("java.lang.Boolean")
                || qualifiedName.equals("java.lang.Byte")
                || qualifiedName.equals("java.lang.Short")
                || qualifiedName.equals("java.lang.Integer")
                || qualifiedName.equals("java.lang.Long")
                || qualifiedName.equals("java.lang.Float")
                || qualifiedName.equals("java.lang.Double")
                || qualifiedName.equals("java.lang.Character")
                || qualifiedName.equals("java.lang.Object")
                || qualifiedName.equals("java.math.BigDecimal")
                || qualifiedName.equals("java.math.BigInteger")
                || qualifiedName.equals("java.util.UUID")
                || qualifiedName.equals("java.time.LocalDate")
                || qualifiedName.equals("java.time.LocalDateTime")
                || qualifiedName.equals("java.time.LocalTime")
                || qualifiedName.equals("java.time.Instant")
                || qualifiedName.equals("java.time.OffsetDateTime")
                || qualifiedName.equals("java.time.OffsetTime")
                || qualifiedName.equals("java.time.ZonedDateTime")) {
            return true;
        }
        TypeElement mapType = elements.getTypeElement("java.util.Map");
        if (mapType != null && types.isAssignable(types.erasure(declaredType), types.erasure(mapType.asType()))) {
            return true;
        }
        TypeElement collectionType = elements.getTypeElement("java.util.Collection");
        if (collectionType != null && types.isAssignable(types.erasure(declaredType), types.erasure(collectionType.asType()))) {
            return true;
        }
        return typeElement.getKind() == ElementKind.ENUM;
    }

    private String mapperTypeContext(ExecutableElement method, String position) {
        TypeElement owner = (TypeElement) method.getEnclosingElement();
        return position + " type for mapper method " + owner.getQualifiedName() + "." + method.getSimpleName();
    }

    private TypeElement asTypeElement(TypeMirror typeMirror) {
        if (typeMirror == null || typeMirror.getKind().isPrimitive()) {
            return null;
        }
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return null;
        }
        Element element = declaredType.asElement();
        return element instanceof TypeElement ? (TypeElement) element : null;
    }

    private List<MapperMethodSpec> mapperMethods(TypeElement mapperType, MapperXmlDefinition xmlDefinition) {
        List<MapperMethodSpec> methods = new ArrayList<>();
        for (Element enclosedElement : mapperType.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosedElement;
            SqlNodeDefinition statement = xmlDefinition.getStatements().get(method.getSimpleName().toString());
            if (statement == null) {
                throw new ProcessorException("No xml statement found for method: " + method.getSimpleName());
            }
            methods.add(new MapperMethodSpec(method, statement));
        }
        return methods;
    }

    private List<MapperCapabilityDelegateSpec> mapperCapabilityDelegates(TypeElement mapperType, List<MapperMethodSpec> mapperMethods) {
        Map<String, MapperMethodSpec> mapperMethodSignatures = new LinkedHashMap<>();
        for (MapperMethodSpec mapperMethod : mapperMethods) {
            mapperMethodSignatures.put(methodSignature(mapperMethod.method()), mapperMethod);
        }
        List<MapperCapabilityDelegateSpec> delegates = new ArrayList<>();
        Set<String> delegatedContracts = new HashSet<>();
        Set<String> delegatedMethodSignatures = new HashSet<>(mapperMethodSignatures.keySet());
        Set<String> usedFieldNames = new HashSet<>();
        collectMapperCapabilityDelegates(mapperType.asType(), delegates, delegatedContracts, delegatedMethodSignatures, usedFieldNames);
        return delegates;
    }

    private void collectMapperCapabilityDelegates(TypeMirror typeMirror,
                                                  List<MapperCapabilityDelegateSpec> delegates,
                                                  Set<String> delegatedContracts,
                                                  Set<String> delegatedMethodSignatures,
                                                  Set<String> usedFieldNames) {
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return;
        }
        for (TypeMirror interfaceTypeMirror : types.directSupertypes(declaredType)) {
            if (!(interfaceTypeMirror instanceof DeclaredType interfaceType)) {
                continue;
            }
            TypeElement interfaceElement = asTypeElement(interfaceType);
            if (interfaceElement == null || interfaceElement.getKind() != ElementKind.INTERFACE) {
                continue;
            }
            MapperCapabilitySpec capabilitySpec = mapperCapabilitySpecs.get(interfaceElement.getQualifiedName().toString());
            if (capabilitySpec != null && delegatedContracts.add(interfaceElement.getQualifiedName().toString())) {
                List<MapperCapabilityMethodSpec> methods = new ArrayList<>();
                for (Element enclosedElement : interfaceElement.getEnclosedElements()) {
                    if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
                        continue;
                    }
                    ExecutableElement method = (ExecutableElement) enclosedElement;
                    if (method.getModifiers().contains(Modifier.STATIC)) {
                        continue;
                    }
                    String signature = methodSignature(method);
                    if (!delegatedMethodSignatures.add(signature)) {
                        continue;
                    }
                    ExecutableType resolvedMethodType = (ExecutableType) types.asMemberOf(interfaceType, method);
                    methods.add(new MapperCapabilityMethodSpec(
                            method.getSimpleName().toString(),
                            types.erasure(resolvedMethodType.getReturnType()).toString(),
                            resolvedMethodType.getParameterTypes().stream().map(parameterType -> types.erasure(parameterType).toString()).toList(),
                            method.getParameters().stream().map(parameter -> parameter.getSimpleName().toString()).toList(),
                            types.erasure(method.getReturnType()).toString(),
                            method.getParameters().stream().map(parameter -> types.erasure(parameter.asType()).toString()).toList()
                    ));
                }
                if (!methods.isEmpty()) {
                    String fieldName = uniqueCapabilityFieldName(interfaceElement.getSimpleName().toString(), usedFieldNames);
                    TypeElement capabilityEntityType = capabilityEntityTypeElement(interfaceType);
                    delegates.add(new MapperCapabilityDelegateSpec(
                            fieldName,
                            interfaceType.toString(),
                            types.erasure(interfaceType).toString(),
                            renderCapabilityInstantiation(capabilitySpec.implType(), interfaceType),
                            capabilitySpec.implType().getQualifiedName().toString(),
                            capabilityConstructorMode(capabilitySpec.implType()),
                            capabilityEntityType == null ? null : capabilityEntityType.getQualifiedName().toString(),
                            capabilityEntityType == null ? null : tableConstantReference(capabilityEntityType).substring(0, tableConstantReference(capabilityEntityType).lastIndexOf('.')),
                            methods
                    ));
                }
            }
            collectMapperCapabilityDelegates(interfaceType, delegates, delegatedContracts, delegatedMethodSignatures, usedFieldNames);
        }
    }

    private boolean hasMapperCapability(TypeElement mapperType) {
        String qualifiedName = mapperType.getQualifiedName().toString();
        Boolean cached = mapperCapabilityPresenceCache.get(qualifiedName);
        if (cached != null) {
            return cached;
        }
        boolean result = hasMapperCapability(mapperType.asType(), new HashSet<>());
        mapperCapabilityPresenceCache.put(qualifiedName, result);
        return result;
    }

    private boolean hasMapperCapability(TypeMirror typeMirror, Set<String> visiting) {
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return false;
        }
        TypeElement currentType = asTypeElement(typeMirror);
        if (currentType != null) {
            String qualifiedName = currentType.getQualifiedName().toString();
            Boolean cached = mapperCapabilityPresenceCache.get(qualifiedName);
            if (cached != null) {
                return cached;
            }
            if (!visiting.add(qualifiedName)) {
                return false;
            }
        }
        for (TypeMirror interfaceTypeMirror : types.directSupertypes(declaredType)) {
            if (!(interfaceTypeMirror instanceof DeclaredType interfaceType)) {
                continue;
            }
            TypeElement interfaceElement = asTypeElement(interfaceType);
            if (interfaceElement == null || interfaceElement.getKind() != ElementKind.INTERFACE) {
                continue;
            }
            if (mapperCapabilitySpecs.containsKey(interfaceElement.getQualifiedName().toString())) {
                if (currentType != null) {
                    mapperCapabilityPresenceCache.put(currentType.getQualifiedName().toString(), true);
                    visiting.remove(currentType.getQualifiedName().toString());
                }
                return true;
            }
            if (hasMapperCapability(interfaceType, visiting)) {
                if (currentType != null) {
                    mapperCapabilityPresenceCache.put(currentType.getQualifiedName().toString(), true);
                    visiting.remove(currentType.getQualifiedName().toString());
                }
                return true;
            }
        }
        if (currentType != null) {
            mapperCapabilityPresenceCache.put(currentType.getQualifiedName().toString(), false);
            visiting.remove(currentType.getQualifiedName().toString());
        }
        return false;
    }

    private List<TypeElement> mapperCapabilityEntityTypes(TypeElement mapperType) {
        String qualifiedName = mapperType.getQualifiedName().toString();
        List<TypeElement> cached = mapperCapabilityEntityTypesCache.get(qualifiedName);
        if (cached != null) {
            return cached;
        }
        Map<String, TypeElement> entityTypes = new LinkedHashMap<>();
        collectMapperEntityTypes(mapperType.asType(), entityTypes, new HashSet<>());
        List<TypeElement> result = new ArrayList<>(entityTypes.values());
        mapperCapabilityEntityTypesCache.put(qualifiedName, result);
        return result;
    }

    private void collectMapperEntityTypes(TypeMirror typeMirror, Map<String, TypeElement> entityTypes, Set<String> visiting) {
        if (!(typeMirror instanceof DeclaredType declaredType)) {
            return;
        }
        TypeElement currentType = asTypeElement(typeMirror);
        if (currentType != null && !visiting.add(currentType.getQualifiedName().toString())) {
            return;
        }
        for (TypeMirror interfaceTypeMirror : types.directSupertypes(declaredType)) {
            if (!(interfaceTypeMirror instanceof DeclaredType interfaceType)) {
                continue;
            }
            TypeElement interfaceElement = asTypeElement(interfaceType);
            if (interfaceElement == null || interfaceElement.getKind() != ElementKind.INTERFACE) {
                continue;
            }
            if (mapperCapabilitySpecs.containsKey(interfaceElement.getQualifiedName().toString())) {
                TypeElement entityTypeElement = capabilityEntityTypeElement(interfaceType);
                if (entityTypeElement != null) {
                    entityTypes.putIfAbsent(entityTypeElement.getQualifiedName().toString(), entityTypeElement);
                }
            }
            collectMapperEntityTypes(interfaceType, entityTypes, visiting);
        }
        if (currentType != null) {
            visiting.remove(currentType.getQualifiedName().toString());
        }
    }

    private boolean hasDeclaredAbstractMethods(TypeElement mapperType) {
        for (Element enclosedElement : mapperType.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.METHOD || enclosedElement.getModifiers().contains(Modifier.DEFAULT)) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosedElement;
            if (!method.getModifiers().contains(Modifier.STATIC)) {
                return true;
            }
        }
        return false;
    }

    private String renderCapabilityInstantiation(TypeElement implType, DeclaredType interfaceType) {
        CapabilityConstructorMode constructorMode = capabilityConstructorMode(implType);
        String implTypeName = implType.getQualifiedName().toString();
        if (constructorMode == CapabilityConstructorMode.SQL_EXECUTOR) {
            return "new " + implTypeName + "(sqlExecutor)";
        }
        TypeElement entityTypeElement = capabilityEntityTypeElement(interfaceType);
        if (constructorMode == CapabilityConstructorMode.SQL_EXECUTOR_AND_ENTITY_CLASS) {
            String entityClassLiteral = entityTypeElement == null ? "null" : entityTypeElement.getQualifiedName() + ".class";
            return "new " + implTypeName + "(sqlExecutor, " + entityClassLiteral + ")";
        }
        String entityTableLiteral = entityTypeElement == null ? "null" : tableConstantReference(entityTypeElement);
        return "new " + implTypeName + "(sqlExecutor, " + entityTableLiteral + ")";
    }

    private CapabilityConstructorMode capabilityConstructorMode(TypeElement implType) {
        for (Element enclosedElement : implType.getEnclosedElements()) {
            if (enclosedElement.getKind() != ElementKind.CONSTRUCTOR) {
                continue;
            }
            ExecutableElement constructor = (ExecutableElement) enclosedElement;
            List<? extends VariableElement> parameters = constructor.getParameters();
            if (parameters.size() == 1 && parameters.getFirst().asType().toString().equals(SQL_EXECUTOR)) {
                return CapabilityConstructorMode.SQL_EXECUTOR;
            }
            if (parameters.size() == 2
                    && parameters.getFirst().asType().toString().equals(SQL_EXECUTOR)
                    && types.erasure(parameters.get(1).asType()).toString().equals("java.lang.Class")) {
                return CapabilityConstructorMode.SQL_EXECUTOR_AND_ENTITY_CLASS;
            }
            if (parameters.size() == 2
                    && parameters.getFirst().asType().toString().equals(SQL_EXECUTOR)
                    && types.erasure(parameters.get(1).asType()).toString().equals("org.byteora.kyra.orm.query.EntityTable")) {
                return CapabilityConstructorMode.SQL_EXECUTOR_AND_ENTITY_TABLE;
            }
        }
        throw new ProcessorException("Mapper capability impl must declare constructor (SqlExecutor), (SqlExecutor, Class<?>) or (SqlExecutor, EntityTable<?>): " + implType.getQualifiedName());
    }

    private TypeElement capabilityEntityTypeElement(DeclaredType interfaceType) {
        if (interfaceType.getTypeArguments().isEmpty()) {
            return null;
        }
        TypeMirror entityType = interfaceType.getTypeArguments().getFirst();
        if (entityType.getKind() == TypeKind.TYPEVAR || entityType.getKind() == TypeKind.WILDCARD) {
            return null;
        }
        return asTypeElement(entityType);
    }

    private String tableConstantReference(TypeElement entityType) {
        return generatedModelPackageName(entityType) + "." + tableSimpleName(entityType) + ".TABLE";
    }

    private TypeElement mapperCapabilityContractType(TypeElement implType) {
        for (AnnotationMirror annotationMirror : implType.getAnnotationMirrors()) {
            if (!annotationMirror.getAnnotationType().toString().equals(MapperCapability.class.getCanonicalName())) {
                continue;
            }
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror.getElementValues().entrySet()) {
                if (entry.getKey().getSimpleName().contentEquals("value") && entry.getValue().getValue() instanceof TypeMirror typeMirror) {
                    return asTypeElement(typeMirror);
                }
            }
        }
        return null;
    }

    private String methodSignature(ExecutableElement method) {
        StringBuilder signature = new StringBuilder(method.getSimpleName()).append('(');
        List<? extends VariableElement> parameters = method.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                signature.append(',');
            }
            signature.append(types.erasure(parameters.get(i).asType()));
        }
        signature.append(')');
        return signature.toString();
    }

    private String uniqueCapabilityFieldName(String simpleName, Set<String> usedFieldNames) {
        String baseName = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
        String candidate = baseName;
        int index = 2;
        while (!usedFieldNames.add(candidate)) {
            candidate = baseName + index++;
        }
        return candidate;
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

    private List<VariableElement> collectTableFields(TypeElement entityType) {
        Map<String, VariableElement> fields = new LinkedHashMap<>();
        collectTableFields(entityType, fields);
        return new ArrayList<>(fields.values());
    }

    private VariableElement resolveIdField(TypeElement entityType, List<VariableElement> fields) {
        List<VariableElement> annotatedFields = fields.stream()
                .filter(field -> hasAnnotation(field, ID_ANNOTATION))
                .toList();
        if (annotatedFields.size() > 1) {
            throw new ProcessorException("Multiple @ID fields found on entity: " + entityType.getQualifiedName());
        }
        if (!annotatedFields.isEmpty()) {
            return annotatedFields.getFirst();
        }
        return fields.stream()
                .filter(field -> field.getSimpleName().contentEquals("id"))
                .findFirst()
                .orElse(null);
    }

    private IdGenerationSpec resolveIdGeneration(TypeElement entityType, VariableElement idField) {
        if (idField == null) {
            return new IdGenerationSpec("NONE", null);
        }
        AnnotationMirror idAnnotation = annotationMirror(idField, ID_ANNOTATION);
        if (idAnnotation == null) {
            return new IdGenerationSpec("NONE", null);
        }
        String strategy = annotationEnumValue(idAnnotation, "strategy");
        return switch (strategy) {
            case "NONE" -> new IdGenerationSpec("NONE", null);
            case "UUID" -> {
                validateUuidIdType(entityType, idField);
                yield new IdGenerationSpec("UUID", null);
            }
            case "CUSTOM" -> new IdGenerationSpec("CUSTOM", customIdGeneratorInstantiation(entityType, idField, idAnnotation));
            default -> throw new ProcessorException("Unsupported @ID strategy " + strategy + " on entity: " + entityType.getQualifiedName());
        };
    }

    private void validateUuidIdType(TypeElement entityType, VariableElement idField) {
        String typeName = renderRuntimeCastType(idField.asType());
        if (!typeName.equals(String.class.getCanonicalName()) && !typeName.equals("java.util.UUID")) {
            throw new ProcessorException("@ID(strategy = UUID) only supports String or UUID fields: "
                    + entityType.getQualifiedName() + "." + idField.getSimpleName());
        }
    }

    private String customIdGeneratorInstantiation(TypeElement entityType, VariableElement idField, AnnotationMirror idAnnotation) {
        TypeMirror generatorType = annotationClassValue(idAnnotation, "generator");
        if (generatorType == null || generatorType.toString().equals(ID_GENERATOR_TYPE)) {
            return null;
        }
        TypeElement generatorElement = (TypeElement) types.asElement(generatorType);
        if (generatorElement == null) {
            throw new ProcessorException("Invalid id generator type on entity: "
                    + entityType.getQualifiedName() + "." + idField.getSimpleName());
        }
        TypeElement idGeneratorType = elements.getTypeElement(ID_GENERATOR_TYPE);
        if (idGeneratorType == null || !types.isAssignable(types.erasure(generatorType), types.erasure(idGeneratorType.asType()))) {
            throw new ProcessorException("Id generator must implement " + ID_GENERATOR_TYPE + ": " + generatorType);
        }
        if (generatorElement.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new ProcessorException("Id generator must be concrete: " + generatorType);
        }
        List<ExecutableElement> constructors = generatorElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.CONSTRUCTOR)
                .map(ExecutableElement.class::cast)
                .toList();
        boolean hasAccessibleNoArgsConstructor = (constructors.isEmpty() && generatorElement.getModifiers().contains(Modifier.PUBLIC)) || constructors.stream()
                .anyMatch(constructor -> constructor.getParameters().isEmpty() && constructor.getModifiers().contains(Modifier.PUBLIC));
        if (!hasAccessibleNoArgsConstructor) {
            throw new ProcessorException("Id generator must declare a public no-arg constructor: " + generatorType);
        }
        return "new " + generatorType + "()";
    }

    private void collectTableFields(TypeElement entityType, Map<String, VariableElement> fields) {
        TypeElement superType = directSuperType(entityType);
        if (superType != null && !isJavaLangObject(superType)) {
            collectTableFields(superType, fields);
        }
        for (VariableElement field : collectInstanceFields(entityType)) {
            fields.putIfAbsent(field.getSimpleName().toString(), field);
        }
    }

    private boolean isPagingType(TypeMirror typeMirror) {
        TypeElement pagingType = elements.getTypeElement(PAGING_TYPE);
        return pagingType != null && types.isAssignable(types.erasure(typeMirror), types.erasure(pagingType.asType()));
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
        return annotationMirror(element, annotationType) != null;
    }

    private AnnotationMirror annotationMirror(Element element, String annotationType) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().toString().equals(annotationType)) {
                return annotationMirror;
            }
        }
        return null;
    }

    private String annotationEnumValue(AnnotationMirror annotationMirror, String attribute) {
        ExecutableElement method = annotationMethod(annotationMirror, attribute);
        String value = annotationValue(annotationMirror, method).getValue().toString();
        int separator = value.lastIndexOf('.');
        return separator >= 0 ? value.substring(separator + 1) : value;
    }

    private TypeMirror annotationClassValue(AnnotationMirror annotationMirror, String attribute) {
        ExecutableElement method = annotationMethod(annotationMirror, attribute);
        Object value = annotationValue(annotationMirror, method).getValue();
        return value instanceof TypeMirror typeMirror ? typeMirror : null;
    }

    private ExecutableElement annotationMethod(AnnotationMirror annotationMirror, String attribute) {
        TypeElement annotationType = (TypeElement) annotationMirror.getAnnotationType().asElement();
        for (Element enclosedElement : annotationType.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD && enclosedElement.getSimpleName().contentEquals(attribute)) {
                return (ExecutableElement) enclosedElement;
            }
        }
        throw new ProcessorException("Missing annotation attribute " + attribute + " on " + annotationMirror.getAnnotationType());
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
        return superType == null || superType.getKind() == TypeKind.NONE ? null : asTypeElement(superType);
    }

    private boolean isJavaLangObject(TypeElement typeElement) {
        return typeElement != null && typeElement.getQualifiedName().contentEquals(Object.class.getCanonicalName());
    }

    private String renderSqlNode(DynamicSqlNode node) {
        if (node instanceof TextSqlNode textSqlNode) {
            return "org.byteora.kyra.orm.dynamic.SqlNodes.text(\"" + escapeJava(textSqlNode.getText()) + "\")";
        }
        if (node instanceof MixedSqlNode mixedSqlNode) {
            String children = mixedSqlNode.getChildren().stream().map(this::renderSqlNode).collect(Collectors.joining(", "));
            return "org.byteora.kyra.orm.dynamic.SqlNodes.mixed(" + children + ")";
        }
        if (node instanceof IfSqlNode ifSqlNode) {
            return "org.byteora.kyra.orm.dynamic.SqlNodes.ifNode(\"" + escapeJava(ifSqlNode.getTest()) + "\", " + renderSqlNode(ifSqlNode.getContents()) + ")";
        }
        if (node instanceof TrimSqlNode trimSqlNode) {
            return "org.byteora.kyra.orm.dynamic.SqlNodes.trim(" + renderNullableString(trimSqlNode.getPrefix()) + ", "
                    + renderNullableString(trimSqlNode.getSuffix()) + ", "
                    + renderStringArray(trimSqlNode.getPrefixOverrides()) + ", "
                    + renderStringArray(trimSqlNode.getSuffixOverrides()) + ", "
                    + renderSqlNode(trimSqlNode.getContents()) + ")";
        }
        if (node instanceof ForEachSqlNode forEachSqlNode) {
            return "org.byteora.kyra.orm.dynamic.SqlNodes.foreach(\"" + escapeJava(forEachSqlNode.getCollection()) + "\", "
                    + renderNullableString(forEachSqlNode.getItem()) + ", "
                    + renderNullableString(forEachSqlNode.getIndex()) + ", "
                    + renderNullableString(forEachSqlNode.getOpen()) + ", "
                    + renderNullableString(forEachSqlNode.getClose()) + ", "
                    + renderNullableString(forEachSqlNode.getSeparator()) + ", "
                    + renderSqlNode(forEachSqlNode.getContents()) + ")";
        }
        if (node instanceof ChooseSqlNode chooseSqlNode) {
            String whenNodes = chooseSqlNode.getWhenNodes().stream().map(this::renderWhenNode).collect(Collectors.joining(", "));
            return "org.byteora.kyra.orm.dynamic.SqlNodes.choose(new org.byteora.kyra.orm.dynamic.WhenSqlNode[]{" + whenNodes + "}, "
                    + (chooseSqlNode.getOtherwiseNode() == null ? "null" : renderSqlNode(chooseSqlNode.getOtherwiseNode())) + ")";
        }
        if (node instanceof BindSqlNode bindSqlNode) {
            return "org.byteora.kyra.orm.dynamic.SqlNodes.bind(\"" + escapeJava(bindSqlNode.getName()) + "\", \"" + escapeJava(bindSqlNode.getValueExpression()) + "\")";
        }
        throw new ProcessorException("Unsupported sql node type: " + node.getClass().getName());
    }

    private String renderWhenNode(WhenSqlNode whenSqlNode) {
        return "org.byteora.kyra.orm.dynamic.SqlNodes.when(\"" + escapeJava(whenSqlNode.getTest()) + "\", " + renderSqlNode(whenSqlNode.getContents()) + ")";
    }

    private String renderStringArray(List<String> values) {
        if (values.isEmpty()) {
            return "new String[0]";
        }
        return "new String[]{" + values.stream().map(value -> "\"" + escapeJava(value) + "\"").collect(Collectors.joining(", ")) + "}";
    }

    private String renderNullableString(String value) {
        return value == null ? "null" : "\"" + escapeJava(value) + "\"";
    }

    private String statementFieldName(String statementId) {
        StringBuilder builder = new StringBuilder("SQL_");
        for (int i = 0; i < statementId.length(); i++) {
            char ch = statementId.charAt(i);
            if (Character.isUpperCase(ch) && i > 0 && Character.isLowerCase(statementId.charAt(i - 1))) {
                builder.append('_');
            }
            builder.append(Character.isLetterOrDigit(ch) ? Character.toUpperCase(ch) : '_');
        }
        return builder.toString();
    }

    private String capitalize(String fieldName) {
        return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    private String boxedType(String typeName) {
        return switch (typeName) {
            case "byte" -> "java.lang.Byte";
            case "short" -> "java.lang.Short";
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "float" -> "java.lang.Float";
            case "double" -> "java.lang.Double";
            case "boolean" -> "java.lang.Boolean";
            case "char" -> "java.lang.Character";
            default -> typeName;
        };
    }

    private String renderRuntimeCastType(TypeMirror typeMirror) {
        if (typeMirror == null) {
            return Object.class.getCanonicalName();
        }
        TypeMirror erasedType = types.erasure(typeMirror);
        return boxedType(erasedType.toString());
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
        String packageName = GENERATED_PACKAGE_PREFIX + "." + packageHashSegment(packageNameOf(typeElement));
        Element enclosingElement = typeElement.getEnclosingElement();
        if (enclosingElement instanceof TypeElement enclosingType) {
            packageName += "." + packageHashSegment(enclosingType.getQualifiedName().toString());
        }
        return packageName;
    }

    private String reflectorTypeName(TypeElement entityType, String suffix) {
        return generatedModelPackageName(entityType) + "." + reflectorSimpleName(entityType, suffix);
    }

    private String mapperImplTypeName(TypeElement mapperType) {
        String packageName = packageNameOf(mapperType);
        String simpleName = mapperType.getSimpleName() + "Impl";
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    private String reflectorSimpleName(TypeElement entityType, String suffix) {
        return entityType.getSimpleName() + suffix;
    }

    private String tableSimpleName(TypeElement entityType) {
        return entityType.getSimpleName() + "Table";
    }

    private String packageHashSegment(String packageName) {
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

    private String resolveTableName(TypeElement entityType) {
        String alias = aliasValue(entityType);
        if (alias != null) {
            return alias;
        }
        return toSnakeCase(entityType.getSimpleName().toString());
    }

    private String resolveColumnName(VariableElement field) {
        String alias = aliasValue(field);
        if (alias != null) {
            return alias;
        }
        return toSnakeCase(field.getSimpleName().toString());
    }

    private String aliasValue(Element element) {
        return AliasSupport.aliasValue(element);
    }

    private String toSnakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isUpperCase(current)) {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private String escapeJava(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private void loadPersistedScanSpecsIfNeeded() {
        if (persistedScanSpecsLoaded) {
            return;
        }
        persistedScanSpecsLoaded = true;
        for (ScanSpecIndexStore.ScanConfigMetadata metadata : scanSpecIndexStore.read()) {
            TypeElement configType = elements.getTypeElement(metadata.configQualifiedName());
            ScanSpec scanSpec = new ScanSpec(configType, metadata.configQualifiedName(), mapperXmlRoots, metadata.entityPackages(), metadata.mapperPackages());
            for (String entityTypeName : metadata.entityTypeNames()) {
                if (shouldRetainPersistedEntityTypeName(entityTypeName)) {
                    scanSpec.entityTypeNames.add(entityTypeName);
                } else {
                    scanSpecIndexDirty = true;
                }
            }
            for (String mapperTypeName : metadata.mapperTypeNames()) {
                if (shouldRetainPersistedMapperTypeName(mapperTypeName)) {
                    scanSpec.mapperTypeNames.add(mapperTypeName);
                } else {
                    scanSpecIndexDirty = true;
                }
            }
            upsertScanSpec(scanSpec);
        }
    }

    private void loadPersistedSupportIndexIfNeeded() {
        if (persistedSupportIndexLoaded) {
            return;
        }
        persistedSupportIndexLoaded = true;
        // Retain entries we cannot resolve in this round. Under IDEA JPS
        // incremental compilation only a small subset of sources is in the
        // current round, so elements.getTypeElement(...) may transiently
        // return null for entities defined in unchanged files. Treating that
        // as "invalid" would prune the entry and rewrite a smaller index,
        // permanently losing reflectors/tables and shrinking the installer.
        // Only prune entries we can positively prove are no longer collectable.
        generatedSupportIndexStore.load((entityTypeName, generatedTypeName) -> {
            TypeElement entityType = elements.getTypeElement(entityTypeName);
            return entityType == null || shouldCollectScannedType(entityType);
        });
        debug("loaded generated support index from=" + generatedSupportIndexStore.loadedLocation()
                + ", reflectors=" + generatedSupportIndexStore.reflectors().size()
                + ", tables=" + generatedSupportIndexStore.tables().size()
                + ", skippedEntries=" + generatedSupportIndexStore.skippedEntries()
                + ", dirty=" + generatedSupportIndexStore.isDirty()
                + ", reflectorEntries=" + summarizeReflectors(generatedSupportIndexStore.reflectors()));
    }

    private void persistScanSpecsIfNeeded() {
        if (!scanSpecIndexDirty) {
            return;
        }
        try {
            scanSpecIndexStore.write(
                    scanSpecs.stream().map(ScanSpec::metadata).toList(),
                    scanSpecs.stream().map(ScanSpec::configType).filter(element -> element != null).toList()
            );
            scanSpecIndexDirty = false;
        } catch (IOException ex) {
            messager.printMessage(Diagnostic.Kind.WARNING, "Failed to persist kyra scan metadata: " + ex.getMessage());
        }
    }

    private void persistGeneratedSupportIndexIfNeeded() {
        debug("persist generated support index requested dirty=" + generatedSupportIndexStore.isDirty()
                + ", reflectors=" + generatedSupportIndexStore.reflectors().size()
                + ", tables=" + generatedSupportIndexStore.tables().size());
        try {
            generatedSupportIndexStore.write(generatedSupportIndexOriginatingElements());
            debug("persist generated support index finished dirty=" + generatedSupportIndexStore.isDirty());
        } catch (IOException ex) {
            debug("failed to persist generated support index exception=" + ex.getClass().getName()
                    + ", message=" + ex.getMessage());
            messager.printMessage(Diagnostic.Kind.WARNING, "Failed to persist generated support metadata: " + ex.getMessage());
        }
    }

    private List<Element> generatedSupportIndexOriginatingElements() {
        LinkedHashSet<Element> originating = new LinkedHashSet<>();
        for (ScanSpec scanSpec : scanSpecs) {
            if (scanSpec.configType != null) {
                originating.add(scanSpec.configType);
            }
        }
        for (GeneratedSupportIndexStore.ReflectRegistration reflector : generatedSupportIndexStore.reflectors()) {
            TypeElement entityType = elements.getTypeElement(reflector.entityTypeName());
            if (entityType != null) {
                originating.add(entityType);
            }
        }
        for (GeneratedSupportIndexStore.TableRegistration table : generatedSupportIndexStore.tables()) {
            TypeElement entityType = elements.getTypeElement(table.entityTypeName());
            if (entityType != null) {
                originating.add(entityType);
            }
        }
        return new ArrayList<>(originating);
    }

    private boolean upsertScanSpec(ScanSpec candidate) {
        for (int i = 0; i < scanSpecs.size(); i++) {
            ScanSpec existing = scanSpecs.get(i);
            if (!existing.configQualifiedName.equals(candidate.configQualifiedName)) {
                continue;
            }
            if (existing.sameConfiguration(candidate)) {
                return false;
            }
            scanSpecs.set(i, candidate);
            return true;
        }
        scanSpecs.add(candidate);
        return true;
    }

    private void printScanSpecMessage(Diagnostic.Kind kind, String message, ScanSpec scanSpec) {
        if (scanSpec.configType != null) {
            messager.printMessage(kind, message, scanSpec.configType);
            return;
        }
        messager.printMessage(kind, message + " [" + scanSpec.configQualifiedName + "]");
    }

    record MapperMethodSpec(ExecutableElement method, SqlNodeDefinition statement) {
    }

    record IdGenerationSpec(String strategy, String generatorInstantiation) {
    }

    private record MapperCapabilitySpec(TypeElement contractType, TypeElement implType) {
    }

    record MapperCapabilityDelegateSpec(
            String fieldName,
            String interfaceTypeLiteral,
            String interfaceErasedTypeLiteral,
            String instantiation,
            String implTypeName,
            CapabilityConstructorMode constructorMode,
            String entityTypeName,
            String tableTypeName,
            List<MapperCapabilityMethodSpec> methods
    ) {
    }

    record MapperCapabilityMethodSpec(
            String methodName,
            String returnType,
            List<String> parameterTypes,
            List<String> parameterNames,
            String bridgeReturnType,
            List<String> bridgeParameterTypes
    ) {
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    enum CapabilityConstructorMode {
        SQL_EXECUTOR,
        SQL_EXECUTOR_AND_ENTITY_CLASS,
        SQL_EXECUTOR_AND_ENTITY_TABLE
    }

    static final class ScanSpec {
        private final TypeElement configType;
        private final String configQualifiedName;
        private final List<String> xmlRoots;
        private final List<String> entityPackages;
        private final List<String> mapperPackages;
        private final Set<String> entityTypeNames = new LinkedHashSet<>();
        private final Set<String> mapperTypeNames = new LinkedHashSet<>();
        private Map<String, MapperXmlDefinition> xmlDefinitions;

        private ScanSpec(TypeElement configType, List<String> xmlRoots, List<String> entityPackages, List<String> mapperPackages) {
            this(configType, configType.getQualifiedName().toString(), xmlRoots, entityPackages, mapperPackages);
        }

        private ScanSpec(TypeElement configType, String configQualifiedName, List<String> xmlRoots, List<String> entityPackages, List<String> mapperPackages) {
            this.configType = configType;
            this.configQualifiedName = configQualifiedName;
            this.xmlRoots = xmlRoots.stream().filter(value -> !value.isBlank()).map(String::trim).toList();
            this.entityPackages = entityPackages.stream().filter(value -> !value.isBlank()).map(String::trim).toList();
            this.mapperPackages = mapperPackages.stream().filter(value -> !value.isBlank()).map(String::trim).toList();
        }

        TypeElement configType() {
            return configType;
        }

        boolean sameConfiguration(ScanSpec other) {
            return configQualifiedName.equals(other.configQualifiedName)
                    && entityPackages.equals(other.entityPackages)
                    && mapperPackages.equals(other.mapperPackages)
                    && xmlRoots.equals(other.xmlRoots);
        }

        ScanSpecIndexStore.ScanConfigMetadata metadata() {
            return new ScanSpecIndexStore.ScanConfigMetadata(
                    configQualifiedName,
                    entityPackages,
                    mapperPackages,
                    new ArrayList<>(entityTypeNames),
                    new ArrayList<>(mapperTypeNames)
            );
        }

    }

    static final class ProcessorException extends RuntimeException {
        ProcessorException(String message) {
            super(message);
        }

        ProcessorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
