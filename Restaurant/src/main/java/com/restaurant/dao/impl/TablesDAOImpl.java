package com.restaurant.dao.impl;

import com.restaurant.dao.TablesDAO;
import com.restaurant.entity.Tables;
import com.restaurant.service.ConnectionPool;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TablesDAOImpl implements TablesDAO {

    @Override
    public List<Tables> findAllTables(Connection conn) throws SQLException {
        String sql = "SELECT table_id, display_id, base_id, capacity, physical_capacity, status, " +
                "table_type, actual_seats, current_group_id, start_time, end_time, " +
                "is_split, sub_table_suffix, main_table_id, merged_with " +
                "FROM restaurant_tables ORDER BY base_id, display_id";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            List<Tables> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(mapResultSetToTables(rs)); // 复用现有映射逻辑
            }
            return tables;
        }
    }

    @Override
    public void initializeDefaultTables(Connection conn) throws SQLException {
        String sql = "INSERT INTO restaurant_tables (display_id, base_id, capacity, physical_capacity, status, table_type) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 1-6号：2人桌
            for (int i = 1; i <= 6; i++) {
                stmt.setString(1, String.valueOf(i));
                stmt.setInt(2, i);
                stmt.setInt(3, 2);
                stmt.setInt(4, 2);
                stmt.setString(5, "VACANT");
                stmt.setString(6, "MAIN");
                stmt.addBatch();
            }

            // 7-12号：4人桌
            for (int i = 7; i <= 12; i++) {
                stmt.setString(1, String.valueOf(i));
                stmt.setInt(2, i);
                stmt.setInt(3, 4);
                stmt.setInt(4, 4);
                stmt.setString(5, "VACANT");
                stmt.setString(6, "MAIN");
                stmt.addBatch();
            }

            // 13-15号：6人桌
            for (int i = 13; i <= 15; i++) {
                stmt.setString(1, String.valueOf(i));
                stmt.setInt(2, i);
                stmt.setInt(3, 6);
                stmt.setInt(4, 6);
                stmt.setString(5, "VACANT");
                stmt.setString(6, "MAIN");
                stmt.addBatch();
            }

            stmt.executeBatch();
            System.out.println("✅ 成功将15张默认餐桌插入数据库");
        }
    }

    @Override
    public Tables save(Tables table) throws SQLException {
        String sql = "INSERT INTO restaurant_tables (display_id, base_id, capacity, physical_capacity, " +
                "status, table_type, actual_seats, current_group_id, is_split, sub_table_suffix, " +
                "main_table_id, start_time, end_time, merged_with) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, table.getDisplayId());
            ps.setInt(2, table.getBaseId());
            ps.setInt(3, table.getCapacity());
            ps.setInt(4, table.getPhysicalCapacity());
            ps.setString(5, table.getStatus().name());
            ps.setString(6, table.getTableType().name());
            ps.setInt(7, table.getActualSeats());

            if (table.getCurrentGroupId() != null) {
                ps.setInt(8, table.getCurrentGroupId());
            } else {
                ps.setNull(8, Types.INTEGER);
            }

            ps.setBoolean(9, table.isSplit());

            if (table.getSubTableSuffix() != null) {
                ps.setString(10, table.getSubTableSuffix());
            } else {
                ps.setNull(10, Types.VARCHAR);
            }

            if (table.getMainTableId() != null) {
                ps.setInt(11, table.getMainTableId());
            } else {
                ps.setNull(11, Types.INTEGER);
            }

            if (table.getStartTime() != null) {
                ps.setTimestamp(12, Timestamp.valueOf(table.getStartTime())); // LocalDateTime 转 Timestamp
            } else {
                ps.setNull(12, Types.TIMESTAMP);
            }

            if (table.getEndTime()!= null) {
                ps.setTimestamp(13, Timestamp.valueOf(table.getEndTime())); // LocalDateTime 转 Timestamp
            } else {
                ps.setNull(13, Types.TIMESTAMP);
            }

            if (table.getMergedWith() != null) {
                ps.setString(14, table.getMergedWith());
            } else {
                ps.setNull(14, Types.VARCHAR);
            }

            int affectedRows = ps.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("创建餐桌失败，没有记录被插入。");
            }

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    table.setTableId(generatedKeys.getInt(1));
                    return table;
                } else {
                    throw new SQLException("创建餐桌失败，没有获取到ID。");
                }
            }
        }
    }

    @Override
    public Tables findById(int id) throws SQLException {
        String sql = "SELECT table_id, display_id, base_id, capacity, physical_capacity, status, " +
                "table_type, actual_seats, current_group_id, is_split, sub_table_suffix, " +
                "main_table_id, start_time, end_time, merged_with " +
                "FROM restaurant_tables WHERE table_id = ?";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTables(rs);
                }
                return null;
            }
        }
    }


    @Override
    public boolean update(Connection conn, Tables table) throws SQLException {
        String sql = "UPDATE restaurant_tables SET display_id = ?, base_id = ?, capacity = ?, " +
                "physical_capacity = ?, status = ?, table_type = ?, actual_seats = ?, " +
                "current_group_id = ?, is_split = ?, sub_table_suffix = ?, main_table_id = ?, " +
                "start_time = ?, end_time = ?, merged_with = ? WHERE table_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table.getDisplayId());
            ps.setInt(2, table.getBaseId());
            ps.setInt(3, table.getCapacity());
            ps.setInt(4, table.getPhysicalCapacity());
            ps.setString(5, table.getStatus().name());
            ps.setString(6, table.getTableType().name());
            ps.setInt(7, table.getActualSeats());

            if (table.getCurrentGroupId() != null) {
                ps.setInt(8, table.getCurrentGroupId());
            } else {
                ps.setNull(8, Types.INTEGER);
            }

            ps.setBoolean(9, table.isSplit());

            if (table.getSubTableSuffix() != null) {
                ps.setString(10, table.getSubTableSuffix());
            } else {
                ps.setNull(10, Types.VARCHAR);
            }

            if (table.getMainTableId() != null) {
                ps.setInt(11, table.getMainTableId());
            } else {
                ps.setNull(11, Types.INTEGER);
            }

            if (table.getStartTime() != null) {
                ps.setTimestamp(12, Timestamp.valueOf(table.getStartTime()));
            } else {
                ps.setNull(12, Types.TIMESTAMP);
            }

            if (table.getEndTime() != null) {
                ps.setTimestamp(13, Timestamp.valueOf(table.getEndTime()));
            } else {
                ps.setNull(13, Types.TIMESTAMP);
            }

            if (table.getMergedWith() != null) {
                ps.setString(14, table.getMergedWith());
            } else {
                ps.setNull(14, Types.VARCHAR);
            }

            ps.setInt(15, table.getTableId());

            return ps.executeUpdate() > 0;
        }
    }

    // ✅ 保留原有无参方法（内部创建新连接）
    @Override
    public boolean update(Tables table) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection()) {
            return update(conn, table); // 复用事务安全版本
        }
    }
    public List<Tables> findAvailableTables(int capacity, String tableType) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT table_id, display_id, base_id, capacity, physical_capacity, status, " +
                        "table_type, actual_seats, current_group_id, is_split, sub_table_suffix, " +
                        "main_table_id, start_time, end_time, merged_with " +
                        "FROM restaurant_tables WHERE status = 'VACANT'"
        );

        if (capacity > 0) {
            sql.append(" AND capacity >= ?");
        }

        if (tableType != null && !tableType.isEmpty()) {
            sql.append(" AND table_type = ?");
        }

        sql.append(" ORDER BY capacity ASC, base_id ASC");

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (capacity > 0) {
                ps.setInt(paramIndex++, capacity);
            }

            if (tableType != null && !tableType.isEmpty()) {
                ps.setString(paramIndex++, tableType);
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<Tables> tables = new ArrayList<>();
                while (rs.next()) {
                    tables.add(mapResultSetToTables(rs));
                }
                return tables;
            }
        }
    }

    @Override
    public boolean updateTableStatus(int tableId, String status, Integer currentGroupId,
                                     int actualSeats) throws SQLException {
        String sql = "UPDATE restaurant_tables SET status = ?, current_group_id = ?, actual_seats = ?, " +
                "start_time = ? WHERE table_id = ?";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);

            if (currentGroupId != null) {
                ps.setInt(2, currentGroupId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }

            ps.setInt(3, actualSeats);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.setInt(5, tableId);

            return ps.executeUpdate() > 0;
        }
    }

    private Tables mapResultSetToTables(ResultSet rs) throws SQLException {
        // 先獲取構造函數必需的三個參數
        int baseId = rs.getInt("base_id");
        int capacity = rs.getInt("capacity");
        String displayId = rs.getString("display_id");
        // 使用正確的構造函數創建對象
        Tables table = new Tables(baseId, capacity, displayId);
        // 設置其他屬性
        table.setTableId(rs.getInt("table_id"));
        table.setPhysicalCapacity(rs.getInt("physical_capacity"));
        table.setStatus(Tables.TableStatus.valueOf(rs.getString("status")));
        table.setTableType(Tables.TableType.valueOf(rs.getString("table_type")));
        table.setActualSeats(rs.getInt("actual_seats"));

        //  正確處理 current_group_id
        Integer currentGroupId = null;
        int groupIdValue = rs.getInt("current_group_id");
        if (!rs.wasNull()) {
            currentGroupId = groupIdValue;
        }
        table.setCurrentGroupId(currentGroupId);

        // 處理布爾字段
        table.setSplit(rs.getBoolean("is_split"));

        //正確處理 sub_table_suffix
        String subTableSuffix = rs.getString("sub_table_suffix");
        table.setSubTableSuffix(rs.wasNull() ? null : subTableSuffix);

        //正確處理 main_table_id
        Integer mainTableId = null;
        int mainTableIdValue = rs.getInt("main_table_id");
        if (!rs.wasNull()) {
            mainTableId = mainTableIdValue;
        }
        table.setMainTableId(mainTableId);

        //  正確處理時間字段
        Timestamp startTime = rs.getTimestamp("start_time");
        if (!rs.wasNull() && startTime != null) {
            table.setStartTime(startTime.toLocalDateTime());
        }

        Timestamp endTime = rs.getTimestamp("end_time");
        if (!rs.wasNull() && endTime != null) {
            table.setEndTime(endTime.toLocalDateTime());
        }

        // 處理合併關係字段
        String mergedWith = rs.getString("merged_with");
        table.setMergedWith(rs.wasNull() ? null : mergedWith);

        return table;
    }




    @Override
    public boolean deleteSubTables(Connection conn, List<Integer> subTableIds) throws SQLException {
        // 空列表快速返回（业务逻辑）
        if (subTableIds == null || subTableIds.isEmpty()) {
            return true;
        }

        String sql = "DELETE FROM restaurant_tables WHERE table_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Integer id : subTableIds) {
                ps.setInt(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
            return true;
        }
    }

    @Override
    public boolean deleteSubTables(List<Integer> subTableIds) throws SQLException {
        // 空列表快速返回（避免创建无用连接）
        if (subTableIds == null || subTableIds.isEmpty()) {
            return true;
        }

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 一行复用核心实现
                boolean result = deleteSubTables(conn, subTableIds);
                conn.commit();
                return result;
            } catch (SQLException e) {
                conn.rollback();
                throw e; // 保持异常类型不变
            }
        }
    }

    //  核心实现（带 Connection 版本）
    @Override
    public Tables findByDisplayId(Connection conn, String displayId) throws SQLException {
        String sql = "SELECT table_id, display_id, base_id, capacity, physical_capacity, status, " +
                "table_type, actual_seats, current_group_id, is_split, sub_table_suffix, " +
                "main_table_id, start_time, end_time, merged_with " +
                "FROM restaurant_tables WHERE display_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, displayId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapResultSetToTables(rs) : null;
            }
        }
    }

    @Override
    public Tables findByDisplayId(String displayId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection()) {
            return findByDisplayId(conn, displayId); // ← 一行复用
        }
    }

    @Override
    public boolean updateSplitStatus(int tableId, boolean isSplit) throws SQLException {
        String sql = "UPDATE restaurant_tables SET is_split = ?, status = ? WHERE table_id = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isSplit);
            ps.setString(2, isSplit ? "SPLITTING" : "VACANT");
            ps.setInt(3, tableId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<Tables> findSubTablesByMainId(int mainTableId) throws SQLException {
        try (Connection conn = ConnectionPool.getConnection()) {
            return findSubTablesByMainId(conn, mainTableId); // ← 一行复用
        }
    }

    @Override
    public List<Tables> findSubTablesByMainId(Connection conn, int mainTableId) throws SQLException {
        String sql = "SELECT table_id, display_id, base_id, capacity, physical_capacity, status, " +
                "table_type, actual_seats, current_group_id, is_split, sub_table_suffix, " +
                "main_table_id, start_time, end_time, merged_with " +
                "FROM restaurant_tables WHERE main_table_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, mainTableId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Tables> subTables = new ArrayList<>();
                while (rs.next()) {
                    subTables.add(mapResultSetToTables(rs));
                }
                return subTables;
            }
        }
    }


    @Override
    public List<List<Tables>> findAdjacentAvailableTables(int capacity, int colsPerRow) throws SQLException {
        // 1. 獲取所有指定容量的空閒餐桌
        String sql = "SELECT table_id, display_id, base_id, capacity, physical_capacity, status, " +
                "table_type, actual_seats, current_group_id, is_split, sub_table_suffix, " +
                "main_table_id, start_time, end_time, merged_with " +
                "FROM restaurant_tables WHERE status = 'VACANT' AND capacity = ? AND table_type = 'MAIN' " +
                "ORDER BY base_id";

        List<Tables> availableTables = new ArrayList<>();
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, capacity);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    availableTables.add(mapResultSetToTables(rs));
                }
            }
        }

        // 2. 查找相鄰的餐桌對
        List<List<Tables>> adjacentPairs = new ArrayList<>();
        for (int i = 0; i < availableTables.size(); i++) {
            Tables table1 = availableTables.get(i);
            int row1 = (table1.getBaseId() - 1) / colsPerRow;
            int col1 = (table1.getBaseId() - 1) % colsPerRow;

            for (int j = i + 1; j < availableTables.size(); j++) {
                Tables table2 = availableTables.get(j);
                int row2 = (table2.getBaseId() - 1) / colsPerRow;
                int col2 = (table2.getBaseId() - 1) % colsPerRow;

                // 檢查是否在同一行且相鄰
                if (row1 == row2 && col2 == col1 + 1) {
                    List<Tables> pair = new ArrayList<>();
                    pair.add(table1);
                    pair.add(table2);
                    adjacentPairs.add(pair);
                }
            }
        }

        return adjacentPairs;
    }


    /**
     * 更新餐桌合併狀態（事務安全版本 - 使用外部傳入的 Connection）
     *
     * @param conn           外部事務連接（由 Model/Controller 管理事務邊界）
     * @param mainTableId    主餐桌ID
     * @param partnerTableId 伙伴餐桌ID
     * @param mergedWith1    伙伴餐桌的display_id（主桌的 merged_with 字段值）
     * @param mergedWith2    主餐桌的display_id（伙伴桌的 merged_with 字段值）
     * @param groupId        關聯的顧客組ID
     * @param actualSeats1   主餐桌實際座位數
     * @param actualSeats2   伙伴餐桌實際座位數
     * @return 更新是否成功
     * @throws SQLException 數據庫操作異常
     *
     * ⚠️ 注意：本方法不管理事務！禁止在內部調用 conn.commit()/rollback()/close()
     */
    @Override
    public boolean updateMergeStatus(Connection conn,
                                     int mainTableId,
                                     int partnerTableId,
                                     String mergedWith1,
                                     String mergedWith2,
                                     Integer groupId,
                                     int actualSeats1,
                                     int actualSeats2) throws SQLException {

        // 🔴 絕對不要：getConnection() / setAutoCommit() / commit() / rollback() / close()

        String updateSql = """
        UPDATE restaurant_tables SET 
            status = 'OCCUPIED',
            table_type = 'MERGED',
            merged_with = ?,
            current_group_id = ?,
            actual_seats = ?,
            start_time = CURRENT_TIMESTAMP
        WHERE table_id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            // 更新主桌
            ps.setString(1, mergedWith1);
            ps.setObject(2, groupId);  // 使用 setObject 兼容 null
            ps.setInt(3, actualSeats1);
            ps.setInt(4, mainTableId);
            ps.executeUpdate();

            // 更新伙伴桌
            ps.setString(1, mergedWith2);
            ps.setObject(2, groupId);
            ps.setInt(3, actualSeats2);
            ps.setInt(4, partnerTableId);
            ps.executeUpdate();
        }

        return true;  // 僅表示 SQL 執行成功，事務提交與否由外層決定
    }

    /**
     * 更新餐桌合併狀態（非事務版本 - 內部創建新連接）
     *
     * @note 僅供工具類/測試/獨立操作使用，不建議在業務流程中直接調用
     */
    @Override
    public boolean updateMergeStatus(int mainTableId,
                                     int partnerTableId,
                                     String mergedWith1,
                                     String mergedWith2,
                                     Integer groupId,
                                     int actualSeats1,
                                     int actualSeats2) throws SQLException {

        try (Connection conn = ConnectionPool.getConnection()) {
            // 委託給事務安全版本執行
            return updateMergeStatus(conn, mainTableId, partnerTableId,
                    mergedWith1, mergedWith2, groupId,
                    actualSeats1, actualSeats2);
        }
    }

    public boolean updateTableStatusForDeparture(int tableId, String status,
                                                 Integer currentGroupId, int actualSeats, String originalTableType) throws SQLException {

        // 关键修复：仅当原类型是 MERGED 时才更新 table_type
        String sql;
        if ("MERGED".equals(originalTableType)) {
            // 合并桌离店 → 恢复为主桌
            sql = "UPDATE restaurant_tables SET " +
                    "status = ?, " +
                    "current_group_id = ?, " +
                    "actual_seats = ?, " +
                    "end_time = CURRENT_TIMESTAMP, " +
                    "merged_with = NULL, " +
                    "table_type = 'MAIN' " +  // 仅 MERGED 餐桌恢复为主桌
                    "WHERE table_id = ?";
        } else {
            // SUBTABLE/MAIN 保持原类型不变，仅清除合并关系
            sql = "UPDATE restaurant_tables SET " +
                    "status = ?, " +
                    "current_group_id = ?, " +
                    "actual_seats = ?, " +
                    "end_time = CURRENT_TIMESTAMP, " +
                    "merged_with = NULL " +  // 仅清除合并关系，不修改 table_type
                    "WHERE table_id = ?";
        }

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            if (currentGroupId != null) {
                ps.setInt(2, currentGroupId);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setInt(3, actualSeats);
            ps.setInt(4, tableId);

            return ps.executeUpdate() > 0;
        }
    }


    @Override
    public int[] splitOccupiedTable( Connection conn, int mainTableId, int existingGroupId, int newGroupId, int subTableCapacity
    ) throws SQLException {

        // 1️⃣ 更新主桌状态 → SPLITTING
        String updateMainSql = """
        UPDATE restaurant_tables 
        SET status = 'SPLITTING', 
            is_split = TRUE,
            current_group_id = NULL,
            actual_seats = 0,
            start_time = NULL,
            end_time = NULL
        WHERE table_id = ?
    """;
        try (PreparedStatement ps = conn.prepareStatement(updateMainSql)) {
            ps.setInt(1, mainTableId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("更新主桌状态失败");
            }
        }

        // 2️⃣ 获取主桌基础信息
        String selectBaseSql = """
        SELECT base_id, display_id 
        FROM restaurant_tables 
        WHERE table_id = ?
    """;
        int baseId;
        String baseDisplayId;
        try (PreparedStatement ps = conn.prepareStatement(selectBaseSql)) {
            ps.setInt(1, mainTableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("主桌不存在");
                baseId = rs.getInt("base_id");
                baseDisplayId = rs.getString("display_id");
            }
        }

        // 3️⃣ 插入两个子桌
        String insertSubSql = """
        INSERT INTO restaurant_tables (
            display_id, base_id, capacity, physical_capacity, 
            status, table_type, actual_seats, current_group_id, 
            is_split, sub_table_suffix, main_table_id, start_time
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?, ?)
    """;

        int[] subTableIds = new int[2];

        try (PreparedStatement ps =
                     conn.prepareStatement(insertSubSql, Statement.RETURN_GENERATED_KEYS)) {

            // 子桌 A（原顾客组）
            ps.setString(1, baseDisplayId + "a");
            ps.setInt(2, baseId);
            ps.setInt(3, subTableCapacity);
            ps.setInt(4, subTableCapacity);
            ps.setString(5, "OCCUPIED");
            ps.setString(6, "SUBTABLE");
            ps.setInt(7, subTableCapacity);
            ps.setInt(8, existingGroupId);
            ps.setString(9, "a");
            ps.setInt(10, mainTableId);
            ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            ps.addBatch();

            // 子桌 B（新顾客组）
            ps.setString(1, baseDisplayId + "b");
            ps.setInt(2, baseId);
            ps.setInt(3, subTableCapacity);
            ps.setInt(4, subTableCapacity);
            ps.setString(5, "OCCUPIED");
            ps.setString(6, "SUBTABLE");
            ps.setInt(7, subTableCapacity);
            ps.setInt(8, newGroupId);
            ps.setString(9, "b");
            ps.setInt(10, mainTableId);
            ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            ps.addBatch();

            ps.executeBatch();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                int i = 0;
                while (rs.next() && i < 2) {
                    subTableIds[i++] = rs.getInt(1);
                }
            }
        }

        if (subTableIds[0] <= 0 || subTableIds[1] <= 0) {
            throw new SQLException("生成子桌 ID 失败");
        }

        // 🔴🔴🔴 4️⃣【关键补丁】同步更新 customer_groups.table_id
        String updateGroupSql = """
        UPDATE customer_groups
        SET table_id = ?, is_assigned = 1
        WHERE group_id = ?
    """;
        try (PreparedStatement ps = conn.prepareStatement(updateGroupSql)) {

            // 原顾客组 → 子桌 A
            ps.setInt(1, subTableIds[0]);
            ps.setInt(2, existingGroupId);
            ps.executeUpdate();

            // 新顾客组 → 子桌 B
            ps.setInt(1, subTableIds[1]);
            ps.setInt(2, newGroupId);
            ps.executeUpdate();
        }

        return subTableIds; // [subA_id, subB_id]
    }





    @Override
    public void updateMergedPairToVacant(int tableId1, int tableId2, Connection conn) throws SQLException {
        String sql = """
        UPDATE restaurant_tables 
        SET merged_with = NULL, 
            table_type = 'MAIN', 
            status = 'VACANT', 
            current_group_id = NULL, 
            start_time = NULL, 
            end_time = NULL, 
            actual_seats = 0 
        WHERE table_id IN (?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableId1);
            ps.setInt(2, tableId2);
            ps.executeUpdate();
        }
    }


    @Override
    public Tables saveSubTable(Connection conn, Tables subTable) throws SQLException {
        // 严格匹配您的 restaurant_tables 表结构
        String sql = "INSERT INTO restaurant_tables (" +
                "display_id, base_id, capacity, physical_capacity, status, table_type, " +
                "start_time, is_split, sub_table_suffix, main_table_id, actual_seats, current_group_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, subTable.getDisplayId());
            ps.setInt(2, subTable.getBaseId());
            ps.setInt(3, subTable.getCapacity());
            ps.setInt(4, subTable.getCapacity()); // physical_capacity = capacity (您的设计)
            ps.setString(5, subTable.getStatus().name());
            ps.setString(6, subTable.getTableType().name());
            ps.setTimestamp(7, subTable.getStartTime() != null ?
                    Timestamp.valueOf(subTable.getStartTime()) : null);
            ps.setBoolean(8, subTable.isSplit());
            ps.setString(9, subTable.getSubTableSuffix());
            ps.setInt(10, subTable.getMainTableId());
            ps.setInt(11, subTable.getActualSeats());
            ps.setInt(12, subTable.getCurrentGroupId() != null ? subTable.getCurrentGroupId() : 0);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    subTable.setTableId(rs.getInt(1)); // 严格使用 setTableId()
                    return subTable;
                }
                throw new SQLException("创建子桌失败，未获取自增ID");
            }
        }
    }

    @Override
    public boolean updateSplitStatus(Connection conn, int tableId, boolean isSplit) throws SQLException {
        // 关键修正：同时清空 current_group_id
        String sql = "UPDATE restaurant_tables " +
                "SET is_split = ?, status = ?, current_group_id = NULL " +
                "WHERE table_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isSplit);
            ps.setString(2, Tables.TableStatus.SPLITTING.name());
            ps.setInt(3, tableId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean hasExistingTableData(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) as count FROM restaurant_tables";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("count") > 0;
            }
        }
        return false;
    }


}