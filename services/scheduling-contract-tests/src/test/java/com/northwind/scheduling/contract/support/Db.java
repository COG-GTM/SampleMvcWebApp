package com.northwind.scheduling.contract.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plain JDBC against the legacy AssessmentDelivery DB, as the shared ad_app login (like every legacy caller). */
public final class Db {

    private final String url;
    private final String user;
    private final String password;

    public Db(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public List<Map<String, Object>> query(String sql, Object... args) {
        try (Connection c = connect(); PreparedStatement ps = prepare(c, sql, args); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    Object v = rs.getObject(i);
                    if (v instanceof Timestamp t) {
                        v = t.toLocalDateTime();
                    } else if (v instanceof java.sql.Date d) {
                        v = d.toLocalDate();
                    }
                    row.put(md.getColumnLabel(i), v);
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = query(sql, args);
        if (rows.size() != 1) {
            throw new IllegalStateException("expected 1 row but got " + rows.size() + ": " + sql);
        }
        return rows.get(0);
    }

    public Object scalar(String sql, Object... args) {
        return one(sql, args).values().iterator().next();
    }

    public int count(String sql, Object... args) {
        return ((Number) scalar(sql, args)).intValue();
    }

    public int update(String sql, Object... args) {
        try (Connection c = connect(); PreparedStatement ps = prepare(c, sql, args)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    /** INSERT returning SCOPE_IDENTITY(); safe on trigger-bearing tables (no bare OUTPUT INSERTED). */
    public int insert(String sql, Object... args) {
        return ((Number) scalar("SET NOCOUNT ON; " + sql + "; SELECT CAST(SCOPE_IDENTITY() AS INT) AS Id", args)).intValue();
    }

    static PreparedStatement prepare(Connection c, String sql, Object... args) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof LocalDateTime ldt) {
                ps.setTimestamp(i + 1, Timestamp.valueOf(ldt));
            } else if (a instanceof LocalDate ld) {
                ps.setDate(i + 1, java.sql.Date.valueOf(ld));
            } else {
                ps.setObject(i + 1, a);
            }
        }
        return ps;
    }
}
