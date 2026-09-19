package io.github.str4ng3r.sql;

import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import io.github.str4ng3r.utils.JormLogger;
import io.github.str4ng3r.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

import static io.github.str4ng3r.sql.ScannerEntity.*;

public class Mapper<T> {

    JormLogger jormLogger;
    String alias;

    public Mapper(JormLogger jormLogger, String alias) {
        this.jormLogger = jormLogger;
        this.alias = alias == null? "": alias;

    }

    Class<T> validateEntity(T entity) throws InvalidSqlGenerationException {
        Class<T> clazz = (Class<T>) entity.getClass();
        if (!clazz.isAnnotationPresent(Entity.class)) {
            throw new InvalidSqlGenerationException("Class must be annotated with @Entity");
        }
        return clazz;
    }


    public EntityMetaData mapFromEntity(T entity) throws InvalidSqlGenerationException {
        jormLogger.startRecord("map-" + alias);
        Class<T> clazz = validateEntity(entity);
        if (!entitiesRegistry.containsKey(clazz)) registryEntity(clazz);
        EntityMetaData processedEntity = entitiesRegistry.get(clazz).cloneStructure();
        getValuesFromEntity(clazz, entity, processedEntity);
        jormLogger.endRecord("map-" + alias);
        return processedEntity;
    }

    public void flushValues(EntityMetaData e) {
        while (!e.values.isEmpty()) e.values.remove(0);
    }

    public T mapFromHashMap(Map<String, Object> data, Class<T> clazz) {
        jormLogger.startRecord("map-" + alias);
        try {
            T obj = clazz.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();    // e.g., "address.city"
                Object value = entry.getValue(); // e.g., "Durango"

                setFieldValue(obj, key, value);
            }
            return obj;
        } catch (Exception e) {
            jormLogger.error("Unable to map from hashmap", e);
            return null;
        } finally {
            jormLogger.endRecord("map-" + alias);
        }
    }

    public List<T> mapFromResultSet(ResultSet rs, Class<T> clazz) {
        jormLogger.startRecord("map-" + alias);
        List<T> list = new ArrayList<>();
        try {
            // Ensure the reflection cache (fields + constructor) is built once.
            if (clazz.isAnnotationPresent(Entity.class) && !entitiesRegistry.containsKey(clazz))
                registryEntity(clazz);
            EntityMetaData meta = entitiesRegistry.get(clazz);
            Map<String, Field> fieldCache = meta != null ? meta.getFieldCache() : null;

            @SuppressWarnings("unchecked")
            Constructor<T> ctor = meta != null && meta.getNoArgConstructor() != null
                    ? (Constructor<T>) meta.getNoArgConstructor()
                    : clazz.getDeclaredConstructor();
            ctor.setAccessible(true);

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Column labels are identical for every row: read them once.
            String[] labels = new String[columnCount + 1];
            for (int i = 1; i <= columnCount; i++)
                labels[i] = metaData.getColumnLabel(i);

            while (rs.next()) {
                T obj = ctor.newInstance();
                for (int i = 1; i <= columnCount; i++) {
                    Object columnValue = readColumn(rs, i, labels[i], fieldCache);
                    if (columnValue != null) setFieldValue(obj, labels[i], columnValue, fieldCache);
                }
                list.add(obj);
            }
        } catch (Exception e) {
            jormLogger.error("Unable to map manually", e);
        }
        jormLogger.endRecord("map-"+alias);
        return list;
    }

    /**
     * Reads a column, asking the driver to convert it to the target field type
     * when known (JDBC 4.1 getObject(int, Class)). This lets vendor-specific
     * types (e.g. oracle.sql.TIMESTAMP) be converted to java.sql.Timestamp by the
     * driver itself, keeping JOrm free of any vendor dependency. Falls back to a
     * plain getObject when the type is unknown or the driver rejects the request.
     */
    private Object readColumn(ResultSet rs, int index, String label, Map<String, Field> fieldCache) {
        try {
            Field field = fieldCache != null && label != null && !label.contains(".")
                    ? fieldCache.get(label) : null;
            if (field != null) {
                Class<?> t = field.getType();
                if (t == java.sql.Timestamp.class || t == java.sql.Date.class
                        || t == java.sql.Time.class || t == String.class
                        || Number.class.isAssignableFrom(t)) {
                    try {
                        return rs.getObject(index, t);
                    } catch (Exception ignored) {
                        // Driver may not support getObject(int, Class) for this type.
                    }
                }
            }
            return rs.getObject(index);
        } catch (SQLException e) {
            jormLogger.error("Unable to read column " + index, e);
            return null;
        }
    }

    private void loopNestedClass(Object obj, List<String> columnNames, int index, Object columnValue)
            throws IllegalAccessException, NoSuchMethodException, NoSuchFieldException, InvocationTargetException, InstantiationException {

        // Stop when we reach the last element in columnNames
        if (index > columnNames.size()) {
            Field field = obj.getClass().getDeclaredField(columnNames.get(index - 1));
            field.setAccessible(true);
            field.set(obj, columnValue);
            return;
        }

        String parentFieldName = columnNames.get(index);
        Field parentField = obj.getClass().getDeclaredField(parentFieldName);
        parentField.setAccessible(true);

        Object parentObj = parentField.get(obj);
        if (parentObj == null) {
            parentObj = parentField.getType().getDeclaredConstructor().newInstance();
            parentField.set(obj, parentObj);
        }


        // Recursive call to handle deeper nested fields
        loopNestedClass(parentObj, columnNames, index + 1, columnValue);
    }

    /**
     * Cache-aware field setter used by mapFromResultSet.
     * Falls back to reflective lookup for nested columns or when the cache misses.
     */
    private void setFieldValue(T obj, String columnName, Object columnValue, Map<String, Field> fieldCache) {
        if (columnName.contains(".")) {
            setFieldValue(obj, columnName, columnValue); // nested path: use reflective walker
            return;
        }
        if (fieldCache != null) {
            Field field = fieldCache.get(columnName);
            if (field != null) {
                try {
                    field.set(obj, coerce(columnValue, field.getType())); // already setAccessible(true)
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    // A single incompatible column must not abort the whole row.
                    jormLogger.error("Unable to set value on mapping for column '" + columnName
                            + "' (value type " + columnValue.getClass().getName()
                            + " -> field " + field.getType().getName() + ")", e);
                }
                return;
            }
            jormLogger.warn("⚠️ Warning: Field '" + columnName + "' not found in " + obj.getClass().getSimpleName());
            return;
        }
        // No cache available: fall back to the reflective path.
        setFieldValue(obj, columnName, columnValue);
    }

    /**
     * Coerces a JDBC-returned value to the target field type when they don't match.
     * Notably, Oracle returns NUMBER columns as BigDecimal, which would fail to be
     * assigned to Integer/Long/etc. fields; this bridges those common mismatches.
     */
    static Object coerce(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        if (value instanceof Number) {
            Number n = (Number) value;
            if (targetType == Integer.class || targetType == int.class) return n.intValue();
            if (targetType == Long.class || targetType == long.class) return n.longValue();
            if (targetType == Double.class || targetType == double.class) return n.doubleValue();
            if (targetType == Float.class || targetType == float.class) return n.floatValue();
            if (targetType == Short.class || targetType == short.class) return n.shortValue();
            if (targetType == Byte.class || targetType == byte.class) return n.byteValue();
            if (targetType == java.math.BigDecimal.class && value instanceof java.math.BigInteger)
                return new java.math.BigDecimal((java.math.BigInteger) value);
            if (targetType == Boolean.class || targetType == boolean.class) return n.intValue() != 0;
        }

        // Temporal coercions. Some drivers (notably Oracle) return their own
        // date/time subclasses or java.time types; normalize to java.sql.Timestamp/Date.
        if (targetType == java.sql.Timestamp.class) {
            if (value instanceof java.sql.Timestamp) return value;
            if (value instanceof java.util.Date) return new java.sql.Timestamp(((java.util.Date) value).getTime());
            if (value instanceof java.time.LocalDateTime) return java.sql.Timestamp.valueOf((java.time.LocalDateTime) value);
            if (value instanceof java.time.LocalDate)
                return java.sql.Timestamp.valueOf(((java.time.LocalDate) value).atStartOfDay());
        }
        if (targetType == java.sql.Date.class) {
            if (value instanceof java.util.Date) return new java.sql.Date(((java.util.Date) value).getTime());
            if (value instanceof java.time.LocalDate) return java.sql.Date.valueOf((java.time.LocalDate) value);
        }

        // String target from any value
        if (targetType == String.class) return value.toString();
        // Leave as-is; reflection will throw if truly incompatible.
        return value;
    }

    private void setFieldValue(T obj, String columnName, Object columnValue) {
        try {
            if (columnName.contains(".")) loopNestedClass(obj, Arrays.asList(columnName.split("\\.")), 0, columnValue);
            else {
                Field field = obj.getClass().getDeclaredField(columnName);
                field.setAccessible(true);
                field.set(obj, columnValue);
            }
        } catch (NoSuchFieldException ignored) {
            // Log ignored fields for debugging
            jormLogger.warn("⚠️ Warning: Field '" + columnName + "' not found in " + obj.getClass().getSimpleName());
        } catch (Exception e) {
            jormLogger.error("Unable to set value on mapping", e);
        }
    }

}
