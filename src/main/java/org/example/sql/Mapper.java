package org.example.sql;

import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import org.example.*;
import org.example.utils.JormLogger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;

import static org.example.sql.ScannerEntity.*;

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
        jormLogger.startRecord("map-" + alias );
        Class<T> clazz = validateEntity(entity);
        EntityMetaData processedEntity = new EntityMetaData();
        getTableNameFromEntity(entity, processedEntity);
        if (!entitiesRegistry.containsKey(clazz)) registryEntity(clazz);
        processedEntity = entitiesRegistry.get(clazz);
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
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                T obj = clazz.getDeclaredConstructor().newInstance();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    Object columnValue = rs.getObject(i);
                    if (columnValue != null) setFieldValue(obj, columnName, columnValue);
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
