package org.byteora.kyra.json;

import org.byteora.kyra.core.runtime.AnnotationMeta;
import org.byteora.kyra.core.runtime.ClassInfo;
import org.byteora.kyra.core.runtime.FieldInfo;
import org.byteora.kyra.core.runtime.ParameterInfo;
import org.byteora.kyra.core.runtime.Reflector;
import org.byteora.kyra.core.runtime.ReflectorRegistry;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;

final class DefaultJsonMapper implements JsonMapper {
    private static final String FLATTEN_ANNOTATION = "org.byteora.kyra.json.Flatten";

    private final JsonFactory jsonFactory;
    private final List<JsonTypeHandler<?>> handlers;
    private final boolean failOnUnknownProperties;
    private final boolean includeNulls;

    private DefaultJsonMapper(List<JsonTypeHandler<?>> handlers, boolean failOnUnknownProperties, boolean includeNulls) {
        this.jsonFactory = new JsonFactory();
        this.handlers = List.copyOf(handlers);
        this.failOnUnknownProperties = failOnUnknownProperties;
        this.includeNulls = includeNulls;
    }

    @Override
    public String toJson(Object value) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = jsonFactory.createGenerator(ObjectWriteContext.empty(), writer)) {
            writeValue(generator, value, value == null ? Object.class : value.getClass(), Map.of());
        } catch (IOException ex) {
            throw new JsonException("Failed to write JSON", ex);
        }
        return writer.toString();
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        return fromJson(json, (Type) type);
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try (JsonParser parser = jsonFactory.createParser(ObjectReadContext.empty(), new StringReader(json))) {
            if (parser.nextToken() == null) {
                throw new JsonException("JSON input is empty");
            }
            Object value = readValue(parser, type, Map.of());
            if (parser.nextToken() != null) {
                throw new JsonException("Unexpected trailing JSON content");
            }
            return (T) value;
        } catch (IOException ex) {
            throw new JsonException("Failed to read JSON", ex);
        }
    }

    private void writeValue(JsonGenerator generator, Object value, Type type, Map<TypeVariable<?>, Type> variables) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        Type resolvedType = Types.resolve(type, variables);
        JsonTypeHandler<Object> handler = handler(resolvedType);
        if (handler == null && resolvedType == Object.class) {
            handler = handler(value.getClass());
        }
        if (handler != null) {
            handler.write(new WriterContext(generator, variables), value);
            return;
        }
        if (value instanceof JsonEnum<?, ?> jsonEnum) {
            writeValue(generator, jsonEnum.getValue(), Object.class, Map.of());
            return;
        }
        Class<?> rawType = Types.rawType(resolvedType);
        if (rawType == Object.class) {
            rawType = value.getClass();
            resolvedType = rawType;
        }
        if (writeSimpleValue(generator, value, rawType)) {
            return;
        }
        if (rawType.isArray()) {
            writeArray(generator, value, Types.arrayElementType(resolvedType));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            writeIterable(generator, iterable, Types.collectionElementType(resolvedType));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            writeMap(generator, map, Types.mapValueType(resolvedType));
            return;
        }
        writeObject(generator, value, resolvedType, rawType);
    }

    private boolean writeSimpleValue(JsonGenerator generator, Object value, Class<?> rawType) {
        if (rawType == String.class || rawType == Character.class || rawType == char.class) {
            generator.writeString(String.valueOf(value));
        } else if (rawType == boolean.class || rawType == Boolean.class) {
            generator.writeBoolean((Boolean) value);
        } else if (value instanceof BigInteger bigInteger) {
            generator.writeNumber(bigInteger);
        } else if (value instanceof BigDecimal bigDecimal) {
            generator.writeNumber(bigDecimal);
        } else if (value instanceof Number number) {
            generator.writeNumber(number.toString());
        } else if (rawType.isEnum()) {
            generator.writeString(((Enum<?>) value).name());
        } else if (value instanceof UUID || value instanceof LocalDate || value instanceof LocalDateTime
                || value instanceof OffsetDateTime || value instanceof Instant || value instanceof ZonedDateTime
                || value instanceof Date || value instanceof LocalTime || value instanceof OffsetTime) {
            generator.writeString(value.toString());
        } else {
            return false;
        }
        return true;
    }

    private void writeArray(JsonGenerator generator, Object value, Type elementType) throws IOException {
        generator.writeStartArray();
        int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
            writeValue(generator, Array.get(value, i), elementType, Map.of());
        }
        generator.writeEndArray();
    }

    private void writeIterable(JsonGenerator generator, Iterable<?> iterable, Type elementType) throws IOException {
        generator.writeStartArray();
        for (Object element : iterable) {
            writeValue(generator, element, elementType, Map.of());
        }
        generator.writeEndArray();
    }

    private void writeMap(JsonGenerator generator, Map<?, ?> map, Type valueType) throws IOException {
        generator.writeStartObject();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                throw new JsonException("JSON object keys cannot be null");
            }
            generator.writeName(String.valueOf(entry.getKey()));
            writeValue(generator, entry.getValue(), valueType, Map.of());
        }
        generator.writeEndObject();
    }

    private void writeObject(JsonGenerator generator, Object value, Type type, Class<?> rawType) throws IOException {
        generator.writeStartObject();
        writeObjectFields(generator, value, type, rawType, "");
        generator.writeEndObject();
    }

    private void writeObjectFields(JsonGenerator generator, Object value, Type type, Class<?> rawType, String prefix) throws IOException {
        Reflector<Object> reflector = reflector(rawType);
        Map<TypeVariable<?>, Type> variables = Types.typeVariables(type);
        String[] fields = reflector.getFields();
        for (int i = 0; i < fields.length; i++) {
            Object fieldValue = reflector.get(value, i);
            FieldInfo fieldInfo = reflector.getField(i);
            String flattenPrefix = flattenPrefix(fieldInfo);
            if (flattenPrefix != null) {
                if (fieldValue == null) {
                    continue;
                }
                Type fieldType = Types.resolve(fieldInfo == null ? Object.class : fieldInfo.type(), variables);
                writeFlattened(generator, fieldValue, fieldType, prefix + flattenPrefix);
                continue;
            }
            if (fieldValue == null && !includeNulls) {
                continue;
            }
            generator.writeName(prefix + jsonName(fieldInfo));
            writeValue(generator, fieldValue, fieldInfo == null ? Object.class : fieldInfo.type(), variables);
        }
    }

    private void writeFlattened(JsonGenerator generator, Object value, Type type, String prefix) throws IOException {
        if (value instanceof Map<?, ?> map) {
            Type valueType = Types.mapValueType(type);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    throw new JsonException("JSON object keys cannot be null");
                }
                Object entryValue = entry.getValue();
                if (entryValue == null && !includeNulls) {
                    continue;
                }
                generator.writeName(prefix + entry.getKey());
                writeValue(generator, entryValue, valueType, Map.of());
            }
            return;
        }
        writeObjectFields(generator, value, type, value.getClass(), prefix);
    }

    private Object readValue(JsonParser parser, Type type, Map<TypeVariable<?>, Type> variables) throws IOException {
        Type resolvedType = Types.resolve(type, variables);
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return defaultValue(Types.rawType(resolvedType));
        }
        JsonTypeHandler<Object> handler = handler(resolvedType);
        if (handler != null) {
            return handler.read(new ReaderContext(parser, variables), resolvedType);
        }
        Class<?> rawType = Types.rawType(resolvedType);
        if (rawType == Object.class) {
            return readUntyped(parser);
        }
        if (rawType.isEnum() && JsonEnum.class.isAssignableFrom(rawType)) {
            return readJsonEnum(parser, rawType, variables);
        }
        var val =readSimpleValue(parser, rawType);
        if (val != null) {
            return val;
        }
        if (rawType.isArray()) {
            return readArray(parser, rawType, Types.arrayElementType(resolvedType));
        }
        if (Collection.class.isAssignableFrom(rawType)) {
            return readCollection(parser, rawType, Types.collectionElementType(resolvedType));
        }
        if (Map.class.isAssignableFrom(rawType)) {
            Type keyType = Types.mapKeyType(resolvedType);
            if (Types.rawType(keyType) != String.class) {
                throw new JsonException("Only Map<String, V> is supported for JSON objects");
            }
            return readMap(parser, Types.mapValueType(resolvedType));
        }
        return readObject(parser, resolvedType, rawType);
    }

    private Object readSimpleValue(JsonParser parser, Class<?> rawType) {
        if (rawType == String.class) {
            return parser.getValueAsString();
        }
        if (rawType == char.class || rawType == Character.class) {
            String value = parser.getValueAsString();
            return value == null || value.isEmpty() ? defaultValue(rawType) : value.charAt(0);
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            return parser.getBooleanValue();
        }
        if (rawType == byte.class || rawType == Byte.class) {
            return parser.getByteValue();
        }
        if (rawType == short.class || rawType == Short.class) {
            return parser.getShortValue();
        }
        if (rawType == int.class || rawType == Integer.class) {
            return parser.getIntValue();
        }
        if (rawType == long.class || rawType == Long.class) {
            return parser.getLongValue();
        }
        if (rawType == float.class || rawType == Float.class) {
            return parser.getFloatValue();
        }
        if (rawType == double.class || rawType == Double.class) {
            return parser.getDoubleValue();
        }
        if (rawType == BigInteger.class) {
            return parser.getBigIntegerValue();
        }
        if (rawType == BigDecimal.class) {
            return parser.getDecimalValue();
        }
        if (rawType.isEnum()) {
            return Enum.valueOf(rawType.asSubclass(Enum.class), parser.getValueAsString());
        }
        String value = parser.getValueAsString();
        if (rawType == UUID.class) {
            return UUID.fromString(value);
        }
        if (rawType == LocalDate.class) {
            return LocalDate.parse(value);
        }
        if (rawType == LocalDateTime.class) {
            return LocalDateTime.parse(value);
        }
        if (rawType == OffsetDateTime.class) {
            return OffsetDateTime.parse(value);
        }
        if (rawType == Instant.class) {
            return Instant.parse(value);
        }
        if (rawType == Date.class) {
            try {
                return SimpleDateFormat.getDateInstance().parse(value);
            } catch (ParseException e) {
                throw new JsonException("Invalid date format: " + value, e);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object readJsonEnum(JsonParser parser, Class<?> rawType, Map<TypeVariable<?>, Type> variables) throws IOException {
        Object[] constants = rawType.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new JsonException("Enum " + rawType.getName() + " has no constants to parse a JSON value into");
        }
        Object encoded = readValue(parser, enumValueType(rawType), variables);
        Object result = ((JsonEnum<Object, Object>) constants[0]).parse(encoded);
        if (result == null) {
            throw new JsonException("No " + rawType.getSimpleName() + " constant matches JSON value: " + encoded);
        }
        return result;
    }

    private Type enumValueType(Class<?> enumClass) {
        for (Type genericInterface : enumClass.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType parameterizedType
                    && parameterizedType.getRawType() == JsonEnum.class) {
                return parameterizedType.getActualTypeArguments()[1];
            }
        }
        return Object.class;
    }

    private Object readUntyped(JsonParser parser) {
        return switch (parser.currentToken()) {
            case START_OBJECT -> {
                Map<String, Object> map = new LinkedHashMap<>();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.currentName();
                    parser.nextToken();
                    map.put(fieldName, readUntyped(parser));
                }
                yield map;
            }
            case START_ARRAY -> {
                List<Object> list = new ArrayList<>();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    list.add(readUntyped(parser));
                }
                yield list;
            }
            case VALUE_STRING -> parser.getValueAsString();
            case VALUE_NUMBER_INT -> parser.getLongValue();
            case VALUE_NUMBER_FLOAT -> parser.getDecimalValue();
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NULL -> null;
            default -> throw new JsonException("Unsupported JSON token: " + parser.currentToken());
        };
    }

    private Object readArray(JsonParser parser, Class<?> rawType, Type elementType) throws IOException {
        expect(parser, JsonToken.START_ARRAY);
        List<Object> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(readValue(parser, elementType, Map.of()));
        }
        Class<?> componentType = rawType.getComponentType();
        Object array = Array.newInstance(componentType, values.size());
        for (int i = 0; i < values.size(); i++) {
            Array.set(array, i, values.get(i));
        }
        return array;
    }

    private Collection<?> readCollection(JsonParser parser, Class<?> rawType, Type elementType) throws IOException {
        expect(parser, JsonToken.START_ARRAY);
        Collection<Object> collection = Set.class.isAssignableFrom(rawType) ? new LinkedHashSet<>() : new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            collection.add(readValue(parser, elementType, Map.of()));
        }
        return collection;
    }

    private Map<String, Object> readMap(JsonParser parser, Type valueType) throws IOException {
        expect(parser, JsonToken.START_OBJECT);
        Map<String, Object> map = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            map.put(fieldName, readValue(parser, valueType, Map.of()));
        }
        return map;
    }

    private Object readObject(JsonParser parser, Type type, Class<?> rawType) throws IOException {
        expect(parser, JsonToken.START_OBJECT);
        Reflector<Object> reflector = reflector(rawType);
        Map<TypeVariable<?>, Type> variables = Types.typeVariables(type);
        Object[] fieldValues = new Object[reflector.getFields().length];
        boolean[] presentFields = new boolean[fieldValues.length];

        FlattenPlan plan = flattenPlan(reflector, variables, rawType);

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            Integer fieldIndex = plan.plainByName().get(fieldName);
            if (fieldIndex != null) {
                FieldInfo fieldInfo = reflector.getField(fieldIndex);
                fieldValues[fieldIndex] = readValue(parser, fieldInfo == null ? Object.class : fieldInfo.type(), variables);
                presentFields[fieldIndex] = true;
                continue;
            }
            UnwrapRoute route = plan.unwrapRoutes().get(fieldName);
            if (route != null) {
                ChildModel model = plan.unwrapModels().get(route.parentIndex());
                FieldInfo childField = model.reflector().getField(route.childIndex());
                model.values()[route.childIndex()] = readValue(parser,
                        childField == null ? Object.class : childField.type(), model.variables());
                model.present()[route.childIndex()] = true;
                continue;
            }
            if (plan.anyMapIndex() >= 0 && (plan.anyMapPrefix().isEmpty() || fieldName.startsWith(plan.anyMapPrefix()))) {
                String key = plan.anyMapPrefix().isEmpty() ? fieldName : fieldName.substring(plan.anyMapPrefix().length());
                plan.anyMap().put(key, readValue(parser, plan.anyMapValueType(), variables));
                continue;
            }
            if (failOnUnknownProperties) {
                throw new JsonException("Unknown JSON property '" + fieldName + "' for " + rawType.getName());
            }
            parser.skipChildren();
        }

        for (Map.Entry<Integer, ChildModel> entry : plan.unwrapModels().entrySet()) {
            ChildModel model = entry.getValue();
            if (!anyPresent(model.present())) {
                continue;
            }
            fieldValues[entry.getKey()] = instantiate(model.reflector(), model.variables(), model.values(), model.present());
            presentFields[entry.getKey()] = true;
        }
        if (plan.anyMapIndex() >= 0) {
            fieldValues[plan.anyMapIndex()] = plan.anyMap();
            presentFields[plan.anyMapIndex()] = true;
        }

        return instantiate(reflector, variables, fieldValues, presentFields);
    }

    private Object instantiate(Reflector<Object> reflector, Map<TypeVariable<?>, Type> variables, Object[] fieldValues, boolean[] presentFields) {
        Map<String, Integer> fieldsByName = fieldsByName(reflector);
        ConstructorBinding constructorBinding = constructorBinding(reflector, variables, fieldsByName, fieldValues, presentFields);
        Object instance = constructorBinding.args().length == 0
                ? reflector.newInstance()
                : reflector.newInstance(constructorBinding.args());
        for (int i = 0; i < fieldValues.length; i++) {
            if (presentFields[i] && !constructorBinding.constructorFields()[i]) {
                reflector.set(instance, i, fieldValues[i]);
            }
        }
        return instance;
    }

    private FlattenPlan flattenPlan(Reflector<Object> reflector, Map<TypeVariable<?>, Type> variables, Class<?> rawType) {
        Map<String, Integer> plainByName = new HashMap<>();
        Map<String, UnwrapRoute> unwrapRoutes = new HashMap<>();
        Map<Integer, ChildModel> unwrapModels = new LinkedHashMap<>();
        int anyMapIndex = -1;
        Type anyMapValueType = Object.class;
        String anyMapPrefix = "";

        String[] fields = reflector.getFields();
        for (int i = 0; i < fields.length; i++) {
            FieldInfo fieldInfo = reflector.getField(i);
            String flattenPrefix = flattenPrefix(fieldInfo);
            if (flattenPrefix == null) {
                plainByName.put(fields[i], i);
                if (fieldInfo != null && fieldInfo.alias() != null && !fieldInfo.alias().isBlank()) {
                    plainByName.put(fieldInfo.alias(), i);
                }
                continue;
            }
            Type fieldType = Types.resolve(fieldInfo == null ? Object.class : fieldInfo.type(), variables);
            Class<?> fieldRaw = Types.rawType(fieldType);
            if (Map.class.isAssignableFrom(fieldRaw)) {
                anyMapIndex = i;
                anyMapValueType = Types.mapValueType(fieldType);
                anyMapPrefix = flattenPrefix;
                continue;
            }
            if (fieldRaw == Object.class) {
                throw new JsonException("Cannot flatten field '" + fields[i] + "' of " + rawType.getName()
                        + ": its concrete type is unknown at runtime. Provide a TypeRef so the generic type can be resolved.");
            }
            Reflector<Object> childReflector = reflector(fieldRaw);
            Map<TypeVariable<?>, Type> childVariables = Types.typeVariables(fieldType);
            String[] childFields = childReflector.getFields();
            ChildModel model = new ChildModel(childReflector, childVariables,
                    new Object[childFields.length], new boolean[childFields.length]);
            unwrapModels.put(i, model);
            for (int j = 0; j < childFields.length; j++) {
                FieldInfo childField = childReflector.getField(j);
                unwrapRoutes.put(flattenPrefix + childFields[j], new UnwrapRoute(i, j));
                if (childField != null && childField.alias() != null && !childField.alias().isBlank()) {
                    unwrapRoutes.put(flattenPrefix + childField.alias(), new UnwrapRoute(i, j));
                }
            }
        }
        Map<String, Object> anyMap = anyMapIndex >= 0 ? new LinkedHashMap<>() : Map.of();
        return new FlattenPlan(plainByName, unwrapRoutes, unwrapModels, anyMapIndex, anyMapValueType, anyMapPrefix, anyMap);
    }

    private boolean anyPresent(boolean[] present) {
        for (boolean value : present) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    private ConstructorBinding constructorBinding(Reflector<Object> reflector,
                                                  Map<TypeVariable<?>, Type> variables,
                                                  Map<String, Integer> fieldsByName,
                                                  Object[] fieldValues,
                                                  boolean[] presentFields) {
        ClassInfo classInfo = reflector.getClassInfo();
        ParameterInfo[] parameters = classInfo == null || classInfo.params() == null
                ? new ParameterInfo[0]
                : classInfo.params();
        Object[] args = new Object[parameters.length];
        boolean[] constructorFields = new boolean[fieldValues.length];
        for (int i = 0; i < parameters.length; i++) {
            ParameterInfo parameter = parameters[i];
            Type parameterType = Types.resolve(parameter.type(), variables);
            Integer fieldIndex = fieldsByName.get(parameter.name());
            if (fieldIndex == null) {
                args[i] = defaultValue(Types.rawType(parameterType));
                continue;
            }
            constructorFields[fieldIndex] = true;
            args[i] = presentFields[fieldIndex]
                    ? fieldValues[fieldIndex]
                    : defaultValue(Types.rawType(parameterType));
        }
        return new ConstructorBinding(args, constructorFields);
    }

    private Map<String, Integer> fieldsByName(Reflector<Object> reflector) {
        Map<String, Integer> fieldsByName = new HashMap<>();
        String[] fields = reflector.getFields();
        for (int i = 0; i < fields.length; i++) {
            fieldsByName.put(fields[i], i);
            FieldInfo fieldInfo = reflector.getField(i);
            if (fieldInfo != null && fieldInfo.alias() != null && !fieldInfo.alias().isBlank()) {
                fieldsByName.put(fieldInfo.alias(), i);
            }
        }
        return fieldsByName;
    }

    private String jsonName(FieldInfo fieldInfo) {
        if (fieldInfo != null && fieldInfo.alias() != null && !fieldInfo.alias().isBlank()) {
            return fieldInfo.alias();
        }
        return fieldInfo == null ? "" : fieldInfo.name();
    }

    private String flattenPrefix(FieldInfo fieldInfo) {
        if (fieldInfo == null) {
            return null;
        }
        for (AnnotationMeta annotation : fieldInfo.annotations()) {
            if (FLATTEN_ANNOTATION.equals(annotation.type())) {
                String prefix = annotation.stringValue("prefix");
                return prefix == null ? "" : prefix;
            }
        }
        return null;
    }

    private Object defaultValue(Class<?> rawType) {
        if (!rawType.isPrimitive()) {
            return null;
        }
        if (rawType == boolean.class) {
            return false;
        }
        if (rawType == char.class) {
            return '\0';
        }
        if (rawType == byte.class) {
            return (byte) 0;
        }
        if (rawType == short.class) {
            return (short) 0;
        }
        if (rawType == int.class) {
            return 0;
        }
        if (rawType == long.class) {
            return 0L;
        }
        if (rawType == float.class) {
            return 0F;
        }
        if (rawType == double.class) {
            return 0D;
        }
        return null;
    }

    private void expect(JsonParser parser, JsonToken expectedToken) {
        if (parser.currentToken() != expectedToken) {
            throw new JsonException("Expected " + expectedToken + " but got " + parser.currentToken());
        }
    }

    @SuppressWarnings("unchecked")
    private Reflector<Object> reflector(Class<?> rawType) {
        Reflector<?> reflector = ReflectorRegistry.get(rawType);
        if (reflector == null) {
            throw new JsonException("No Reflector registered for " + rawType.getName()
                    + ". Add @Reflect and run kyra-processor so the ServiceLoader installer is generated.");
        }
        return (Reflector<Object>) reflector;
    }

    @SuppressWarnings("unchecked")
    private JsonTypeHandler<Object> handler(Type type) {
        for (JsonTypeHandler<?> handler : handlers) {
            if (handler.supports(type)) {
                return (JsonTypeHandler<Object>) handler;
            }
        }
        return null;
    }

    private record ConstructorBinding(Object[] args, boolean[] constructorFields) {
    }

    private record UnwrapRoute(int parentIndex, int childIndex) {
    }

    private record ChildModel(Reflector<Object> reflector, Map<TypeVariable<?>, Type> variables,
                              Object[] values, boolean[] present) {
    }

    private record FlattenPlan(Map<String, Integer> plainByName,
                               Map<String, UnwrapRoute> unwrapRoutes,
                               Map<Integer, ChildModel> unwrapModels,
                               int anyMapIndex,
                               Type anyMapValueType,
                               String anyMapPrefix,
                               Map<String, Object> anyMap) {
    }

    static final class Builder implements JsonMapper.Builder {
        private final List<JsonTypeHandler<?>> handlers = new ArrayList<>();
        private boolean failOnUnknownProperties;
        private boolean includeNulls = false;

        @Override
        public Builder register(JsonTypeHandler<?> handler) {
            handlers.add(handler);
            return this;
        }

        @Override
        public Builder failOnUnknownProperties(boolean failOnUnknownProperties) {
            this.failOnUnknownProperties = failOnUnknownProperties;
            return this;
        }

        @Override
        public Builder includeNulls(boolean includeNulls) {
            this.includeNulls = includeNulls;
            return this;
        }

        @Override
        public JsonMapper build() {
            return new DefaultJsonMapper(handlers, failOnUnknownProperties, includeNulls);
        }
    }

    private final class WriterContext implements JsonWriterContext {
        private final JsonGenerator generator;
        private final Map<TypeVariable<?>, Type> variables;

        private WriterContext(JsonGenerator generator, Map<TypeVariable<?>, Type> variables) {
            this.generator = generator;
            this.variables = variables;
        }

        @Override
        public JsonGenerator generator() {
            return generator;
        }

        @Override
        public void write(Object value) {
            write(value, value == null ? Object.class : value.getClass());
        }

        @Override
        public void write(Object value, Type type) {
            try {
                writeValue(generator, value, type, variables);
            } catch (IOException ex) {
                throw new JsonException("Failed to write JSON value", ex);
            }
        }
    }

    private final class ReaderContext implements JsonReaderContext {
        private final JsonParser parser;
        private final Map<TypeVariable<?>, Type> variables;

        private ReaderContext(JsonParser parser, Map<TypeVariable<?>, Type> variables) {
            this.parser = parser;
            this.variables = variables;
        }

        @Override
        public JsonParser parser() {
            return parser;
        }

        @Override
        public <T> T read(Class<T> type) {
            return read((Type) type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T read(Type type) {
            try {
                return (T) readValue(parser, type, variables);
            } catch (IOException ex) {
                throw new JsonException("Failed to read JSON value", ex);
            }
        }
    }
}
