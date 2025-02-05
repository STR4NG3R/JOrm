package org.example.sql;

import io.github.str4ng3r.common.*;
import io.github.str4ng3r.exceptions.InvalidCurrentPageException;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;
import org.example.Mapper;
import org.example.utils.JDBCUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public class Runner<T> {

    Connection connection;
    Selector selector;

    public enum ISOLATION {
        NONE(0),
        READ_UNCOMMITTED(1),
        READ_COMMITED(2),
        REPEATABLE_READ(4),
        SERIALIZABLE(8);

        final int isolationValue;

        ISOLATION(int value) {
            isolationValue = value;
        }
    }

    public void setTransaction(ISOLATION isolation) throws SQLException {
        connection.setTransactionIsolation(isolation.isolationValue);
    }

    Selector getSelector() {
        if (selector == null) selector = new Selector();
        return selector;
    }


    public Runner<T> setConnection(Connection connection) {
        this.connection = connection;
        return this;
    }

    public List<T> select(Selector selector, Function<ResultSet, T> consumer) throws InvalidSqlGenerationException, SQLException {
        ResultSet rs = JDBCUtils.createResultSet(selector, connection);
        ArrayList<T> list = new ArrayList<>();
        while (rs.next()) list.add(consumer.apply(rs));
        return list;
    }

    public List<T> select(Selector selector, Class<T> clazz) throws SQLException, InvalidSqlGenerationException {
        ResultSet rs = JDBCUtils.createResultSet(selector, connection);
        Mapper<T> mapper = new Mapper<>();
        return mapper.mapFromResultSet(rs, clazz);
    }

    public Template<List<T>> selectPaginated(int currentPage, int pageSize, Selector selector, Class<T> clazz) throws InvalidSqlGenerationException, SQLException, InvalidCurrentPageException {
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        int count = JDBCUtils.getCount(connection, selector, sqlParameter);
        selector.setPagination(sqlParameter, new Pagination(pageSize, count, currentPage));
        ResultSet rs = JDBCUtils.createResultSet(selector, connection);
        return new Template<List<T>>(sqlParameter, new Mapper<T>().mapFromResultSet(rs, clazz));
    }

    public Template<List<T>> selectPaginated(int currentPage, int pageSize, Selector selector, Function<ResultSet, T> consumer) throws SQLException, InvalidSqlGenerationException, InvalidCurrentPageException {
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        int count = JDBCUtils.getCount(connection, selector, sqlParameter);
        selector.setPagination(sqlParameter, new Pagination(pageSize, count, currentPage));
        ResultSet rs = JDBCUtils.createResultSet(selector, connection);
        ArrayList<T> list = new ArrayList<>();
        while (rs.next()) list.add(consumer.apply(rs));
        return new Template<>(sqlParameter, list);
    }

    public int update(Update update, HashMap<String, Object> payload) throws InvalidSqlGenerationException, SQLException {
        payload.forEach((key, value) -> {
            update.addParameter(key, (String) value);
        });
        SqlParameter sqlParameter = update.getSqlAndParameters();
        PreparedStatement ps = connection.prepareStatement(sqlParameter.sql);
        return ps.executeUpdate();
    }

    public int update(Update update) throws InvalidSqlGenerationException, SQLException {
        SqlParameter sqlParameter = update.getSqlAndParameters();
        System.out.println(sqlParameter);
        PreparedStatement ps = connection.prepareStatement(sqlParameter.sql);
        JDBCUtils.addParameters(ps, sqlParameter.getListParameters());
        return ps.executeUpdate();
    }

    public int delete(Delete delete) throws InvalidSqlGenerationException, SQLException {
        SqlParameter sqlParameter = delete.getSqlAndParameters();
        PreparedStatement ps = connection.prepareStatement(sqlParameter.sql);
        JDBCUtils.addParameters(ps, sqlParameter.getListParameters());
        return ps.executeUpdate();
    }

    public int insert(Class<T> clazz, T data) throws InvalidSqlGenerationException, IllegalAccessException, SQLException {
        Mapper<T> mapper = new Mapper<>();
        Mapper.ProcessedEntity processedEntity = mapper.mapFromEntitiy(data);
        String columns = String.join(",", processedEntity.getColumns());

        if (processedEntity.getColumnId() != null) {
            List<T> result = select(new Selector()
                            .select(
                                    processedEntity.getTableName(),
                                    columns
                            ).where(processedEntity.getColumnId() + " = :id", (p) -> {
                                p.put("id", processedEntity.getColumnIdValue());
                            })
                    , clazz
            );
            if (!result.isEmpty()) {
                Update u = new Update().from(processedEntity.getTableName())
                        .where(processedEntity.getColumnId() + " = :id", (p) -> {
                            p.put("id", processedEntity.getColumnIdValue());
                        });
                for (int i = 0; i < processedEntity.getColumns().size(); i++) {
                    Object value = processedEntity.getValues().get(i);
                    if (value != null) {
                        String column = processedEntity.getColumns().get(i);
                        u.setColumnsValuesToUpdate((cv) ->
                                cv.put(column, value));
                    }
                }
                return update(u);
            }
        }

        return commonInsert(processedEntity.getTableName(), clazz, processedEntity);
    }


    public void insert(Class<T> clazz, List<T> data) throws InvalidSqlGenerationException, IllegalAccessException, SQLException {
        for (T d : data) insert(clazz, d);
    }

    protected int commonInsert(String tableName, Class<T> clazz, Mapper.ProcessedEntity processedEntity) throws InvalidSqlGenerationException, IllegalAccessException, SQLException {
        String sql = new Insert(clazz.getSimpleName())
                .setColumns(String.join(", ", processedEntity.getColumns()))
                .setTable(tableName)
                .setValues(processedEntity.getValues().toArray(new Object[0]))
                .write();
        System.out.println(sql);
        PreparedStatement ps = connection.prepareStatement(sql);
        JDBCUtils.addParameters(ps, processedEntity.getValues());
        return ps.executeUpdate();
    }
}
