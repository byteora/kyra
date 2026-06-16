package org.byteora.kyra.processor;

import org.byteora.kyra.core.annotation.ReflectMetadataLevel;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReflectorClassGenerator {
    private static final String REFLECTOR = "org/byteora/kyra/core/runtime/Reflector";
    private static final String REFLECTOR_DESC = "Lorg/byteora/kyra/core/runtime/Reflector;";
    private static final String REFLECTOR_REGISTRY = "org/byteora/kyra/core/runtime/ReflectorRegistry";
    private static final String CLASS_INFO = "org/byteora/kyra/core/runtime/ClassInfo";
    private static final String FIELD_INFO = "org/byteora/kyra/core/runtime/FieldInfo";
    private static final String METHOD_INFO = "org/byteora/kyra/core/runtime/MethodInfo";
    private static final String PARAMETER_INFO = "org/byteora/kyra/core/runtime/ParameterInfo";
    private static final String ANNOTATION_META = "org/byteora/kyra/core/runtime/AnnotationMeta";
    private static final String RUNTIME_TYPES = "org/byteora/kyra/core/runtime/RuntimeTypes";
    private static final String CLASS_INFO_DESC = "Lorg/byteora/kyra/core/runtime/ClassInfo;";
    private static final String FIELD_INFO_DESC = "Lorg/byteora/kyra/core/runtime/FieldInfo;";
    private static final String METHOD_INFO_DESC = "Lorg/byteora/kyra/core/runtime/MethodInfo;";
    private static final String PARAMETER_INFO_DESC = "Lorg/byteora/kyra/core/runtime/ParameterInfo;";
    private static final String ANNOTATION_META_DESC = "Lorg/byteora/kyra/core/runtime/AnnotationMeta;";
    private static final String TYPE_DESC = "Ljava/lang/reflect/Type;";

    private final Context context;

    public ReflectorClassGenerator(Context context) {
        this.context = context;
    }

    public byte[] buildReflectorClass(String generatedQualifiedName, TypeElement entityType) {
        String classInternalName = AsmUtils.internalName(generatedQualifiedName);
        String entityInternalName = AsmUtils.internalName(entityType);
        ReflectMetadataLevel metadataLevel = metadataLevel(entityType);
        boolean includeFieldsMetadata = metadataLevel.includesFields();
        boolean includeMethodsMetadata = metadataLevel.includesMethods();
        boolean includeAnnotationMetadata = annotationMetadata(entityType);
        List<VariableElement> fields = context.collectInstanceFields(entityType);
        List<String> expandedFieldNames = context.expandedFieldNames(entityType);
        List<ExecutableElement> methods = includeMethodsMetadata ? context.collectInvokableMethods(entityType) : List.of();
        Map<String, List<ExecutableElement>> methodsByName = groupMethodsByName(methods);
        List<String> expandedMethodNames = includeMethodsMetadata ? expandedMethodNames(entityType) : List.of();
        TypeElement parentReflectType = resolveParentReflectType(entityType);
        String parentReflectTypeName = parentReflectType == null ? null : AsmUtils.internalName(parentReflectType);
        ExecutableElement preferredConstructor = findPreferredConstructor(entityType);
        ExecutableElement fullArgsConstructor = findFullArgsConstructor(entityType);
        boolean hasImplicitDefaultConstructor = constructors(entityType).isEmpty() && !entityType.getModifiers().contains(Modifier.ABSTRACT);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, classInternalName, null, "java/lang/Object",
                new String[]{REFLECTOR});

        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE, "classInfo", CLASS_INFO_DESC, null, null).visitEnd();
        if (includeFieldsMetadata) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "FIELD_NAMES", "[Ljava/lang/String;", null, null).visitEnd();
        }
        if (includeMethodsMetadata) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "METHOD_NAMES", "[Ljava/lang/String;", null, null).visitEnd();
        }
        if (parentReflectTypeName != null) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE, "parentReflector", REFLECTOR_DESC, null, null).visitEnd();
        }

        writeClinit(cw, classInternalName, includeFieldsMetadata, expandedFieldNames, includeMethodsMetadata, expandedMethodNames);
        writeNoArgConstructor(cw);
        if (parentReflectTypeName != null) {
            writeParentReflectorAccessor(cw, classInternalName, parentReflectTypeName);
        }
        if (includeMethodsMetadata && parentReflectTypeName != null) {
            writeMergeMethodInfos(cw);
        }
        writeNewInstance(cw, entityType, entityInternalName, preferredConstructor, hasImplicitDefaultConstructor);
        writeNewInstanceWithArgs(cw, entityType, entityInternalName, classInternalName, fullArgsConstructor, hasImplicitDefaultConstructor);
        writeGetClassInfo(cw, classInternalName, entityType, includeAnnotationMetadata, fullArgsConstructor);
        writeInvoke(cw, classInternalName, entityType, entityInternalName, methodsByName, expandedMethodNames, parentReflectTypeName);
        writeSetByIndex(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName);
        writeGet(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName);
        writePrimitiveAccessors(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName);
        writeGetFields(cw, classInternalName, includeFieldsMetadata, parentReflectTypeName);
        writeFieldIndex(cw, classInternalName, expandedFieldNames, includeFieldsMetadata, parentReflectTypeName);
        writeGetField(cw, classInternalName, entityType, fields, expandedFieldNames, includeFieldsMetadata, includeAnnotationMetadata, parentReflectTypeName);
        writeGetMethods(cw, classInternalName, includeMethodsMetadata, expandedMethodNames, parentReflectTypeName);
        writeMethodIndex(cw, classInternalName, expandedMethodNames, includeMethodsMetadata, parentReflectTypeName);
        writeGetMethod(cw, classInternalName, methodsByName, expandedMethodNames, includeMethodsMetadata, includeAnnotationMetadata, parentReflectTypeName);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private ReflectMetadataLevel metadataLevel(TypeElement entityType) {
        ReflectSpec reflectSpec = context.reflectSpec(entityType);
        return reflectSpec == null ? ReflectMetadataLevel.BASIC : reflectSpec.metadataLevel();
    }

    private boolean annotationMetadata(TypeElement entityType) {
        ReflectSpec reflectSpec = context.reflectSpec(entityType);
        return reflectSpec != null && reflectSpec.annotationMetadata();
    }

    private TypeElement resolveParentReflectType(TypeElement entityType) {
        TypeElement declaredSuperType = context.directSuperType(entityType);
        if (declaredSuperType == null || context.isJavaLangObject(declaredSuperType)) {
            return null;
        }
        return context.hasReflectSpec(declaredSuperType) ? declaredSuperType : null;
    }

    private void writeClinit(ClassWriter cw,
                             String classInternalName,
                             boolean includeFieldsMetadata,
                             List<String> fieldNames,
                             boolean includeMethodsMetadata,
                             List<String> methodNames) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        if (includeFieldsMetadata) {
            if (fieldNames.isEmpty()) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, REFLECTOR, "NO_FIELDS", "[Ljava/lang/String;");
            } else {
                AsmUtils.pushStringArray(mv, fieldNames);
            }
            mv.visitFieldInsn(Opcodes.PUTSTATIC, classInternalName, "FIELD_NAMES", "[Ljava/lang/String;");
        }
        if (includeMethodsMetadata) {
            if (methodNames.isEmpty()) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, REFLECTOR, "NO_METHOD_NAMES", "[Ljava/lang/String;");
            } else {
                AsmUtils.pushStringArray(mv, methodNames);
            }
            mv.visitFieldInsn(Opcodes.PUTSTATIC, classInternalName, "METHOD_NAMES", "[Ljava/lang/String;");
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeNoArgConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeParentReflectorAccessor(ClassWriter cw, String classInternalName, String parentReflectTypeName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "parentReflector", "()" + REFLECTOR_DESC, null, null);
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, classInternalName, "parentReflector", REFLECTOR_DESC);
        mv.visitInsn(Opcodes.DUP);
        Label cached = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, cached);
        mv.visitInsn(Opcodes.POP);
        mv.visitLdcInsn(org.objectweb.asm.Type.getObjectType(parentReflectTypeName));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, REFLECTOR_REGISTRY, "get", "(Ljava/lang/Class;)Lorg/byteora/kyra/core/runtime/Reflector;", false);
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, classInternalName, "parentReflector", REFLECTOR_DESC);
        mv.visitLabel(cached);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeMergeMethodNames(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "mergeMethodNames", "([Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;", null, null);
        mv.visitCode();
        Label currentNonEmpty = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitJumpInsn(Opcodes.IFNE, currentNonEmpty);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "[Ljava/lang/String;", "clone", "()Ljava/lang/Object;", false);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "[Ljava/lang/String;");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(currentNonEmpty);
        Label inheritedNonEmpty = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitJumpInsn(Opcodes.IFNE, inheritedNonEmpty);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "[Ljava/lang/String;", "clone", "()Ljava/lang/Object;", false);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "[Ljava/lang/String;");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(inheritedNonEmpty);
        mv.visitTypeInsn(Opcodes.NEW, "java/util/LinkedHashSet");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/LinkedHashSet", "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections", "addAll", "(Ljava/util/Collection;[Ljava/lang/Object;)Z", false);
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections", "addAll", "(Ljava/util/Collection;[Ljava/lang/Object;)Z", false);
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        AsmUtils.pushInt(mv, 0);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Set", "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;", true);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "[Ljava/lang/String;");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeMergeMethodInfos(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "mergeMethodInfos", "([Lorg/byteora/kyra/core/runtime/MethodInfo;[Lorg/byteora/kyra/core/runtime/MethodInfo;)[Lorg/byteora/kyra/core/runtime/MethodInfo;", null, null);
        mv.visitCode();
        Label currentNonEmpty = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitJumpInsn(Opcodes.IFNE, currentNonEmpty);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(currentNonEmpty);
        Label inheritedNonEmpty = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitJumpInsn(Opcodes.IFNE, inheritedNonEmpty);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(inheritedNonEmpty);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitInsn(Opcodes.IADD);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, METHOD_INFO);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        AsmUtils.pushInt(mv, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        AsmUtils.pushInt(mv, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        AsmUtils.pushInt(mv, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeNewInstance(ClassWriter cw, TypeElement entityType, String entityInternalName, ExecutableElement constructor, boolean hasImplicitDefaultConstructor) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "newInstance", "()Ljava/lang/Object;", null, null);
        mv.visitCode();
        if (entityType.getModifiers().contains(Modifier.ABSTRACT) || (constructor == null && !hasImplicitDefaultConstructor)) {
            emitUnsupported(mv, "Type cannot be instantiated without accessible constructor: " + entityType.getQualifiedName());
        } else {
            mv.visitTypeInsn(Opcodes.NEW, entityInternalName);
            mv.visitInsn(Opcodes.DUP);
            List<? extends VariableElement> parameters = constructor == null ? List.of() : constructor.getParameters();
            for (VariableElement parameter : parameters) {
                emitDefaultValue(mv, parameter.asType());
            }
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, entityInternalName, "<init>",
                    AsmUtils.methodDescriptor(context.voidType(), parameters.stream().map(VariableElement::asType).toList(), context.types()), false);
            mv.visitInsn(Opcodes.ARETURN);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeNewInstanceWithArgs(ClassWriter cw, TypeElement entityType, String entityInternalName, String classInternalName, ExecutableElement constructor, boolean hasImplicitDefaultConstructor) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "newInstance", "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        if (constructor == null) {
            Label argsNull = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitJumpInsn(Opcodes.IFNULL, argsNull);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitJumpInsn(Opcodes.IFEQ, argsNull);
            emitIllegalArgument(mv, "Expected 0 constructor arguments");
            mv.visitLabel(argsNull);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, classInternalName, "newInstance", "()Ljava/lang/Object;", false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        int parameterCount = constructor.getParameters().size();
        Label notNull = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
        AsmUtils.pushInt(mv, 0);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        mv.visitVarInsn(Opcodes.ASTORE, 1);
        mv.visitLabel(notNull);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        AsmUtils.pushInt(mv, parameterCount);
        Label lengthOk = new Label();
        mv.visitJumpInsn(Opcodes.IF_ICMPEQ, lengthOk);
        emitIllegalArgument(mv, "Unexpected constructor argument count");
        mv.visitLabel(lengthOk);
        mv.visitTypeInsn(Opcodes.NEW, entityInternalName);
        mv.visitInsn(Opcodes.DUP);
        for (int i = 0; i < parameterCount; i++) {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            AsmUtils.pushInt(mv, i);
            mv.visitInsn(Opcodes.AALOAD);
            AsmUtils.castFromObject(mv, constructor.getParameters().get(i).asType(), context.types());
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, entityInternalName, "<init>",
                AsmUtils.methodDescriptor(context.voidType(), constructor.getParameters().stream().map(VariableElement::asType).toList(), context.types()), false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeGetClassInfo(ClassWriter cw, String classInternalName, TypeElement entityType, boolean includeAnnotationMetadata, ExecutableElement constructor) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getClassInfo", "()" + CLASS_INFO_DESC, null, null);
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, classInternalName, "classInfo", CLASS_INFO_DESC);
        mv.visitInsn(Opcodes.DUP);
        Label cached = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, cached);
        mv.visitInsn(Opcodes.POP);
        emitClassInfo(mv, classInternalName, entityType, includeAnnotationMetadata, constructor);
        mv.visitInsn(Opcodes.DUP);
        mv.visitFieldInsn(Opcodes.PUTSTATIC, classInternalName, "classInfo", CLASS_INFO_DESC);
        mv.visitLabel(cached);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitClassInfo(MethodVisitor mv, String classInternalName, TypeElement entityType, boolean includeAnnotationMetadata, ExecutableElement constructor) {
        mv.visitTypeInsn(Opcodes.NEW, CLASS_INFO);
        mv.visitInsn(Opcodes.DUP);
        AsmUtils.pushClassLiteral(mv, entityType.asType(), context.types());
        emitTypeOrNull(mv, entityType.getSuperclass());
        AsmUtils.pushInt(mv, modifierMask(entityType.getModifiers()));
        emitAnnotationArray(mv, entityType.getAnnotationMirrors(), includeAnnotationMetadata, classInternalName);
        emitParameterInfoArray(mv, constructor == null ? List.of() : constructor.getParameters(), includeAnnotationMetadata, classInternalName);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, CLASS_INFO, "<init>", "(Ljava/lang/Class;Ljava/lang/reflect/Type;I[" + ANNOTATION_META_DESC + "[" + PARAMETER_INFO_DESC + ")V", false);
    }

    private void writeInvoke(ClassWriter cw,
                             String classInternalName,
                             TypeElement entityType,
                             String entityInternalName,
                             Map<String, List<ExecutableElement>> methodsByName,
                             List<String> expandedMethodNames,
                             String parentReflectTypeName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "invoke", "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, entityInternalName);
        mv.visitVarInsn(Opcodes.ASTORE, 4);
        if (expandedMethodNames.isEmpty()) {
            emitIllegalArgument(mv, "Unknown method index");
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        Label defaultLabel = new Label();
        Label[] caseLabels = new Label[expandedMethodNames.size()];
        for (int i = 0; i < expandedMethodNames.size(); i++) {
            caseLabels[i] = new Label();
        }
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitTableSwitchInsn(0, expandedMethodNames.size() - 1, defaultLabel, caseLabels);
        for (int i = 0; i < expandedMethodNames.size(); i++) {
            String methodName = expandedMethodNames.get(i);
            mv.visitLabel(caseLabels[i]);
            List<ExecutableElement> localMethods = methodsByName.get(methodName);
            if (localMethods != null && !localMethods.isEmpty()) {
                emitInvokeCase(mv, localMethods.get(0), entityInternalName);
            } else if (parentReflectTypeName != null) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
                mv.visitVarInsn(Opcodes.ASTORE, 5);
                mv.visitVarInsn(Opcodes.ALOAD, 5);
                mv.visitLdcInsn(methodName);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "methodIndex", "(Ljava/lang/String;)I", true);
                mv.visitVarInsn(Opcodes.ISTORE, 6);
                mv.visitVarInsn(Opcodes.ALOAD, 5);
                mv.visitVarInsn(Opcodes.ALOAD, 4);
                mv.visitVarInsn(Opcodes.ILOAD, 6);
                mv.visitVarInsn(Opcodes.ALOAD, 3);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "invoke", "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;", true);
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                emitIllegalArgument(mv, "Unknown method index");
            }
        }
        mv.visitLabel(defaultLabel);
        emitIllegalArgument(mv, "Unknown method index");
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeSet(ClassWriter cw, String classInternalName, TypeElement entityType, String entityInternalName, List<VariableElement> fields, String parentReflectTypeName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "set", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, entityInternalName);
        mv.visitVarInsn(Opcodes.ASTORE, 4);
        AsmUtils.emitStringEqualsDispatch(
                mv,
                2,
                fields.stream().map(field -> field.getSimpleName().toString()).toList(),
                index -> emitSetCase(mv, entityType, entityInternalName, fields.get(index)),
                () -> emitSetDefault(mv, classInternalName, parentReflectTypeName)
        );
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeSetByIndex(ClassWriter cw,
                                 String classInternalName,
                                 TypeElement entityType,
                                 String entityInternalName,
                                 List<VariableElement> fields,
                                 List<String> expandedFieldNames,
                                 String parentReflectTypeName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "set", "(Ljava/lang/Object;ILjava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, entityInternalName);
        mv.visitVarInsn(Opcodes.ASTORE, 4);
        if (expandedFieldNames.isEmpty()) {
            emitIllegalArgument(mv, "Unknown property index");
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        Map<String, VariableElement> localFields = new LinkedHashMap<>();
        for (VariableElement field : fields) {
            localFields.put(field.getSimpleName().toString(), field);
        }
        Label defaultLabel = new Label();
        Label[] caseLabels = new Label[expandedFieldNames.size()];
        for (int i = 0; i < expandedFieldNames.size(); i++) {
            caseLabels[i] = new Label();
        }
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitTableSwitchInsn(0, expandedFieldNames.size() - 1, defaultLabel, caseLabels);
        for (int i = 0; i < expandedFieldNames.size(); i++) {
            mv.visitLabel(caseLabels[i]);
            String fieldName = expandedFieldNames.get(i);
            VariableElement field = localFields.get(fieldName);
            if (field != null) {
                emitSetCase(mv, entityType, entityInternalName, field);
            } else if (parentReflectTypeName != null) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
                mv.visitVarInsn(Opcodes.ASTORE, 5);
                mv.visitVarInsn(Opcodes.ALOAD, 5);
                mv.visitLdcInsn(fieldName);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "fieldIndex", "(Ljava/lang/String;)I", true);
                mv.visitVarInsn(Opcodes.ISTORE, 6);
                mv.visitVarInsn(Opcodes.ALOAD, 5);
                mv.visitVarInsn(Opcodes.ALOAD, 4);
                mv.visitVarInsn(Opcodes.ILOAD, 6);
                mv.visitVarInsn(Opcodes.ALOAD, 3);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "set", "(Ljava/lang/Object;ILjava/lang/Object;)V", true);
                mv.visitInsn(Opcodes.RETURN);
            } else {
                emitIllegalArgument(mv, "Unknown property index");
            }
        }
        mv.visitLabel(defaultLabel);
        emitIllegalArgument(mv, "Unknown property index");
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeGet(ClassWriter cw,
                          String classInternalName,
                          TypeElement entityType,
                          String entityInternalName,
                          List<VariableElement> fields,
                          List<String> expandedFieldNames,
                          String parentReflectTypeName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "get", "(Ljava/lang/Object;I)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, entityInternalName);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        if (expandedFieldNames.isEmpty()) {
            emitIllegalArgument(mv, "Unknown property index");
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        Map<String, VariableElement> localFields = new LinkedHashMap<>();
        for (VariableElement field : fields) {
            localFields.put(field.getSimpleName().toString(), field);
        }
        Label defaultLabel = new Label();
        Label[] caseLabels = new Label[expandedFieldNames.size()];
        for (int i = 0; i < expandedFieldNames.size(); i++) {
            caseLabels[i] = new Label();
        }
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitTableSwitchInsn(0, expandedFieldNames.size() - 1, defaultLabel, caseLabels);
        for (int i = 0; i < expandedFieldNames.size(); i++) {
            String fieldName = expandedFieldNames.get(i);
            mv.visitLabel(caseLabels[i]);
            VariableElement field = localFields.get(fieldName);
            if (field != null) {
                emitGetCase(mv, entityType, entityInternalName, field);
            } else if (parentReflectTypeName != null) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
                mv.visitVarInsn(Opcodes.ASTORE, 4);
                mv.visitVarInsn(Opcodes.ALOAD, 4);
                mv.visitLdcInsn(fieldName);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "fieldIndex", "(Ljava/lang/String;)I", true);
                mv.visitVarInsn(Opcodes.ISTORE, 5);
                mv.visitVarInsn(Opcodes.ALOAD, 4);
                mv.visitVarInsn(Opcodes.ALOAD, 3);
                mv.visitVarInsn(Opcodes.ILOAD, 5);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "get", "(Ljava/lang/Object;I)Ljava/lang/Object;", true);
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                emitIllegalArgument(mv, "Unknown property index");
            }
        }
        mv.visitLabel(defaultLabel);
        emitIllegalArgument(mv, "Unknown property index");
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeGetFields(ClassWriter cw, String classInternalName, boolean includeFieldsMetadata, String parentReflectTypeName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getFields", "()[Ljava/lang/String;", null, null);
        mv.visitCode();
        if (includeFieldsMetadata) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, classInternalName, "FIELD_NAMES", "[Ljava/lang/String;");
            mv.visitInsn(Opcodes.ARETURN);
        } else if (parentReflectTypeName != null) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "getFields", "()[Ljava/lang/String;", true);
            mv.visitInsn(Opcodes.ARETURN);
        } else {
            mv.visitFieldInsn(Opcodes.GETSTATIC, REFLECTOR, "NO_FIELDS", "[Ljava/lang/String;");
            mv.visitInsn(Opcodes.ARETURN);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeFieldIndex(ClassWriter cw, String classInternalName, List<String> expandedFieldNames, boolean includeFieldsMetadata, String parentReflectTypeName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "fieldIndex", "(Ljava/lang/String;)I", null, null);
        mv.visitCode();
        if (!includeFieldsMetadata) {
            emitParentIntOrMinusOne(mv, classInternalName, parentReflectTypeName, "fieldIndex", "(Ljava/lang/String;)I");
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        Label nonNull = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitJumpInsn(Opcodes.IFNONNULL, nonNull);
        AsmUtils.pushInt(mv, -1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(nonNull);
        AsmUtils.emitStringEqualsDispatch(
                mv,
                1,
                expandedFieldNames,
                index -> {
                    AsmUtils.pushInt(mv, index);
                    mv.visitInsn(Opcodes.IRETURN);
                },
                () -> emitParentIntDefault(mv, classInternalName, parentReflectTypeName, "fieldIndex", "(Ljava/lang/String;)I", 1)
        );
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeGetField(ClassWriter cw, String classInternalName, TypeElement entityType, List<VariableElement> fields, List<String> expandedFieldNames, boolean includeFieldsMetadata, boolean includeAnnotationMetadata, String parentReflectTypeName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getField", "(I)" + FIELD_INFO_DESC, null, null);
        mv.visitCode();
        if (!includeFieldsMetadata) {
            emitParentObjectOrNullByIndex(mv, classInternalName, parentReflectTypeName, "getField", "(I)" + FIELD_INFO_DESC);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        Map<String, VariableElement> localFields = new LinkedHashMap<>();
        for (VariableElement field : fields) {
            localFields.put(field.getSimpleName().toString(), field);
        }
        if (expandedFieldNames.isEmpty()) {
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        Label defaultLabel = new Label();
        Label[] caseLabels = new Label[expandedFieldNames.size()];
        for (int i = 0; i < expandedFieldNames.size(); i++) {
            caseLabels[i] = new Label();
        }
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitTableSwitchInsn(0, expandedFieldNames.size() - 1, defaultLabel, caseLabels);
        for (int i = 0; i < expandedFieldNames.size(); i++) {
            String fieldName = expandedFieldNames.get(i);
            mv.visitLabel(caseLabels[i]);
            VariableElement field = localFields.get(fieldName);
            if (field != null) {
                emitFieldInfo(mv, field, includeAnnotationMetadata, classInternalName);
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
                mv.visitVarInsn(Opcodes.ASTORE, 2);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.visitLdcInsn(fieldName);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "fieldIndex", "(Ljava/lang/String;)I", true);
                mv.visitVarInsn(Opcodes.ISTORE, 3);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.visitVarInsn(Opcodes.ILOAD, 3);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "getField", "(I)" + FIELD_INFO_DESC, true);
                mv.visitInsn(Opcodes.ARETURN);
            }
        }
        mv.visitLabel(defaultLabel);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitParentFieldLookupByName(MethodVisitor mv, String classInternalName, String fieldName) {
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
        mv.visitLdcInsn(fieldName);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "fieldIndex", "(Ljava/lang/String;)I", true);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "getField", "(I)" + FIELD_INFO_DESC, true);
        mv.visitInsn(Opcodes.ARETURN);
    }

    private void writeGetMethods(ClassWriter cw, String classInternalName, boolean includeMethodsMetadata, List<String> methodNames, String parentReflectTypeName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getMethods", "()[Ljava/lang/String;", null, null);
        mv.visitCode();
        if (includeMethodsMetadata) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, classInternalName, "METHOD_NAMES", "[Ljava/lang/String;");
            mv.visitInsn(Opcodes.ARETURN);
        } else if (parentReflectTypeName != null) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "getMethods", "()[Ljava/lang/String;", true);
            mv.visitInsn(Opcodes.ARETURN);
        } else {
            mv.visitFieldInsn(Opcodes.GETSTATIC, REFLECTOR, "NO_METHOD_NAMES", "[Ljava/lang/String;");
            mv.visitInsn(Opcodes.ARETURN);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeMethodIndex(ClassWriter cw, String classInternalName, List<String> methodNames, boolean includeMethodsMetadata, String parentReflectTypeName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "methodIndex", "(Ljava/lang/String;)I", null, null);
        mv.visitCode();
        if (!includeMethodsMetadata) {
            emitParentIntOrMinusOne(mv, classInternalName, parentReflectTypeName, "methodIndex", "(Ljava/lang/String;)I");
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        Label nonNull = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitJumpInsn(Opcodes.IFNONNULL, nonNull);
        AsmUtils.pushInt(mv, -1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitLabel(nonNull);
        AsmUtils.emitStringEqualsDispatch(
                mv,
                1,
                methodNames,
                index -> {
                    AsmUtils.pushInt(mv, index);
                    mv.visitInsn(Opcodes.IRETURN);
                },
                () -> emitParentIntDefault(mv, classInternalName, parentReflectTypeName, "methodIndex", "(Ljava/lang/String;)I", 1)
        );
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writeGetMethod(ClassWriter cw, String classInternalName, Map<String, List<ExecutableElement>> methodsByName, List<String> methodNames, boolean includeMethodsMetadata, boolean includeAnnotationMetadata, String parentReflectTypeName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "getMethod", "(I)[Lorg/byteora/kyra/core/runtime/MethodInfo;", null, null);
        mv.visitCode();
        if (!includeMethodsMetadata) {
            emitParentMethodArrayOrEmptyByIndex(mv, classInternalName, parentReflectTypeName);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        if (methodNames.isEmpty()) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, REFLECTOR, "NO_METHODS", "[" + METHOD_INFO_DESC);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }
        Label defaultLabel = new Label();
        Label[] caseLabels = new Label[methodNames.size()];
        for (int i = 0; i < methodNames.size(); i++) {
            caseLabels[i] = new Label();
        }
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitTableSwitchInsn(0, methodNames.size() - 1, defaultLabel, caseLabels);
        for (int i = 0; i < methodNames.size(); i++) {
            String methodName = methodNames.get(i);
            mv.visitLabel(caseLabels[i]);
            List<ExecutableElement> localMethods = methodsByName.get(methodName);
            if (localMethods != null) {
                emitMethodInfoArray(mv, localMethods, includeAnnotationMetadata, classInternalName);
                if (parentReflectTypeName != null) {
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
                    mv.visitVarInsn(Opcodes.ASTORE, 2);
                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                    mv.visitLdcInsn(methodName);
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "methodIndex", "(Ljava/lang/String;)I", true);
                    mv.visitVarInsn(Opcodes.ISTORE, 3);
                    mv.visitVarInsn(Opcodes.ALOAD, 2);
                    mv.visitVarInsn(Opcodes.ILOAD, 3);
                    mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "getMethod", "(I)[Lorg/byteora/kyra/core/runtime/MethodInfo;", true);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "mergeMethodInfos", "([Lorg/byteora/kyra/core/runtime/MethodInfo;[Lorg/byteora/kyra/core/runtime/MethodInfo;)[Lorg/byteora/kyra/core/runtime/MethodInfo;", false);
                }
                mv.visitInsn(Opcodes.ARETURN);
            } else if (parentReflectTypeName != null) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
                mv.visitVarInsn(Opcodes.ASTORE, 2);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.visitLdcInsn(methodName);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "methodIndex", "(Ljava/lang/String;)I", true);
                mv.visitVarInsn(Opcodes.ISTORE, 3);
                mv.visitVarInsn(Opcodes.ALOAD, 2);
                mv.visitVarInsn(Opcodes.ILOAD, 3);
                mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "getMethod", "(I)[Lorg/byteora/kyra/core/runtime/MethodInfo;", true);
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                mv.visitFieldInsn(Opcodes.GETSTATIC, REFLECTOR, "NO_METHODS", "[" + METHOD_INFO_DESC);
                mv.visitInsn(Opcodes.ARETURN);
            }
        }
        mv.visitLabel(defaultLabel);
        mv.visitFieldInsn(Opcodes.GETSTATIC, REFLECTOR, "NO_METHODS", "[" + METHOD_INFO_DESC);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitParentIntOrMinusOne(MethodVisitor mv, String classInternalName, String parentReflectTypeName, String methodName, String descriptor) {
        if (parentReflectTypeName != null) {
            Label nonNull = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitJumpInsn(Opcodes.IFNONNULL, nonNull);
            AsmUtils.pushInt(mv, -1);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitLabel(nonNull);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, methodName, descriptor, true);
            mv.visitInsn(Opcodes.IRETURN);
        } else {
            AsmUtils.pushInt(mv, -1);
            mv.visitInsn(Opcodes.IRETURN);
        }
    }

    private void emitParentIntDefault(MethodVisitor mv, String classInternalName, String parentReflectTypeName, String methodName, String descriptor, int localIndex) {
        if (parentReflectTypeName != null) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
            mv.visitVarInsn(Opcodes.ALOAD, localIndex);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, methodName, descriptor, true);
            mv.visitInsn(Opcodes.IRETURN);
        } else {
            AsmUtils.pushInt(mv, -1);
            mv.visitInsn(Opcodes.IRETURN);
        }
    }

    private void emitParentObjectOrNull(MethodVisitor mv, String classInternalName, String parentReflectTypeName, String methodName, String descriptor) {
        if (parentReflectTypeName != null) {
            Label nonNull = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitJumpInsn(Opcodes.IFNONNULL, nonNull);
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitLabel(nonNull);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, methodName, descriptor, true);
            mv.visitInsn(Opcodes.ARETURN);
        } else {
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ARETURN);
        }
    }

    private void emitParentObjectOrNullByIndex(MethodVisitor mv, String classInternalName, String parentReflectTypeName, String methodName, String descriptor) {
        if (parentReflectTypeName != null) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, methodName, descriptor, true);
            mv.visitInsn(Opcodes.ARETURN);
        } else {
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ARETURN);
        }
    }

    private void emitParentMethodArrayOrEmpty(MethodVisitor mv, String classInternalName, String parentReflectTypeName) {
        if (parentReflectTypeName != null) {
            Label nonNull = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitJumpInsn(Opcodes.IFNONNULL, nonNull);
            AsmUtils.pushInt(mv, 0);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, METHOD_INFO);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitLabel(nonNull);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "getMethod", "(Ljava/lang/String;)[Lorg/byteora/kyra/core/runtime/MethodInfo;", true);
            mv.visitInsn(Opcodes.ARETURN);
        } else {
            AsmUtils.pushInt(mv, 0);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, METHOD_INFO);
            mv.visitInsn(Opcodes.ARETURN);
        }
    }

    private void emitParentMethodArrayOrEmptyByIndex(MethodVisitor mv, String classInternalName, String parentReflectTypeName) {
        if (parentReflectTypeName != null) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
            mv.visitVarInsn(Opcodes.ILOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "getMethod", "(I)[Lorg/byteora/kyra/core/runtime/MethodInfo;", true);
            mv.visitInsn(Opcodes.ARETURN);
        } else {
            AsmUtils.pushInt(mv, 0);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, METHOD_INFO);
            mv.visitInsn(Opcodes.ARETURN);
        }
    }

    private void emitFieldInfo(MethodVisitor mv, VariableElement field, boolean includeAnnotationMetadata, String classInternalName) {
        mv.visitTypeInsn(Opcodes.NEW, FIELD_INFO);
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(field.getSimpleName().toString());
        emitTypeLiteral(mv, field.asType());
        AsmUtils.pushInt(mv, modifierMask(field.getModifiers()));
        String alias = context.fieldAlias(field);
        if (alias == null) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else {
            mv.visitLdcInsn(alias);
        }
        emitAnnotationArray(mv, field.getAnnotationMirrors(), includeAnnotationMetadata, classInternalName);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, FIELD_INFO, "<init>", "(Ljava/lang/String;Ljava/lang/reflect/Type;ILjava/lang/String;[" + ANNOTATION_META_DESC + ")V", false);
    }

    private void emitMethodInfoArray(MethodVisitor mv, List<ExecutableElement> methods, boolean includeAnnotationMetadata, String classInternalName) {
        AsmUtils.pushInt(mv, methods.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, METHOD_INFO);
        for (int i = 0; i < methods.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            AsmUtils.pushInt(mv, i);
            emitMethodInfo(mv, methods.get(i), includeAnnotationMetadata, classInternalName);
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    private void emitMethodInfo(MethodVisitor mv, ExecutableElement method, boolean includeAnnotationMetadata, String classInternalName) {
        mv.visitTypeInsn(Opcodes.NEW, METHOD_INFO);
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(method.getSimpleName().toString());
        emitTypeLiteral(mv, method.getReturnType());
        AsmUtils.pushInt(mv, modifierMask(method.getModifiers()));
        emitParameterInfoArray(mv, method.getParameters(), includeAnnotationMetadata, classInternalName);
        emitAnnotationArray(mv, method.getAnnotationMirrors(), includeAnnotationMetadata, classInternalName);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, METHOD_INFO, "<init>", "(Ljava/lang/String;Ljava/lang/reflect/Type;I[" + PARAMETER_INFO_DESC + "[" + ANNOTATION_META_DESC + ")V", false);
    }

    private void emitParameterInfoArray(MethodVisitor mv, List<? extends VariableElement> parameters, boolean includeAnnotationMetadata, String classInternalName) {
        if (parameters.isEmpty()) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, REFLECTOR, "NO_PARAMS", "[" + PARAMETER_INFO_DESC);
            return;
        }
        AsmUtils.pushInt(mv, parameters.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, PARAMETER_INFO);
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement parameter = parameters.get(i);
            mv.visitInsn(Opcodes.DUP);
            AsmUtils.pushInt(mv, i);
            mv.visitTypeInsn(Opcodes.NEW, PARAMETER_INFO);
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(parameter.getSimpleName().toString());
            emitTypeLiteral(mv, parameter.asType());
            emitAnnotationArray(mv, parameter.getAnnotationMirrors(), includeAnnotationMetadata, classInternalName);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, PARAMETER_INFO, "<init>", "(Ljava/lang/String;Ljava/lang/reflect/Type;[" + ANNOTATION_META_DESC + ")V", false);
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    private void emitAnnotationArray(MethodVisitor mv, List<? extends AnnotationMirror> annotations, boolean includeAnnotationMetadata, String classInternalName) {
        List<? extends AnnotationMirror> includedAnnotations = annotations.stream()
                .filter(annotation -> includeAnnotationMetadata && isRuntimeVisibleAnnotation(annotation))
                .toList();
        if (includedAnnotations.isEmpty()) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, REFLECTOR, "NO_ANNOTATIONS", "[" + ANNOTATION_META_DESC);
            return;
        }
        AsmUtils.pushInt(mv, includedAnnotations.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, ANNOTATION_META);
        for (int i = 0; i < includedAnnotations.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            AsmUtils.pushInt(mv, i);
            emitAnnotationMeta(mv, includedAnnotations.get(i), classInternalName);
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    private void emitAnnotationMeta(MethodVisitor mv, AnnotationMirror annotationMirror, String classInternalName) {
        TypeElement annotationType = (TypeElement) annotationMirror.getAnnotationType().asElement();
        List<ExecutableElement> attributes = new ArrayList<>();
        for (Element enclosed : annotationType.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                attributes.add((ExecutableElement) enclosed);
            }
        }
        mv.visitTypeInsn(Opcodes.NEW, ANNOTATION_META);
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(annotationType.getQualifiedName().toString());
        emitMap(mv, attributes.size(), index -> {
            ExecutableElement method = attributes.get(index);
            mv.visitLdcInsn(method.getSimpleName().toString());
            emitAnnotationValueObject(mv, annotationValue(annotationMirror, method), method.getReturnType(), classInternalName);
        });
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ANNOTATION_META, "<init>", "(Ljava/lang/String;Ljava/util/Map;)V", false);
    }

    private void emitMap(MethodVisitor mv, int size, java.util.function.IntConsumer entryEmitter) {
        if (size == 0) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Map", "of", "()Ljava/util/Map;", true);
            return;
        }
        AsmUtils.pushInt(mv, size);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/util/Map$Entry");
        for (int i = 0; i < size; i++) {
            mv.visitInsn(Opcodes.DUP);
            AsmUtils.pushInt(mv, i);
            entryEmitter.accept(i);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Map", "entry", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map$Entry;", true);
            mv.visitInsn(Opcodes.AASTORE);
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Map", "ofEntries", "([Ljava/util/Map$Entry;)Ljava/util/Map;", true);
    }

    @SuppressWarnings("unchecked")
    private void emitAnnotationValueObject(MethodVisitor mv, AnnotationValue value, TypeMirror expectedType, String classInternalName) {
        Object raw = value.getValue();
        switch (expectedType.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE -> emitObjectLiteral(mv, raw);
            case ARRAY -> emitAnnotationArrayValue(mv, (List<? extends AnnotationValue>) raw, (ArrayType) expectedType, classInternalName);
            default -> emitDeclaredAnnotationValue(mv, raw, expectedType, classInternalName);
        }
    }

    private void emitAnnotationArrayValue(MethodVisitor mv, List<? extends AnnotationValue> values, ArrayType arrayType, String classInternalName) {
        AsmUtils.pushInt(mv, values.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        for (int i = 0; i < values.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            AsmUtils.pushInt(mv, i);
            emitAnnotationValueObject(mv, values.get(i), arrayType.getComponentType(), classInternalName);
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    private void emitDeclaredAnnotationValue(MethodVisitor mv, Object raw, TypeMirror expectedType, String classInternalName) {
        if (raw instanceof String stringValue) {
            mv.visitLdcInsn(stringValue);
            return;
        }
        if (raw instanceof Character charValue) {
            mv.visitLdcInsn((int) charValue);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            return;
        }
        if (raw instanceof TypeMirror typeMirror) {
            mv.visitLdcInsn(typeMirror.toString());
            return;
        }
        if (raw instanceof VariableElement enumConstant) {
            TypeElement owner = (TypeElement) enumConstant.getEnclosingElement();
            mv.visitLdcInsn(owner.getQualifiedName() + "." + enumConstant.getSimpleName());
            return;
        }
        if (raw instanceof AnnotationMirror annotationMirror) {
            emitAnnotationMeta(mv, annotationMirror, classInternalName);
            return;
        }
        emitObjectLiteral(mv, raw);
    }

    private void emitTypeOrNull(MethodVisitor mv, TypeMirror typeMirror) {
        if (typeMirror == null || typeMirror.getKind() == TypeKind.NONE) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else {
            emitTypeLiteral(mv, typeMirror);
        }
    }

    private void emitTypeLiteral(MethodVisitor mv, TypeMirror typeMirror) {
        if (typeMirror == null || typeMirror.getKind() == TypeKind.NONE) {
            mv.visitInsn(Opcodes.ACONST_NULL);
            return;
        }
        switch (typeMirror.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE -> AsmUtils.pushClassLiteral(mv, typeMirror, context.types());
            case ARRAY -> emitArrayTypeLiteral(mv, (ArrayType) typeMirror);
            case DECLARED -> emitDeclaredTypeLiteral(mv, (DeclaredType) typeMirror);
            case TYPEVAR -> emitTypeVariableLiteral(mv, (javax.lang.model.type.TypeVariable) typeMirror);
            case WILDCARD -> emitWildcardTypeLiteral(mv, (WildcardType) typeMirror);
            default -> AsmUtils.pushClassLiteral(mv, typeMirror, context.types());
        }
    }

    private void emitArrayTypeLiteral(MethodVisitor mv, ArrayType arrayType) {
        TypeMirror componentType = arrayType.getComponentType();
        if (isReifiable(componentType)) {
            AsmUtils.pushClassLiteral(mv, arrayType, context.types());
            return;
        }
        emitTypeLiteral(mv, componentType);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME_TYPES, "array", "(" + TYPE_DESC + ")" + TYPE_DESC, false);
    }

    private void emitDeclaredTypeLiteral(MethodVisitor mv, DeclaredType declaredType) {
        if (declaredType.getTypeArguments().isEmpty()) {
            AsmUtils.pushClassLiteral(mv, declaredType, context.types());
            return;
        }
        AsmUtils.pushClassLiteral(mv, declaredType, context.types());
        TypeMirror ownerType = declaredType.getEnclosingType();
        if (ownerType == null || ownerType.getKind() == TypeKind.NONE) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else {
            emitTypeLiteral(mv, ownerType);
        }
        emitTypeArray(mv, declaredType.getTypeArguments());
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME_TYPES, "parameterized", "(Ljava/lang/Class;" + TYPE_DESC + "[" + TYPE_DESC + ")" + TYPE_DESC, false);
    }

    private void emitTypeVariableLiteral(MethodVisitor mv, javax.lang.model.type.TypeVariable typeVariable) {
        mv.visitLdcInsn(typeVariable.asElement().getSimpleName().toString());
        TypeMirror upperBound = typeVariable.getUpperBound();
        if (upperBound != null && upperBound.getKind() != TypeKind.NULL && upperBound.getKind() != TypeKind.NONE) {
            emitTypeArray(mv, List.of(upperBound));
        } else {
            emitTypeArray(mv, List.of(context.objectTypeElement().asType()));
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME_TYPES, "typeVariable", "(Ljava/lang/String;[" + TYPE_DESC + ")" + TYPE_DESC, false);
    }

    private void emitWildcardTypeLiteral(MethodVisitor mv, WildcardType wildcardType) {
        TypeMirror extendsBound = wildcardType.getExtendsBound();
        TypeMirror superBound = wildcardType.getSuperBound();
        emitTypeArray(mv, extendsBound == null ? List.of(context.objectTypeElement().asType()) : List.of(extendsBound));
        emitTypeArray(mv, superBound == null ? List.of() : List.of(superBound));
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME_TYPES, "wildcard", "([" + TYPE_DESC + "[" + TYPE_DESC + ")" + TYPE_DESC, false);
    }

    private void emitTypeArray(MethodVisitor mv, List<? extends TypeMirror> types) {
        AsmUtils.pushInt(mv, types.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/reflect/Type");
        for (int i = 0; i < types.size(); i++) {
            mv.visitInsn(Opcodes.DUP);
            AsmUtils.pushInt(mv, i);
            emitTypeLiteral(mv, types.get(i));
            mv.visitInsn(Opcodes.AASTORE);
        }
    }

    private boolean isReifiable(TypeMirror typeMirror) {
        return switch (typeMirror.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE -> true;
            case ARRAY -> isReifiable(((ArrayType) typeMirror).getComponentType());
            case DECLARED -> typeMirror instanceof DeclaredType declaredType && declaredType.getTypeArguments().isEmpty();
            default -> false;
        };
    }

    private boolean isRuntimeVisibleAnnotation(AnnotationMirror annotationMirror) {
        Element element = annotationMirror.getAnnotationType().asElement();
        if (!(element instanceof TypeElement annotationType)) {
            return false;
        }
        for (AnnotationMirror meta : annotationType.getAnnotationMirrors()) {
            if (!meta.getAnnotationType().toString().equals("java.lang.annotation.Retention")) {
                continue;
            }
            for (AnnotationValue value : meta.getElementValues().values()) {
                Object raw = value.getValue();
                if (raw instanceof VariableElement retentionValue) {
                    return retentionValue.getSimpleName().contentEquals(RetentionPolicy.RUNTIME.name());
                }
            }
        }
        return false;
    }

    private AnnotationValue annotationValue(AnnotationMirror annotationMirror, ExecutableElement method) {
        AnnotationValue value = annotationMirror.getElementValues().get(method);
        if (value != null) {
            return value;
        }
        AnnotationValue defaultValue = method.getDefaultValue();
        if (defaultValue == null) {
            throw new IllegalArgumentException("Missing annotation value for " + method.getSimpleName() + " on " + annotationMirror);
        }
        return defaultValue;
    }

    private int modifierMask(java.util.Set<Modifier> modifiers) {
        int value = 0;
        if (modifiers.contains(Modifier.PUBLIC)) value |= java.lang.reflect.Modifier.PUBLIC;
        if (modifiers.contains(Modifier.PROTECTED)) value |= java.lang.reflect.Modifier.PROTECTED;
        if (modifiers.contains(Modifier.PRIVATE)) value |= java.lang.reflect.Modifier.PRIVATE;
        if (modifiers.contains(Modifier.ABSTRACT)) value |= java.lang.reflect.Modifier.ABSTRACT;
        if (modifiers.contains(Modifier.STATIC)) value |= java.lang.reflect.Modifier.STATIC;
        if (modifiers.contains(Modifier.FINAL)) value |= java.lang.reflect.Modifier.FINAL;
        if (modifiers.contains(Modifier.TRANSIENT)) value |= java.lang.reflect.Modifier.TRANSIENT;
        if (modifiers.contains(Modifier.VOLATILE)) value |= java.lang.reflect.Modifier.VOLATILE;
        if (modifiers.contains(Modifier.SYNCHRONIZED)) value |= java.lang.reflect.Modifier.SYNCHRONIZED;
        if (modifiers.contains(Modifier.NATIVE)) value |= java.lang.reflect.Modifier.NATIVE;
        if (modifiers.contains(Modifier.STRICTFP)) value |= java.lang.reflect.Modifier.STRICT;
        return value;
    }

    private void emitInvokeCase(MethodVisitor mv, ExecutableElement method, String entityInternalName) {
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        for (int i = 0; i < method.getParameters().size(); i++) {
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            AsmUtils.pushInt(mv, i);
            mv.visitInsn(Opcodes.AALOAD);
            AsmUtils.castFromObject(mv, method.getParameters().get(i).asType(), context.types());
        }
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entityInternalName, method.getSimpleName().toString(),
                AsmUtils.methodDescriptor(method.getReturnType(), method.getParameters().stream().map(VariableElement::asType).toList(), context.types()), false);
        if (method.getReturnType().getKind() == TypeKind.VOID) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else if (method.getReturnType().getKind().isPrimitive()) {
            AsmUtils.box(mv, method.getReturnType());
        }
        mv.visitInsn(Opcodes.ARETURN);
    }

    private void emitInvokeDefault(MethodVisitor mv, String classInternalName, String parentReflectTypeName) {
        if (parentReflectTypeName != null) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
            mv.visitVarInsn(Opcodes.ALOAD, 4);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "invoke", "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", true);
            mv.visitInsn(Opcodes.ARETURN);
            return;
        }
        emitIllegalArgument(mv, "Unknown method");
    }

    private void writePrimitiveAccessors(ClassWriter cw,
                                         String classInternalName,
                                         TypeElement entityType,
                                         String entityInternalName,
                                         List<VariableElement> fields,
                                         List<String> expandedFieldNames,
                                         String parentReflectTypeName) {
        maybeWritePrimitiveAccessor(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName,
                TypeKind.BOOLEAN, "setBoolean", "(Ljava/lang/Object;IZ)V", Opcodes.ILOAD,
                "getBoolean", "(Ljava/lang/Object;I)Z", Opcodes.IRETURN);
        maybeWritePrimitiveAccessor(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName,
                TypeKind.BYTE, "setByte", "(Ljava/lang/Object;IB)V", Opcodes.ILOAD,
                "getByte", "(Ljava/lang/Object;I)B", Opcodes.IRETURN);
        maybeWritePrimitiveAccessor(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName,
                TypeKind.SHORT, "setShort", "(Ljava/lang/Object;IS)V", Opcodes.ILOAD,
                "getShort", "(Ljava/lang/Object;I)S", Opcodes.IRETURN);
        maybeWritePrimitiveAccessor(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName,
                TypeKind.INT, "setInt", "(Ljava/lang/Object;II)V", Opcodes.ILOAD,
                "getInt", "(Ljava/lang/Object;I)I", Opcodes.IRETURN);
        maybeWritePrimitiveAccessor(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName,
                TypeKind.LONG, "setLong", "(Ljava/lang/Object;IJ)V", Opcodes.LLOAD,
                "getLong", "(Ljava/lang/Object;I)J", Opcodes.LRETURN);
        maybeWritePrimitiveAccessor(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName,
                TypeKind.CHAR, "setChar", "(Ljava/lang/Object;IC)V", Opcodes.ILOAD,
                "getChar", "(Ljava/lang/Object;I)C", Opcodes.IRETURN);
        maybeWritePrimitiveAccessor(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName,
                TypeKind.FLOAT, "setFloat", "(Ljava/lang/Object;IF)V", Opcodes.FLOAD,
                "getFloat", "(Ljava/lang/Object;I)F", Opcodes.FRETURN);
        maybeWritePrimitiveAccessor(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName,
                TypeKind.DOUBLE, "setDouble", "(Ljava/lang/Object;ID)V", Opcodes.DLOAD,
                "getDouble", "(Ljava/lang/Object;I)D", Opcodes.DRETURN);
    }

    private void maybeWritePrimitiveAccessor(ClassWriter cw,
                                             String classInternalName,
                                             TypeElement entityType,
                                             String entityInternalName,
                                             List<VariableElement> fields,
                                             List<String> expandedFieldNames,
                                             String parentReflectTypeName,
                                             TypeKind primitiveKind,
                                             String setMethodName,
                                             String setDescriptor,
                                             int setValueLoadOpcode,
                                             String getMethodName,
                                             String getDescriptor,
                                             int getReturnOpcode) {
        List<Integer> indices = primitiveFieldIndices(entityType, fields, expandedFieldNames, parentReflectTypeName, primitiveKind);
        if (indices.isEmpty()) {
            return;
        }
        writePrimitiveSetter(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName,
                indices, primitiveKind, setMethodName, setDescriptor, setValueLoadOpcode);
        writePrimitiveGetter(cw, classInternalName, entityType, entityInternalName, fields, expandedFieldNames, parentReflectTypeName,
                indices, primitiveKind, getMethodName, getDescriptor, getReturnOpcode);
    }

    private List<Integer> primitiveFieldIndices(TypeElement entityType,
                                                List<VariableElement> localFields,
                                                List<String> expandedFieldNames,
                                                String parentReflectTypeName,
                                                TypeKind primitiveKind) {
        Map<String, VariableElement> localFieldsByName = new LinkedHashMap<>();
        for (VariableElement field : localFields) {
            localFieldsByName.put(field.getSimpleName().toString(), field);
        }
        Map<String, VariableElement> parentFieldsByName = new LinkedHashMap<>();
        if (parentReflectTypeName != null) {
            TypeElement parentType = resolveParentReflectType(entityType);
            if (parentType != null) {
                for (VariableElement field : context.collectInstanceFields(parentType)) {
                    parentFieldsByName.put(field.getSimpleName().toString(), field);
                }
            }
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < expandedFieldNames.size(); i++) {
            String fieldName = expandedFieldNames.get(i);
            VariableElement field = localFieldsByName.get(fieldName);
            if (field == null) {
                field = parentFieldsByName.get(fieldName);
            }
            if (field != null && field.asType().getKind() == primitiveKind) {
                indices.add(i);
            }
        }
        return indices;
    }

    private static String primitiveKindLabel(TypeKind kind) {
        return switch (kind) {
            case BOOLEAN -> "boolean";
            case BYTE -> "byte";
            case SHORT -> "short";
            case INT -> "int";
            case LONG -> "long";
            case CHAR -> "char";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            default -> kind.name().toLowerCase();
        };
    }

    private static final int PRIMITIVE_SET_ENTITY_SLOT = 6;
    private static final int PRIMITIVE_SET_PARENT_SLOT = 7;

    private void writePrimitiveSetter(ClassWriter cw,
                                      String classInternalName,
                                      TypeElement entityType,
                                      String entityInternalName,
                                      List<VariableElement> fields,
                                      List<String> expandedFieldNames,
                                      String parentReflectTypeName,
                                      List<Integer> indices,
                                      TypeKind primitiveKind,
                                      String methodName,
                                      String descriptor,
                                      int valueLoadOpcode) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, descriptor, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, entityInternalName);
        // Slot 6 avoids overlap with long/double value params that occupy slots 3+4.
        mv.visitVarInsn(Opcodes.ASTORE, PRIMITIVE_SET_ENTITY_SLOT);
        Map<String, VariableElement> localFields = new LinkedHashMap<>();
        for (VariableElement field : fields) {
            localFields.put(field.getSimpleName().toString(), field);
        }
        emitPrimitiveIndexSwitch(mv, indices, primitiveKind,
                (index, caseLabel) -> {
                    String fieldName = expandedFieldNames.get(index);
                    VariableElement field = localFields.get(fieldName);
                    if (field != null) {
                        emitSetPrimitiveCase(mv, entityType, entityInternalName, field, primitiveKind, valueLoadOpcode, PRIMITIVE_SET_ENTITY_SLOT);
                    } else if (parentReflectTypeName != null) {
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
                        mv.visitVarInsn(Opcodes.ASTORE, PRIMITIVE_SET_PARENT_SLOT);
                        mv.visitVarInsn(Opcodes.ALOAD, PRIMITIVE_SET_PARENT_SLOT);
                        mv.visitVarInsn(Opcodes.ALOAD, PRIMITIVE_SET_ENTITY_SLOT);
                        // Inherited field: the child's expanded index does not match the parent's own
                        // switch layout, so re-resolve the index by name against the parent reflector.
                        mv.visitVarInsn(Opcodes.ALOAD, PRIMITIVE_SET_PARENT_SLOT);
                        mv.visitLdcInsn(fieldName);
                        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "fieldIndex", "(Ljava/lang/String;)I", true);
                        mv.visitVarInsn(valueLoadOpcode, 3);
                        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, methodName, descriptor, true);
                        mv.visitInsn(Opcodes.RETURN);
                    } else {
                        emitIllegalArgument(mv, "Unknown property index or not a " + primitiveKindLabel(primitiveKind));
                    }
                });
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void writePrimitiveGetter(ClassWriter cw,
                                      String classInternalName,
                                      TypeElement entityType,
                                      String entityInternalName,
                                      List<VariableElement> fields,
                                      List<String> expandedFieldNames,
                                      String parentReflectTypeName,
                                      List<Integer> indices,
                                      TypeKind primitiveKind,
                                      String methodName,
                                      String descriptor,
                                      int returnOpcode) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, methodName, descriptor, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, entityInternalName);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        Map<String, VariableElement> localFields = new LinkedHashMap<>();
        for (VariableElement field : fields) {
            localFields.put(field.getSimpleName().toString(), field);
        }
        emitPrimitiveIndexSwitch(mv, indices, primitiveKind,
                (index, caseLabel) -> {
                    String fieldName = expandedFieldNames.get(index);
                    VariableElement field = localFields.get(fieldName);
                    if (field != null) {
                        emitGetPrimitiveCase(mv, entityType, entityInternalName, field, primitiveKind, returnOpcode);
                    } else if (parentReflectTypeName != null) {
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
                        mv.visitVarInsn(Opcodes.ASTORE, 4);
                        mv.visitVarInsn(Opcodes.ALOAD, 4);
                        mv.visitVarInsn(Opcodes.ALOAD, 3);
                        // Inherited field: re-resolve the index by name against the parent reflector,
                        // whose switch uses its own layout rather than the child's expanded index.
                        mv.visitVarInsn(Opcodes.ALOAD, 4);
                        mv.visitLdcInsn(fieldName);
                        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "fieldIndex", "(Ljava/lang/String;)I", true);
                        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, methodName, descriptor, true);
                        mv.visitInsn(returnOpcode);
                    } else {
                        emitIllegalArgument(mv, "Unknown property index or not a " + primitiveKindLabel(primitiveKind));
                    }
                });
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    @FunctionalInterface
    private interface PrimitiveIndexCaseEmitter {
        void emit(int index, Label caseLabel);
    }

    private void emitPrimitiveIndexSwitch(MethodVisitor mv,
                                          List<Integer> indices,
                                          TypeKind primitiveKind,
                                          PrimitiveIndexCaseEmitter caseEmitter) {
        Label defaultLabel = new Label();
        if (indices.size() == 1) {
            int onlyIndex = indices.get(0);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitLdcInsn(onlyIndex);
            mv.visitJumpInsn(Opcodes.IF_ICMPNE, defaultLabel);
            caseEmitter.emit(onlyIndex, defaultLabel);
            mv.visitLabel(defaultLabel);
            emitIllegalArgument(mv, "Unknown property index or not a " + primitiveKindLabel(primitiveKind));
            return;
        }
        int[] keys = indices.stream().mapToInt(Integer::intValue).sorted().toArray();
        Label[] caseLabels = new Label[keys.length];
        for (int i = 0; i < keys.length; i++) {
            caseLabels[i] = new Label();
        }
        mv.visitVarInsn(Opcodes.ILOAD, 2);
        mv.visitLookupSwitchInsn(defaultLabel, keys, caseLabels);
        for (int i = 0; i < keys.length; i++) {
            mv.visitLabel(caseLabels[i]);
            caseEmitter.emit(keys[i], caseLabels[i]);
        }
        mv.visitLabel(defaultLabel);
        emitIllegalArgument(mv, "Unknown property index or not a " + primitiveKindLabel(primitiveKind));
    }

    private void emitSetPrimitiveCase(MethodVisitor mv,
                                      TypeElement entityType,
                                      String entityInternalName,
                                      VariableElement field,
                                      TypeKind primitiveKind,
                                      int valueLoadOpcode,
                                      int entitySlot) {
        String fieldName = field.getSimpleName().toString();
        ExecutableElement setter = findMethod(entityType, "set" + context.capitalize(fieldName), 1);
        if (setter != null && setter.getParameters().get(0).asType().getKind() == primitiveKind) {
            mv.visitVarInsn(Opcodes.ALOAD, entitySlot);
            mv.visitVarInsn(valueLoadOpcode, 3);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entityInternalName, setter.getSimpleName().toString(),
                    AsmUtils.methodDescriptor(setter.getReturnType(), setter.getParameters().stream().map(VariableElement::asType).toList(), context.types()), false);
            if (setter.getReturnType().getKind() != TypeKind.VOID) {
                if (setter.getReturnType().getKind() == TypeKind.LONG || setter.getReturnType().getKind() == TypeKind.DOUBLE) {
                    mv.visitInsn(Opcodes.POP2);
                } else {
                    mv.visitInsn(Opcodes.POP);
                }
            }
            mv.visitInsn(Opcodes.RETURN);
            return;
        }
        if (field.getModifiers().contains(Modifier.PUBLIC)) {
            mv.visitVarInsn(Opcodes.ALOAD, entitySlot);
            mv.visitVarInsn(valueLoadOpcode, 3);
            mv.visitFieldInsn(Opcodes.PUTFIELD, entityInternalName, fieldName, AsmUtils.descriptor(field.asType(), context.types()));
            mv.visitInsn(Opcodes.RETURN);
            return;
        }
        emitUnsupported(mv, "No setter or field access for property: " + fieldName);
    }

    private void emitGetPrimitiveCase(MethodVisitor mv,
                                      TypeElement entityType,
                                      String entityInternalName,
                                      VariableElement field,
                                      TypeKind primitiveKind,
                                      int returnOpcode) {
        String fieldName = field.getSimpleName().toString();
        ExecutableElement getter = findMethod(entityType, "get" + context.capitalize(fieldName), 0);
        ExecutableElement booleanGetter = findMethod(entityType, "is" + context.capitalize(fieldName), 0);
        ExecutableElement recordAccessor = entityType.getKind() == ElementKind.RECORD ? findMethod(entityType, fieldName, 0) : null;
        if (getter != null && getter.getReturnType().getKind() == primitiveKind) {
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entityInternalName, getter.getSimpleName().toString(),
                    AsmUtils.methodDescriptor(getter.getReturnType(), List.of(), context.types()), false);
            mv.visitInsn(returnOpcode);
            return;
        }
        if (booleanGetter != null && booleanGetter.getReturnType().getKind() == primitiveKind) {
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entityInternalName, booleanGetter.getSimpleName().toString(),
                    AsmUtils.methodDescriptor(booleanGetter.getReturnType(), List.of(), context.types()), false);
            mv.visitInsn(returnOpcode);
            return;
        }
        if (recordAccessor != null && recordAccessor.getReturnType().getKind() == primitiveKind) {
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entityInternalName, recordAccessor.getSimpleName().toString(),
                    AsmUtils.methodDescriptor(recordAccessor.getReturnType(), List.of(), context.types()), false);
            mv.visitInsn(returnOpcode);
            return;
        }
        if (field.getModifiers().contains(Modifier.PUBLIC)) {
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitFieldInsn(Opcodes.GETFIELD, entityInternalName, fieldName, AsmUtils.descriptor(field.asType(), context.types()));
            mv.visitInsn(returnOpcode);
            return;
        }
        emitUnsupported(mv, "No getter or field access for property: " + fieldName);
    }

    private void emitSetCase(MethodVisitor mv, TypeElement entityType, String entityInternalName, VariableElement field) {
        String fieldName = field.getSimpleName().toString();
        ExecutableElement setter = findMethod(entityType, "set" + context.capitalize(fieldName), 1);
        if (setter != null) {
            mv.visitVarInsn(Opcodes.ALOAD, 4);
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            AsmUtils.castFromObject(mv, field.asType(), context.types());
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entityInternalName, setter.getSimpleName().toString(),
                    AsmUtils.methodDescriptor(setter.getReturnType(), setter.getParameters().stream().map(VariableElement::asType).toList(), context.types()), false);
            if (setter.getReturnType().getKind() != TypeKind.VOID) {
                if (setter.getReturnType().getKind() == TypeKind.LONG || setter.getReturnType().getKind() == TypeKind.DOUBLE) {
                    mv.visitInsn(Opcodes.POP2);
                } else {
                    mv.visitInsn(Opcodes.POP);
                }
            }
            mv.visitInsn(Opcodes.RETURN);
            return;
        }
        if (field.getModifiers().contains(Modifier.PUBLIC)) {
            mv.visitVarInsn(Opcodes.ALOAD, 4);
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            AsmUtils.castFromObject(mv, field.asType(), context.types());
            mv.visitFieldInsn(Opcodes.PUTFIELD, entityInternalName, fieldName, AsmUtils.descriptor(field.asType(), context.types()));
            mv.visitInsn(Opcodes.RETURN);
            return;
        }
        emitUnsupported(mv, "No setter or field access for property: " + fieldName);
    }

    private void emitSetDefault(MethodVisitor mv, String classInternalName, String parentReflectTypeName) {
        if (parentReflectTypeName != null) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
            mv.visitVarInsn(Opcodes.ALOAD, 4);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "set", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V", true);
        }
    }

    private void emitGetCase(MethodVisitor mv, TypeElement entityType, String entityInternalName, VariableElement field) {
        String fieldName = field.getSimpleName().toString();
        ExecutableElement getter = findMethod(entityType, "get" + context.capitalize(fieldName), 0);
        ExecutableElement booleanGetter = findMethod(entityType, "is" + context.capitalize(fieldName), 0);
        ExecutableElement recordAccessor = entityType.getKind() == ElementKind.RECORD ? findMethod(entityType, fieldName, 0) : null;
        if (getter != null) {
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entityInternalName, getter.getSimpleName().toString(),
                    AsmUtils.methodDescriptor(getter.getReturnType(), List.of(), context.types()), false);
            if (getter.getReturnType().getKind().isPrimitive()) {
                AsmUtils.box(mv, getter.getReturnType());
            }
            mv.visitInsn(Opcodes.ARETURN);
            return;
        }
        if (booleanGetter != null) {
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entityInternalName, booleanGetter.getSimpleName().toString(),
                    AsmUtils.methodDescriptor(booleanGetter.getReturnType(), List.of(), context.types()), false);
            if (booleanGetter.getReturnType().getKind().isPrimitive()) {
                AsmUtils.box(mv, booleanGetter.getReturnType());
            }
            mv.visitInsn(Opcodes.ARETURN);
            return;
        }
        if (recordAccessor != null) {
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, entityInternalName, recordAccessor.getSimpleName().toString(),
                    AsmUtils.methodDescriptor(recordAccessor.getReturnType(), List.of(), context.types()), false);
            if (recordAccessor.getReturnType().getKind().isPrimitive()) {
                AsmUtils.box(mv, recordAccessor.getReturnType());
            }
            mv.visitInsn(Opcodes.ARETURN);
            return;
        }
        if (field.getModifiers().contains(Modifier.PUBLIC)) {
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitFieldInsn(Opcodes.GETFIELD, entityInternalName, fieldName, AsmUtils.descriptor(field.asType(), context.types()));
            if (field.asType().getKind().isPrimitive()) {
                AsmUtils.box(mv, field.asType());
            }
            mv.visitInsn(Opcodes.ARETURN);
            return;
        }
        emitUnsupported(mv, "No getter or field access for property: " + fieldName);
    }

    private void emitGetDefault(MethodVisitor mv, String classInternalName, String parentReflectTypeName) {
        if (parentReflectTypeName != null) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, classInternalName, "parentReflector", "()" + REFLECTOR_DESC, false);
            mv.visitVarInsn(Opcodes.ALOAD, 3);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, REFLECTOR, "get", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;", true);
            mv.visitInsn(Opcodes.ARETURN);
            return;
        }
        emitIllegalArgument(mv, "Unknown property");
    }

    private void emitIllegalArgument(MethodVisitor mv, String message) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(message);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.ATHROW);
    }

    private void emitUnsupported(MethodVisitor mv, String message) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(message);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(Opcodes.ATHROW);
    }

    private void emitDefaultValue(MethodVisitor mv, TypeMirror typeMirror) {
        switch (typeMirror.getKind()) {
            case BOOLEAN, BYTE, SHORT, INT, CHAR -> mv.visitInsn(Opcodes.ICONST_0);
            case LONG -> mv.visitInsn(Opcodes.LCONST_0);
            case FLOAT -> mv.visitInsn(Opcodes.FCONST_0);
            case DOUBLE -> mv.visitInsn(Opcodes.DCONST_0);
            default -> mv.visitInsn(Opcodes.ACONST_NULL);
        }
    }

    private void emitObjectLiteral(MethodVisitor mv, Object raw) {
        if (raw == null) {
            mv.visitInsn(Opcodes.ACONST_NULL);
            return;
        }
        if (raw instanceof String stringValue) {
            mv.visitLdcInsn(stringValue);
            return;
        }
        if (raw instanceof Boolean booleanValue) {
            mv.visitInsn(booleanValue ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            return;
        }
        if (raw instanceof Byte byteValue) {
            mv.visitIntInsn(Opcodes.BIPUSH, byteValue);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            return;
        }
        if (raw instanceof Short shortValue) {
            mv.visitIntInsn(Opcodes.SIPUSH, shortValue);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            return;
        }
        if (raw instanceof Integer intValue) {
            AsmUtils.pushInt(mv, intValue);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            return;
        }
        if (raw instanceof Long longValue) {
            mv.visitLdcInsn(longValue);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            return;
        }
        if (raw instanceof Float floatValue) {
            mv.visitLdcInsn(floatValue);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            return;
        }
        if (raw instanceof Double doubleValue) {
            mv.visitLdcInsn(doubleValue);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            return;
        }
        if (raw instanceof Character charValue) {
            mv.visitLdcInsn((int) charValue);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            return;
        }
        mv.visitLdcInsn(String.valueOf(raw));
    }

    private ExecutableElement findPreferredConstructor(TypeElement entityType) {
        if (entityType.getModifiers().contains(Modifier.ABSTRACT)) {
            return null;
        }
        List<ExecutableElement> constructors = constructors(entityType);
        ExecutableElement preferred = null;
        for (ExecutableElement constructor : constructors) {
            if (!constructor.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (preferred == null || constructor.getParameters().size() < preferred.getParameters().size()) {
                preferred = constructor;
            }
            if (constructor.getParameters().isEmpty()) {
                preferred = constructor;
                break;
            }
        }
        return preferred;
    }

    private ExecutableElement findFullArgsConstructor(TypeElement entityType) {
        ExecutableElement preferred = null;
        for (ExecutableElement constructor : constructors(entityType)) {
            if (!constructor.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            if (preferred == null || constructor.getParameters().size() > preferred.getParameters().size()) {
                preferred = constructor;
            }
        }
        return preferred;
    }

    private List<ExecutableElement> constructors(TypeElement entityType) {
        List<ExecutableElement> constructors = new ArrayList<>();
        for (Element enclosedElement : entityType.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {
                constructors.add((ExecutableElement) enclosedElement);
            }
        }
        return constructors;
    }

    private Map<String, List<ExecutableElement>> groupMethodsByName(List<ExecutableElement> methods) {
        Map<String, List<ExecutableElement>> methodsByName = new LinkedHashMap<>();
        for (ExecutableElement method : methods) {
            methodsByName.computeIfAbsent(method.getSimpleName().toString(), ignored -> new ArrayList<>()).add(method);
        }
        return methodsByName;
    }

    private List<String> expandedMethodNames(TypeElement entityType) {
        LinkedHashMap<String, Boolean> names = new LinkedHashMap<>();
        for (ExecutableElement method : context.collectInvokableMethods(entityType)) {
            names.putIfAbsent(method.getSimpleName().toString(), Boolean.TRUE);
        }
        TypeElement parentType = resolveParentReflectType(entityType);
        if (parentType != null) {
            for (String name : expandedMethodNames(parentType)) {
                names.putIfAbsent(name, Boolean.TRUE);
            }
        }
        return new ArrayList<>(names.keySet());
    }

    private ExecutableElement findMethod(TypeElement ownerElement, String methodName, int parameterCount) {
        for (Element enclosedElement : ownerElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD && enclosedElement.getSimpleName().contentEquals(methodName)) {
                ExecutableElement method = (ExecutableElement) enclosedElement;
                if (method.getParameters().size() == parameterCount && method.getModifiers().contains(Modifier.PUBLIC)) {
                    return method;
                }
            }
        }
        return null;
    }

    public interface Context {
        Types types();
        TypeElement objectTypeElement();
        TypeMirror voidType();
        ReflectSpec reflectSpec(TypeElement entityType);
        List<VariableElement> collectInstanceFields(TypeElement entityType);
        List<ExecutableElement> collectInvokableMethods(TypeElement entityType);
        List<String> expandedFieldNames(TypeElement entityType);
        boolean hasReflectSpec(TypeElement typeElement);
        TypeElement directSuperType(TypeElement entityType);
        boolean isJavaLangObject(TypeElement typeElement);
        String fieldAlias(VariableElement field);
        String capitalize(String value);
    }
}
