package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.core.EnumSupport;
import org.byteora.kyra.core.IEnum;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public final class TypeConverter {

    private CustomTypeConverter[] customConverters = null;

    public TypeConverter register(CustomTypeConverter converter) {
        if (converter == null) {
            return this;
        }
        if (customConverters == null) {
            customConverters = new CustomTypeConverter[]{converter};
            return this;
        }
        customConverters = Arrays.copyOf(customConverters, customConverters.length + 1);
        customConverters[customConverters.length - 1] = converter;
        return this;
    }

    public void clearCustomConverters() {
        customConverters = null;
    }

    public <T> T cast(ResultSet resultSet, int index, Class<T> targetType) throws SQLException {
        return cast(resultSet, index, targetType, null, null);
    }

    public <T> T cast(ResultSet resultSet, int index, Class<T> targetType, String columnName, String fieldName) throws SQLException {
        try {
            return doCast(resultSet, index, targetType);
        } catch (SqlExecutorException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw conversionFailure(resultSet.getObject(index), targetType, columnName, fieldName, ex);
        }
    }

    public Object fieldToColumn(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof IEnum<?, ?>) {
            return EnumSupport.toValue(value);
        }
        if (customConverters == null || customConverters.length == 0) {
            return value;
        }
        Class<?> sourceType = value.getClass();
        for (CustomTypeConverter converter : customConverters) {
            if (converter.supports(sourceType)) {
                return converter.fieldToColumn(value);
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> T doCast(ResultSet resultSet, int index, Class<T> targetType) throws SQLException {
        if (customConverters != null) {
            for (CustomTypeConverter converter : customConverters) {
                if (converter.supports(targetType)) {
                    return converter.columnToField(resultSet, index, targetType);
                }
            }
        }
        if (EnumSupport.isIEnum(targetType)) {
            Object columnValue = resultSet.getObject(index, (Class<?>) EnumSupport.valueType(targetType));
            if (columnValue == null) {
                return null;
            }
            return (T) EnumSupport.parse(targetType.asSubclass(Enum.class), columnValue);
        }
        return resultSet.getObject(index, targetType);
    }

    private SqlExecutorException conversionFailure(Object value, Class<?> targetType, String columnName, String fieldName, RuntimeException cause) {
        String sourceType = value == null ? "null" : value.getClass().getName();
        StringBuilder message = new StringBuilder("Failed to convert result");
        if (columnName != null || fieldName != null) {
            message.append(" from");
            if (columnName != null) {
                message.append(" column '").append(columnName).append("'");
            }
            if (fieldName != null) {
                message.append(" to field '").append(fieldName).append("'");
            }
        }
        message.append(" from type ").append(sourceType)
                .append(" to type ").append(targetType.getName());
        return new SqlExecutorException(message.toString(), cause);
    }
}
