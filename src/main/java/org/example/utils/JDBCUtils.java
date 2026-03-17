package org.example.utils;

import io.github.str4ng3r.common.Selector;
import io.github.str4ng3r.common.SqlParameter;
import io.github.str4ng3r.exceptions.InvalidSqlGenerationException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class JDBCUtils{

    JormLogger jormLogger;

    public JDBCUtils(JormLogger jormLogger) {
        this.jormLogger = jormLogger;
    }

    public void addParameters(PreparedStatement ps, List<Object> parameters) throws SQLException {
        ps.clearParameters();
        for (int i = 0; i < parameters.size(); i++) ps.setObject(i + 1, parameters.get(i));
    }

    public int getCount(Connection connection, Selector s, SqlParameter sqlParameter, boolean withDeleted, String alias) throws SQLException {
        this.jormLogger.info(sqlParameter.toString());
        this.jormLogger.startRecord("count-" + alias, sqlParameter.sql);
        PreparedStatement ps = connection.prepareStatement(s.getCount(sqlParameter.sql));
        addParameters(ps, sqlParameter.getListParameters());
        ResultSet rs = ps.executeQuery();
        if (rs.next())
            return rs.getInt(1);
        this.jormLogger.endRecord(alias);
        return 0;
    }


    public ResultSet createResultSet(Selector selector, Connection connection, boolean withDeleted, String alias) throws SQLException, InvalidSqlGenerationException {
        //selector.setWithDeleted(withDeleted);
        SqlParameter sqlParameter = selector.getSqlAndParameters();
        this.jormLogger.info(sqlParameter.toString());
        this.jormLogger.startRecord(alias, sqlParameter.sql);
        PreparedStatement ps = connection.prepareStatement(sqlParameter.sql);
        addParameters(ps, sqlParameter.getListParameters());
        ResultSet rs = ps.executeQuery();
        jormLogger.endRecord(alias);
        return rs;
    }
}
