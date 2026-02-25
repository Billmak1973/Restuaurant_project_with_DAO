package com.restaurant.dao;

import com.restaurant.entity.Tables;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public interface OrderDAO {
    int createOrder(Connection conn, int tableId, double totalAmount) throws SQLException;

    Integer findActiveOrderIdByTableId(Connection conn, int tableId) throws SQLException;


    boolean deleteOrder(Connection conn, int orderId) throws SQLException;

    /**
     * 获取订单总金额
     */
    double getOrderTotalAmount(Connection conn, int orderId) throws SQLException;

    /**
     * 获取订单创建时间
     */
    Timestamp getOrderCreateTime(Connection conn, int orderId) throws SQLException;

    /**
     * 执行结账：更新订单状态为 CHECKED_OUT
     */
    void checkoutOrder(Connection conn, int orderId) throws SQLException;

    /**
     * 更新指定日期的营业总收入
     */
    void updateDailyRevenue(Connection conn, double amount, Date revenueDate) throws SQLException;

    /**
     * 记录订单到季度销售统计表
     */
    void recordQuarterlySales(Connection conn, int orderId) throws SQLException;

    /**
     * 获取指定餐桌的活跃订单头信息
     */
    Map<String, Object> getActiveOrderHeaderByTableId(Connection conn, int tableId) throws SQLException;

    /**
     * 获取指定订单的明细项列表
     */
    List<Map<String, Object>> getOrderItemsByOrderId(Connection conn, int orderId) throws SQLException;

    Integer findCheckedOutOrderIdByTableId(Connection conn, int tableId) throws SQLException;

    void updateOrderStatusAndAmount(Connection conn, int orderId, Tables.OrderStatus status, double amount) throws SQLException;

    /**
     * 检查餐桌是否存在历史结账标记（is_checked_out=TRUE）
     *
     * @return true=存在历史结账记录（重新点单场景）
     */
    boolean isOrderPreviouslyCheckedOut(Connection conn, int tableId) throws SQLException;

    void deleteTableOrdersByTableId(Connection conn, int tableId) throws SQLException;

    /**
     * 将订单从原餐桌迁移到新餐桌
     */
    void migrateOrdersToTable(Connection conn, int oldTableId, int newTableId) throws SQLException;

    /**
     * 迁移餐桌的所有订单记录到目标餐桌（包含所有状态：ORDERED / CHECKED_OUT）
     * <p>
     * 业务规则：
     * - 订单是消费历史记录，必须完整保留
     * - 迁移后原餐桌不再关联任何订单
     * - 用于共享餐桌/拆分餐桌等场景
     *
     * @param conn        事务连接
     * @param fromTableId 源餐桌ID
     * @param toTableId   目标餐桌ID
     * @throws SQLException
     */
    void migrateAllOrders(Connection conn, int fromTableId, int toTableId) throws SQLException;

    /**
     * 检查餐桌是否有任何订单记录
     *
     * @param conn    事务连接
     * @param tableId 餐桌ID
     * @return true=存在至少1个订单
     * @throws SQLException
     */
    boolean hasAnyOrders(Connection conn, int tableId) throws SQLException;

    /**
     * 获取餐桌最新订单状态（用于设置Tables.orderStatus瞬态字段）
     * <p>
     * 映射规则：
     * - 存在 ORDERED 状态订单 → ORDERED_UNFINISHED
     * - 仅存在 CHECKED_OUT 订单 → CHECKED_OUT
     * - 无订单 → NO_ORDER
     *
     * @param conn    事务连接
     * @param tableId 餐桌ID
     * @return 订单状态
     * @throws SQLException
     */
    Tables.OrderStatus getLatestOrderStatus(Connection conn, int tableId) throws SQLException;

    // 在 OrderDAO 接口末尾添加
    List<Map<String, Object>> getQuarterlyDishSalesReport(Connection conn, int year, String quarter, String category) throws SQLException;
    List<String> getAvailableYearsForDishSales(Connection conn) throws SQLException;
}
