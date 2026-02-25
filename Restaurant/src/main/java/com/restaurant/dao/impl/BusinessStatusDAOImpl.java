package com.restaurant.dao.impl;

import com.restaurant.dao.BusinessStatusDAO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BusinessStatusDAOImpl implements BusinessStatusDAO {

    @Override
    public void insertTodayStatus(Connection conn, LocalDate date) throws SQLException {
        String sql = "INSERT INTO business_status (business_date, is_open, next_call_number, daily_total_customers) " +
                "VALUES (?, true, 1, 0)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            ps.executeUpdate();
        }
    }

    @Override
    public int getNextCallNumber(Connection conn, LocalDate date) throws SQLException {
        String selectSql = "SELECT next_call_number FROM business_status WHERE business_date = ?";

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("next_call_number");
                }
            }
        }

        // 記錄不存在 → 創建初始記錄
        String insertSql = "INSERT INTO business_status (business_date, is_open, next_call_number) VALUES (?, true, 1)";
        try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
            insertPs.setDate(1, java.sql.Date.valueOf(date));
            insertPs.executeUpdate();
        }
        return 1;
    }

    // src/com/restaurant/dao/impl/BusinessStatusDAOImpl.java
    @Override
    public void ensureTodayStatusExists(Connection conn, LocalDate date) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM business_status WHERE business_date = ?";
        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    // 记录不存在 → 插入
                    insertTodayStatus(conn, date);
                }
            }
        }
    }
    @Override
    public void incrementNextCallNumber(Connection conn, LocalDate date) throws SQLException {
        String sql = "UPDATE business_status SET next_call_number = next_call_number + 1 WHERE business_date = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            ps.executeUpdate();
        }
    }

    @Override
    public void incrementDailyTotalCustomers(Connection conn, int customerCount, LocalDate date) throws SQLException {
        if (customerCount <= 0) {
            throw new IllegalArgumentException("顾客人数必须大于0");
        }

        String sql = "UPDATE business_status SET daily_total_customers = daily_total_customers + ? WHERE business_date = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerCount);
            ps.setDate(2, java.sql.Date.valueOf(date));
            int affected = ps.executeUpdate();

            if (affected == 0) {
                // 安全兜底：如果記錄不存在，創建初始記錄
                String insertSql = "INSERT INTO business_status " +
                        "(business_date, is_open, next_call_number, daily_total_customers) " +
                        "VALUES (?, true, 1, ?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    insertPs.setDate(1, java.sql.Date.valueOf(date));
                    insertPs.setInt(2, customerCount);
                    insertPs.executeUpdate();
                }
            }
        }
    }

    @Override
    public Boolean loadIsOpenStatus(Connection conn, LocalDate date) throws SQLException {
        String sql = "SELECT is_open FROM business_status WHERE business_date = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("is_open");
                }
            }
        }
        // 记录不存在时返回 null（调用方决定默认值）
        return null;
    }

    @Override
    public void updateBusinessStatus(Connection conn, LocalDate date, boolean isOpen, int nextCallNumber) throws SQLException {
        String sql = """
            INSERT INTO business_status 
                (business_date, is_open, next_call_number, daily_total_customers)
            VALUES (?, ?, ?, 0)
            ON DUPLICATE KEY UPDATE 
                is_open = VALUES(is_open),
                next_call_number = VALUES(next_call_number)
                /* ✅ 關鍵：不更新 daily_total_customers，保持累加值 */
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            ps.setBoolean(2, isOpen);
            ps.setInt(3, nextCallNumber);
            ps.executeUpdate();
        }
    }


    @Override
    public List<Map<String, Object>> getDailyReport(Connection conn, String date) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();

        String sql = "SELECT " +
                "bs.business_date AS date, " +
                "bs.daily_revenue AS revenue, " +
                "bs.daily_total_customers AS customers, " +
                "(SELECT COUNT(*) FROM table_orders " +
                " WHERE DATE(order_time) = bs.business_date " +
                " AND status = 'CHECKED_OUT') AS orderCount " +
                "FROM business_status bs " +
                "WHERE bs.business_date = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, java.sql.Date.valueOf(java.time.LocalDate.parse(date)));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", rs.getString("date"));
                    row.put("revenue", rs.getDouble("revenue"));
                    row.put("customers", rs.getInt("customers"));
                    row.put("orderCount", rs.getInt("orderCount"));
                    result.add(row);
                }
            }
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getDateRangeReport(Connection conn, String startDate, String endDate) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();

        String sql = "SELECT " +
                "bs.business_date as report_date, " +
                "COALESCE(COUNT(o.order_id), 0) as order_count, " +
                "bs.daily_revenue as total_revenue, " +
                "bs.daily_total_customers as total_customers " +
                "FROM business_status bs " +
                "LEFT JOIN table_orders o ON " +
                "    DATE(o.order_time) = bs.business_date AND " +
                "    o.status = 'CHECKED_OUT' " +
                "WHERE bs.business_date BETWEEN ? AND ? " +
                "GROUP BY bs.business_date, bs.daily_revenue, bs.daily_total_customers " +
                "ORDER BY bs.business_date";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, startDate);
            ps.setString(2, endDate);

            try (ResultSet rs = ps.executeQuery()) {
                boolean hasData = false;
                while (rs.next()) {
                    hasData = true;
                    Map<String, Object> dayData = new HashMap<>();
                    dayData.put("date", rs.getString("report_date"));
                    dayData.put("revenue", rs.getDouble("total_revenue"));
                    dayData.put("customers", rs.getInt("total_customers"));
                    dayData.put("orderCount", rs.getInt("order_count"));
                    result.add(dayData);
                }

                // 无数据时填充空日期（保持原有逻辑）
                if (!hasData) {
                    for (String d : getAllDatesBetween(startDate, endDate)) {
                        Map<String, Object> empty = new HashMap<>();
                        empty.put("date", d);
                        empty.put("revenue", 0.0);
                        empty.put("customers", 0);
                        empty.put("orderCount", 0);
                        result.add(empty);
                    }
                }
            }
        }
        return result;
    }

    // 辅助方法：获取日期范围内的所有日期
    private List<String> getAllDatesBetween(String startDate, String endDate) {
        List<String> dates = new ArrayList<>();
        try {
            java.time.LocalDate start = java.time.LocalDate.parse(startDate);
            java.time.LocalDate end = java.time.LocalDate.parse(endDate);
            while (!start.isAfter(end)) {
                dates.add(start.toString());
                start = start.plusDays(1);
            }
        } catch (Exception e) {
            System.err.println("日期解析错误: " + e.getMessage());
        }
        return dates;
    }
}