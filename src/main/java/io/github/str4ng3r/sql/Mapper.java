package io.github.str4ng3r.sql;

import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import io.github.str4ng3r.utils.JormLogger;
import io.github.str4ng3r.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
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
                    Object columnValue = rs.getObject(i);
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
                    field.set(obj, columnValue); // already setAccessible(true)
                } catch (IllegalAccessException e) {
                    jormLogger.error("Unable to set value on mapping", e);
                }
                return;
            }
            jormLogger.warn("⚠️ Warning: Field '" + columnName + "' not found in " + obj.getClass().getSimpleName());
            return;
        }
        // No cache available: fall back to the reflective path.
        setFieldValue(obj, columnName, columnValue);
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
