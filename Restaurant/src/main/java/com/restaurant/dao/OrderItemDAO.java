package com.restaurant.dao;

import com.restaurant.entity.OrderItem;
import com.restaurant.entity.OrderItemServingStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface OrderItemDAO {
    void addOrderItems(Connection conn, int orderId, List<OrderItem> items) throws SQLException;

    void mergeOrderItems(Connection conn, int orderId, Map<String, Integer> newItems,
                         Function<String, Integer> getItemIdFunc,
                         Function<String, Double> getPriceFunc) throws SQLException;

    /**
     * 獲取指定訂單項的當前上菜狀態
     * @param conn 數據庫連接（由外部管理事務）
     * @param orderId 訂單ID
     * @param itemId 菜品ID
     * @return 當前上菜狀態
     * @throws SQLException 數據庫操作失敗
     */
    OrderItemServingStatus getServingStatus(Connection conn, int orderId, int itemId) throws SQLException;

    /**
     * 增加指定訂單項的已上桌數量
     * @param conn 數據庫連接
     * @param orderId 訂單ID
     * @param itemId 菜品ID
     * @param increment 增加數量（必須 > 0）
     * @return 是否成功更新
     * @throws IllegalArgumentException 當增量無效或超量時
     * @throws SQLException 數據庫操作失敗
     */
    boolean incrementServedQuantity(Connection conn, int orderId, int itemId, int increment)
            throws SQLException, IllegalArgumentException;

    /**
     * 一鍵標記訂單中所有未完成菜品為已上桌
     * @param conn 數據庫連接
     * @param orderId 訂單ID
     * @return 成功更新的菜品數量
     * @throws SQLException 數據庫操作失敗
     */
    int markAllItemsAsServed(Connection conn, int orderId) throws SQLException;

    /**
     * 檢查訂單是否有未上桌菜品（served_quantity < quantity）
     * @param conn 數據庫連接
     * @param orderId 訂單ID
     * @return true=存在未上桌菜品
     * @throws SQLException 數據庫操作失敗
     */
    boolean hasUnservedItems(Connection conn, int orderId) throws SQLException;




    void recalculateOrderTotal(Connection conn, int orderId) throws SQLException; // 添加到接口


    /**
     * 撤销订单项（数量归零时直接删除）
     */
    boolean cancelOrderItem(Connection conn, int orderId, int itemId,
                            int cancelQuantity, String cancellationReason) throws SQLException;


    /**
     * 检查订单是否还有剩余菜品
     */
    boolean hasRemainingItems(Connection conn, int orderId) throws SQLException;

    /**
     * 记录撤销审计日志
     */
    void recordCancellation(Connection conn, String itemCode, int cancelledQuantity,
                            String reason, String beforeStatus) throws SQLException;


    void deleteOrderItemsByOrderId(Connection conn, int orderId) throws SQLException;

    /**
     * 检查指定订单是否有已上菜的菜品（状态为 PARTIALLY_SERVED 或 SERVED）
     * @param conn 数据库连接
     * @param orderId 订单ID
     * @return true=存在已上菜菜品，false=全部为PENDING状态
     * @throws SQLException
     */
    boolean hasServedItems(Connection conn, int orderId) throws SQLException;
}
