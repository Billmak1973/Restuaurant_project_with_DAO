package com.restaurant.dao;

import com.restaurant.entity.Tables;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public interface TablesDAO extends BaseDAO<Tables> {

    List<Tables> findAllTables(Connection conn) throws SQLException; // 新增

    /**
     * 初始化15张默认餐桌（仅在首次启动时调用）
     *
     * @param conn 外部事务连接（确保与后续操作在同一个事务中）
     * @throws SQLException
     */
    void initializeDefaultTables(Connection conn) throws SQLException;

    /**
     * 根据显示ID查找餐桌
     *
     * @param displayId 餐桌显示ID (如 "7a")
     * @return 找到的餐桌，未找到返回null
     * @throws SQLException 数据库操作异常
     */
    Tables findByDisplayId(String displayId) throws SQLException;

    Tables findByDisplayId(Connection conn, String displayId) throws SQLException;

    /**
     * 查找主桌的所有子桌
     *
     * @param mainTableId 主桌ID
     * @return 子桌列表
     * @throws SQLException 数据库操作异常
     */
    List<Tables> findSubTablesByMainId(int mainTableId) throws SQLException;

    // 新增事务安全重载方法（带 Connection 参数）
    List<Tables> findSubTablesByMainId(Connection conn, int mainTableId) throws SQLException;

    /**
     * 查找可用餐桌
     *
     * @param capacity  餐桌容量
     * @param tableType 餐桌类型 (可选，如 "MAIN"、"SUBTABLE"、"MERGED")
     * @return 可用餐桌列表
     * @throws SQLException 数据库操作异常
     */
    List<Tables> findAvailableTables(int capacity, String tableType) throws SQLException;

    /**
     * 根据餐桌ID更新状态
     *
     * @param tableId        餐桌ID
     * @param status         新状态 (如 "VACANT", "OCCUPIED", "SETTING_UP", "SPLITTING")
     * @param currentGroupId 当前顾客组ID，可为null
     * @param actualSeats    实际入座人数
     * @return 更新是否成功
     * @throws SQLException 数据库操作异常
     */
    boolean updateTableStatus(int tableId, String status, Integer currentGroupId,
                              int actualSeats) throws SQLException;

    /**
     * 更新餐桌拆分状态
     *
     * @param tableId 餐桌ID
     * @param isSplit 是否拆分
     * @return 更新是否成功
     * @throws SQLException 数据库操作异常
     */
    boolean updateSplitStatus(int tableId, boolean isSplit) throws SQLException;

    /**
     * 删除子桌
     *
     * @param subTableIds 要删除的子桌ID列表
     * @return 删除是否成功
     * @throws SQLException 数据库操作异常
     */
    boolean deleteSubTables(List<Integer> subTableIds) throws SQLException;


    /**
     * 查找相鄰的可用餐桌對
     *
     * @param capacity   每張餐桌所需容量
     * @param colsPerRow 每行餐桌數（餐廳布局）
     * @return 相鄰餐桌對列表
     * @throws SQLException 數據庫操作異常
     */
    List<List<Tables>> findAdjacentAvailableTables(int capacity, int colsPerRow) throws SQLException;

    /**
     * 更新餐桌合併狀態
     *
     * @param mainTableId    主餐桌ID
     * @param partnerTableId 伙伴餐桌ID
     * @param mergedWith1    伙伴餐桌的display_id
     * @param mergedWith2    主餐桌的display_id
     * @param groupId        關聯的顧客組ID
     * @param actualSeats1   主餐桌實際座位數
     * @param actualSeats2   伙伴餐桌實際座位數
     * @return 更新是否成功
     * @throws SQLException 數據庫操作異常
     */
    boolean updateMergeStatus(int mainTableId, int partnerTableId, String mergedWith1, String mergedWith2,
                              Integer groupId, int actualSeats1, int actualSeats2) throws SQLException;

    boolean updateMergeStatus(Connection conn, int mainTableId, int partnerTableId, String mergedWith1,
                              String mergedWith2, Integer groupId, int actualSeats1, int actualSeats2) throws SQLException;

    boolean updateTableStatusForDeparture(int tableId, String status,
                                          Integer currentGroupId, int actualSeats, String originalTableType) throws SQLException;

    /**
     * 原子分裂占用中的餐桌（事务内完成所有操作）
     *
     * @param conn             外部事务连接（由Model传入）
     * @param mainTableId      原主桌ID
     * @param existingGroupId  原顾客组ID
     * @param newGroupId       新顾客组ID
     * @param subTableCapacity 子桌容量（2人）
     * @return [subTableA_id, subTableB_id]
     * @throws SQLException
     */
    int[] splitOccupiedTable(Connection conn, int mainTableId, int existingGroupId, int newGroupId, int subTableCapacity) throws SQLException;


    // 新增事务安全方法 (带Connection参数)
    boolean deleteSubTables(Connection conn, List<Integer> subTableIds) throws SQLException;


    void updateMergedPairToVacant(int tableId1, int tableId2, Connection conn) throws SQLException;

    boolean update(Connection conn, Tables table) throws SQLException;

    @Override
    boolean update(Tables table) throws SQLException; // 继承自 BaseDAO

    /**
     * 保存子桌（仅持久化，不处理业务逻辑）
     *
     * @param conn     事务连接
     * @param subTable 子桌对象（必须设置mainTableId和subTableSuffix）
     * @return 持久化后的子桌对象（含自增ID）
     * @throws SQLException
     */
    Tables saveSubTable(Connection conn, Tables subTable) throws SQLException;

    /**
     * 更新主桌拆分状态 → SPLITTING
     *
     * @param conn    事务连接
     * @param tableId 主桌ID
     * @param isSplit true=标记为拆分
     * @return 更新是否成功
     * @throws SQLException
     */
    boolean updateSplitStatus(Connection conn, int tableId, boolean isSplit) throws SQLException;

    /**
     * 檢查數據庫中是否已有餐桌數據
     * @param conn 數據庫連接
     * @return true=已有數據，false=空表
     * @throws SQLException
     */
    boolean hasExistingTableData(Connection conn) throws SQLException;

}
