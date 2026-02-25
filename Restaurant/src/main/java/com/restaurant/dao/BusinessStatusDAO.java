package com.restaurant.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface BusinessStatusDAO {
    /**
     * 插入当天的业务状态记录
     *
     * @param conn 数据库连接（事务控制由调用方管理）
     * @param date 业务日期
     * @throws SQLException 数据库操作异常
     */
    void insertTodayStatus(Connection conn, LocalDate date) throws SQLException;

    int getNextCallNumber(Connection conn, LocalDate date) throws SQLException;

    void ensureTodayStatusExists(Connection conn, LocalDate date) throws SQLException;

    void incrementNextCallNumber(Connection conn, LocalDate date) throws SQLException;

    void incrementDailyTotalCustomers(Connection conn, int customerCount, LocalDate date) throws SQLException;

    Boolean loadIsOpenStatus(Connection conn, LocalDate date) throws SQLException;

    /**
     * 更新營業狀態（插入或更新，但不影響 daily_total_customers）
     *
     * @param conn           事務連接
     * @param date           業務日期
     * @param isOpen         是否營業
     * @param nextCallNumber 下一個叫號
     * @throws SQLException
     */
    void updateBusinessStatus(Connection conn, LocalDate date, boolean isOpen, int nextCallNumber) throws SQLException;

    // 在 BusinessStatusDAO 接口末尾添加
    List<Map<String, Object>> getDailyReport(Connection conn, String date) throws SQLException;
    List<Map<String, Object>> getDateRangeReport(Connection conn, String startDate, String endDate) throws SQLException;
}