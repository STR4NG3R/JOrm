package org.example;

import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.*;
import java.util.function.Consumer;

public class Mapper<T> {

    public void iterateFields(Class<T> clazz, Consumer<Field> consumer) {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            consumer.accept(field);
        }
    }

    public void getColumnsFromEntity(Class<T> clazz, ProcessedEntity processedEntity) {
        List<String> columns = new ArrayList<>();
        iterateFields(clazz,
                field -> {
                    if (field.isAnnotationPresent(Column.class)) {
                        columns.add(field.getName());
                        return;
                    }
                    if (field.isAnnotationPresent(Id.class)) processedEntity.setColumnId(field.getName());
                });
        processedEntity.setColumns(columns);
    }

    public void getValuesFromEntity(Class<T> clazz, T entity, ProcessedEntity processedEntity) {
        List<Object> values = new ArrayList<>();
        iterateFields(clazz,
                field -> {
                    try {
                        if (field.isAnnotationPresent(Column.class)) {
                            values.add(field.get(entity));
                            return;
                        }
                        if (field.isAnnotationPresent(Id.class)) {
                            processedEntity.setColumnIdValue(field.get(entity));
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
        processedEntity.setValues(values);
    }


    Class<T> validateEntity(T entity) throws InvalidSqlGenerationException {
        Class<T> clazz = (Class<T>) entity.getClass();
        if (!clazz.isAnnotationPresent(Entity.class)) {
            throw new InvalidSqlGenerationException("Class must be annotated with @Entity");
        }
        return clazz;
    }

    public String getTableNameFromEntity(T entity) {
        return entity.getClass().getAnnotation(Entity.class).name();
    }

    public ProcessedEntity mapFromEntitiy(T entity) throws InvalidSqlGenerationException {
        Class<T> clazz = validateEntity(entity);
        ProcessedEntity processedEntity = new ProcessedEntity();
        processedEntity.setTableName(getTableNameFromEntity(entity));
        getColumnsFromEntity(clazz, processedEntity);
        getValuesFromEntity(clazz, entity, processedEntity);
        return processedEntity;
    }


    public T mapFromHashMap(Map<String, Object> data, Class<T> clazz) {
        try {
            T obj = clazz.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();    // e.g., "address.city"
                Object value = entry.getValue(); // e.g., "Durango"

                setFieldValue(obj, key, value);
            }
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<T> mapFromResultSet(ResultSet rs, Class<T> clazz) {
        List<T> list = new ArrayList<>();

        try {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                T obj = clazz.getDeclaredConstructor().newInstance();

                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    Object columnValue = rs.getObject(i);

                    if (columnValue != null) {
                        setFieldValue(obj, columnName, columnValue);
                    }
                }
                list.add(obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            if (columnName.contains(".")) {
                loopNestedClass(obj, Arrays.asList(columnName.split("\\.")), 0, columnValue);
            } else {
                Field field = obj.getClass().getDeclaredField(columnName);
                field.setAccessible(true);
                field.set(obj, columnValue);
            }
        } catch (NoSuchFieldException ignored) {
            // Log ignored fields for debugging
            System.out.println("⚠️ Warning: Field '" + columnName + "' not found in " + obj.getClass().getSimpleName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class ProcessedEntity {
        List<String> columns;
        List<Object> values;
        String tableName;
        String columnId;
        Object columnIdValue;

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> columns) {
            this.columns = columns;
        }

        public List<Object> getValues() {
            return values;
        }

        public void setValues(List<Object> values) {
            this.values = values;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public void setColumnIdValue(Object columnIdValue) {
            this.columnIdValue = columnIdValue;
        }

        public String getColumnId() {
            return columnId;
        }

        public Object getColumnIdValue() {
            return columnIdValue;
        }

        public void setColumnId(String columnId) {
            this.columnId = columnId;
        }
    }
}
