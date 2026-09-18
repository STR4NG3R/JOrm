package io.github.str4ng3r.utils;

import io.github.str4ng3r.common.Selector;
import io.github.str4ng3r.common.SqlParameter;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class JDBCUtils {

    JormLogger jormLogger;

    public JDBCUtils(JormLogger jormLogger) {
        this.jormLogger = jormLogger;
    }

    public void addParameters(PreparedStatement ps, List<Object> parameters) throws SQLException {
        ps.clearParameters();
        for (int i = 0; i < parameters.size(); i++) ps.setObject(i + 1, parameters.get(i));
    }

    /**
     * Appends a soft-delete filter to the SQL when deletedAtColumn is provided.
     * Detects whether the query already has a WHERE clause to use AND or WHERE.
     */
    public static String applySoftDeleteFilter(String sql, String deletedAtColumn) {
        if (deletedAtColumn == null) return sql;
        String connector = sql.toUpperCase().contains(" WHERE ") ? " AND " : " WHERE ";
        return sql + connector + deletedAtColumn + " IS NULL";
    }

    public int getCount(Connection connection, Selector s, SqlParameter sqlParameter, String alias)
            throws SQLException {
        this.jormLogger.info(sqlParameter.toString());
        this.jormLogger.startRecord("count-" + alias, sqlParameter.getSql());
        PreparedStatement ps = connection.prepareStatement(s.getCount(sqlParameter.getSql()));
        addParameters(ps, sqlParameter.getListParameters());
        ResultSet rs = ps.executeQuery();
        if (rs.next())
            return rs.getInt(1);
        this.jormLogger.endRecord(alias);
        return 0;
    }

    public ResultSet createResultSet(Selector selector, Connection connection, String alias)
            throws SQLException, InvalidSqlGenerationException {
        return createResultSet(selector, connection, alias, null);
    }

    public ResultSet createResultSet(Selector selector, Connection connection, String alias, String deletedAtColumn)
            throws SQLException, InvalidSqlGenerationException {
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        String sql = applySoftDeleteFilter(sqlParameter.getSql(), deletedAtColumn);
        this.jormLogger.info(sql);
        this.jormLogger.startRecord(alias, sql);
        PreparedStatement ps = connection.prepareStatement(sql);
        addParameters(ps, sqlParameter.getListParameters());
        ResultSet rs = ps.executeQuery();
        jormLogger.endRecord(alias);
        return rs;
    }
}
