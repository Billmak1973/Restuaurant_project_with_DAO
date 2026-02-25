package com.restaurant.dao.impl;

import com.restaurant.dao.QueueDAO;
import com.restaurant.entity.CustomerGroup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class QueueDAOImpl implements QueueDAO {

    @Override
    public void insertQueue(Connection conn, String queueType, int groupId, int position) throws SQLException {

        String sql = """
                INSERT INTO queues (queue_type, group_id, position)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, queueType);
            ps.setInt(2, groupId);
            ps.setInt(3, position);

            int affected = ps.executeUpdate();
            if (affected != 1) {
                throw new SQLException("插入 queues 失败，影响行数=" + affected);
            }
        }
    }



    public int getNextQueuePosition(Connection conn, String queueType) throws SQLException {
        String sql = "SELECT COALESCE(MAX(position), 0) + 1 FROM queues WHERE queue_type = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, queueType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 1; // 理论上不会走到
    }

    @Override
    public String findQueueTypeByGroupId(Connection conn, int groupId) throws SQLException {
        String sql = "SELECT queue_type FROM queues WHERE group_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("queue_type") : null;
            }
        }
    }


    @Override
    public void updateQueuePositions(Connection conn, String queueType) throws SQLException {
        // ✅ 完全还原原始逻辑：使用窗口函数 + WHERE queue_type 双重限定
        String updateSql = """
            UPDATE queues q1
            JOIN (
                SELECT queue_id, ROW_NUMBER() OVER (ORDER BY position ASC, queue_id ASC) as new_position
                FROM queues
                WHERE queue_type = ?
            ) q2 ON q1.queue_id = q2.queue_id
            SET q1.position = q2.new_position
            WHERE q1.queue_type = ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, queueType); // 子查询中的 WHERE
            ps.setString(2, queueType); // 外层 UPDATE 的 WHERE
            int affected = ps.executeUpdate();
            System.out.println("队列 " + queueType + " 位置重排完成，影响行数: " + affected);
        }
    }

    @Override
    public void removeFromQueue(Connection conn, int groupId, String queueType) throws SQLException {
        // 1. 从数据库中删除
        String deleteSql = "DELETE FROM queues WHERE group_id = ? AND queue_type = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setInt(1, groupId);
            ps.setString(2, queueType);
            ps.executeUpdate();
        }

        // 2. 重排剩余位置（关键：只重排当前 queueType）
        updateQueuePositions(conn, queueType); // 复用上述方法
    }

    // QueueDAOImpl.java
    @Override
    public List<CustomerGroup> loadQueueByType(Connection conn, String queueType) throws SQLException {
        String sql = """
        SELECT q.*, cg.* 
        FROM queues q 
        JOIN customer_groups cg ON q.group_id = cg.group_id 
        WHERE q.queue_type = ? 
        ORDER BY q.position
        """;

        List<CustomerGroup> groups = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, queueType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    groups.add(mapResultSetToCustomerGroup(rs));
                }
            }
        }
        return groups;
    }

    /**
     * 将 ResultSet 映射为 CustomerGroup（私有辅助方法）
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

        int position = rs.getInt("position");

        return new CustomerGroup(groupId, callNumber, size, startTime,
                isAssigned, shownWaitMessage, tableId, position);
    }
}
