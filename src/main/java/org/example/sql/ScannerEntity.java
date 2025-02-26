package org.example.sql;

import io.github.str4ng3r.common.Table;
import org.example.*;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

class ScannerEntity {
    static HashMap<String, EntityMetaData> entities = new HashMap<>();

    static void findEntities(boolean forceUpdate) {
        if (!entities.isEmpty()) return;
        Reflections reflections = new Reflections(Runner.configuration.scanPath);
        // Find all classes annotated with @Entity
        Set<Class<?>> annotatedClasses = reflections.getTypesAnnotatedWith(Entity.class);
        // Print the found classes
        for (Class<?> clazz : annotatedClasses) {
            Entity e = clazz.getAnnotation(Entity.class);
            EntityMetaData entityMetaData = new EntityMetaData();
            entityMetaData.tableName = e.name();
            entityMetaData.schema = e.schema();
            entityMetaData.db = e.database();
            getColumnsFromEntity(clazz, entityMetaData);

            entities.put(createKey(entityMetaData.tableName, entityMetaData.db, entityMetaData.schema),
                    entityMetaData);
        }
    }

    public static String createKey(String name, String db, String schema) {
        return ((db == null || db.isEmpty() ? "" : db + ".")
                + (db == null || schema.isEmpty() ? "" : schema + ".")
                + name);
    }

    public static <T> void getTableNameFromEntity(T entity, EntityMetaData processedEntity) {
        Entity e = entity.getClass().getAnnotation(Entity.class);
        processedEntity.tableName = e.name();
        processedEntity.db = e.database();
        processedEntity.schema = e.schema();
    }

    public static void iterateFields(Class<?> clazz, Consumer<Field> consumer) {
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            consumer.accept(field);
        }
    }

    public static <T> void getValuesFromEntity(Class<?> clazz, T entity, EntityMetaData processedEntity) {
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

    public static void getColumnsFromEntity(Class<?> clazz, EntityMetaData processedEntity) {
        List<String> columns = new ArrayList<>();
        iterateFields(clazz,
                field -> {
                    if (field.isAnnotationPresent(Column.class)) columns.add(field.getName());
                    else if (field.isAnnotationPresent(Id.class)) processedEntity.setColumnId(field.getName());
                    else if (field.isAnnotationPresent(CreatedAt.class)) processedEntity.setColumnCreatedAt(field.getName());
                    else if (field.isAnnotationPresent(UpdatedAt.class)) processedEntity.setColumnUpdatedAt(field.getName());
                    else if (field.isAnnotationPresent(DeletedAt.class)) processedEntity.setColumnDeletedAt(field.getName());
                });
        processedEntity.setColumns(columns);
    }

    public static EntityMetaData getEntityFromTableName(Table tableName) {
        String k = createKey(tableName.name, tableName.database, tableName.schema);
        if (entities.containsKey(k)){
            return entities.get(k);
        }

            return null;
    }
}
