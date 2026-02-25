package com.restaurant.dao.impl;

import com.restaurant.dao.CustomerGroupDAO;
import com.restaurant.entity.CustomerGroup;
import com.restaurant.service.ConnectionPool;

import java.sql.*;
import java.time.LocalDateTime;

public class CustomerGroupDAOImpl implements CustomerGroupDAO {


    // ✅ 核心实现（带 Connection 版本）- 仅此一处写 SQL
    @Override
    public CustomerGroup save(Connection conn, CustomerGroup group) throws SQLException {
        String sql = "INSERT INTO customer_groups (call_number, group_size, start_time, is_assigned, shown_wait_message, table_id) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, group.getCallNumber());
            ps.setInt(2, group.getSize());
            ps.setTimestamp(3, Timestamp.valueOf(group.getStartTime()));
            ps.setBoolean(4, group.isAssigned());
            ps.setBoolean(5, group.hasShownWaitMessage());

            if (group.getTableId() != null) {
                ps.setInt(6, group.getTableId());
            } else {
                ps.setNull(6, Types.INTEGER);
            }

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("创建顾客组失败，没有记录被插入");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    group.setGroup_id(rs.getInt(1));
                    return group;
                }
                throw new SQLException("创建顾客组失败，没有获取到ID");
            }
        }
    }

    // ✅ 简化：无 Connection 版本直接复用带 Connection 版本
    @Override
    public CustomerGroup save(CustomerGroup group) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection()) {
            return save(conn, group); // ← 一行代码复用核心逻辑
        }
    }

    @Override
    public CustomerGroup findById(int id) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection()) {
            return findById(conn, id); // ← 复用核心实现
        }
    }

    @Override
    public CustomerGroup findById(Connection conn, int id) throws SQLException {
        String sql = "SELECT group_id, call_number, group_size, start_time, is_assigned, " +
                "shown_wait_message, table_id FROM customer_groups WHERE group_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapResultSetToCustomerGroup(rs) : null;
            }
        }
    }

    @Override
    public boolean update(Connection conn, CustomerGroup group) throws SQLException {
        String sql = "UPDATE customer_groups SET call_number = ?, group_size = ?, start_time = ?, " +
                "is_assigned = ?, shown_wait_message = ?, table_id = ? WHERE group_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, group.getCallNumber());
            ps.setInt(2, group.getSize());
            ps.setTimestamp(3, Timestamp.valueOf(group.getStartTime()));
            ps.setBoolean(4, group.isAssigned());
            ps.setBoolean(5, group.hasShownWaitMessage());

            if (group.getTableId() != null) {
                ps.setInt(6, group.getTableId());
            } else {
                ps.setNull(6, Types.INTEGER);
            }

            ps.setInt(7, group.getGroup_id());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean update(CustomerGroup group) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection()) {
            return update(conn, group); // ← 复用核心实现
        }
    }


    @Override
    public boolean updateAssignmentStatus(Connection conn,  // ← 新增參數
                                          int groupId,
                                          Integer tableId,
                                          boolean isAssigned,
                                          boolean shownWaitMessage) throws SQLException {

        String sql = """
        UPDATE customer_groups 
        SET table_id = ?, is_assigned = ?, shown_wait_message = ? 
        WHERE group_id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (tableId != null) {
                ps.setInt(1, tableId);
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setBoolean(2, isAssigned);
            ps.setBoolean(3, shownWaitMessage);
            ps.setInt(4, groupId);
            return ps.executeUpdate() > 0;
        }
    }

    // ✅ 保留原無參方法，內部調用帶 Connection 版本
    @Override
    public boolean updateAssignmentStatus(int groupId, Integer tableId,
                                          boolean isAssigned, boolean shownWaitMessage) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection()) {
            return updateAssignmentStatus(conn, groupId, tableId, isAssigned, shownWaitMessage);
        }
    }

    /**
     * 将ResultSet映射为CustomerGroup对象
     */
    private CustomerGroup mapResultSetToCustomerGroup(ResultSet rs) throws SQLException {
        int groupId = rs.getInt("group_id");
        int callNumber = rs.getInt("call_number");
        int size = rs.getInt("group_size");
        LocalDateTime startTime = rs.getTimestamp("start_time").toLocalDateTime();
        boolean isAssigned = rs.getBoolean("is_assigned");
        boolean shownWaitMessage = rs.getBoolean("shown_wait_message");

        Integer tableId = null;
        int tableIdValue = rs.getInt("table_id");
        if (!rs.wasNull()) {
            tableId = tableIdValue;
        }

        // position字段在customer_groups表中不存在，需要从queue表获取
        int position = 0;

        return new CustomerGroup(groupId, callNumber, size, startTime,
                isAssigned, shownWaitMessage, tableId, position);
    }


    @Override
    public boolean delete(Connection conn, int id) throws SQLException {
        // 事务场景：使用外部传入的连接
        String sql = "DELETE FROM customer_groups WHERE group_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public CustomerGroup findByCallNumber(Connection conn, int callNumber) throws SQLException {
        // ✅ 修正：使用数据库实际存在的字段名（group_size, start_time）
        String sql = """
                
                        SELECT group_id, call_number, group_size, start_time, is_assigned, 
                       shown_wait_message, table_id 
                FROM customer_groups 
                WHERE call_number = ? AND is_assigned = 0
                ORDER BY start_time ASC
                LIMIT 1
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, callNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapResultSetToCustomerGroup(rs) : null;
            }
        }
    }

    @Override
    public void saveWithoutTableRef(Connection conn, CustomerGroup group) throws SQLException {
        String sql = "INSERT INTO customer_groups (call_number, group_size, start_time, is_assigned, shown_wait_message, table_id) " +
                "VALUES (?, ?, ?, ?, ?, NULL)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, group.getCallNumber());
            ps.setInt(2, group.getSize());
            ps.setTimestamp(3, Timestamp.valueOf(group.getStartTime()));
            ps.setBoolean(4, group.isAssigned());
            ps.setBoolean(5, group.hasShownWaitMessage());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    group.setGroup_id(rs.getInt(1));
                }
            }
        }
    }

    public void updateTableId(Connection conn, int groupId, int tableId) throws SQLException {
        // 严格匹配您的表结构
        String sql = "UPDATE customer_groups SET table_id = ? WHERE group_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            ps.setInt(2, groupId);
            ps.executeUpdate();
        }
    }

}
