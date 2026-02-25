package com.restaurant.dao.impl;

import com.restaurant.dao.OrderItemDAO;
import com.restaurant.entity.OrderItem;
import com.restaurant.entity.OrderItemServingStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class OrderItemDAOImpl implements OrderItemDAO {


    @Override
    public void addOrderItems(Connection conn, int orderId, List<OrderItem> items) throws SQLException {
        if (items == null || items.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO order_items (order_id, item_id, quantity, served_quantity, status, price_at_order) " +
                "VALUES (?, ?, ?, 0, 'UNSERVED', ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (OrderItem item : items) {
                ps.setInt(1, orderId);
                ps.setInt(2, item.getItemId());
                ps.setInt(3, item.getQuantity());
                ps.setDouble(4, item.getPriceAtOrder()); // ✅ 严格使用 double
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public void mergeOrderItems(Connection conn, int orderId, Map<String, Integer> newItems,
                                Function<String, Integer> getItemIdFunc,
                                Function<String, Double> getPriceFunc) throws SQLException {
        // 1. 获取现有订单项
        Map<String, Integer> existingItems = new HashMap<>();
        String checkSql = "SELECT mi.item_code, oi.quantity FROM order_items oi " +
                "JOIN menu_items mi ON oi.item_id = mi.item_id " +
                "WHERE oi.order_id = ?";

        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setInt(1, orderId);
            try (ResultSet rs = checkPs.executeQuery()) {
                while (rs.next()) {
                    existingItems.put(rs.getString("item_code"), rs.getInt("quantity"));
                }
            }
        }

        // 2. 合并逻辑：更新已有项 + 插入新项
        String updateSql = "UPDATE order_items oi " +
                "JOIN menu_items mi ON oi.item_id = mi.item_id " +
                "SET oi.quantity = oi.quantity + ?, oi.price_at_order = ? " +
                "WHERE oi.order_id = ? AND mi.item_code = ?";

        String insertSql = "INSERT INTO order_items (order_id, item_id, quantity, served_quantity, status, price_at_order) " +
                "SELECT ?, mi.item_id, ?, 0, 'UNSERVED', ? " +
                "FROM menu_items mi WHERE mi.item_code = ?";

        try (PreparedStatement updatePs = conn.prepareStatement(updateSql);
             PreparedStatement insertPs = conn.prepareStatement(insertSql)) {

            for (Map.Entry<String, Integer> entry : newItems.entrySet()) {
                String itemCode = entry.getKey();
                int newQty = entry.getValue();
                double price = getPriceFunc.apply(itemCode); // ✅ double
                int dbItemId = getItemIdFunc.apply(itemCode);

                if (existingItems.containsKey(itemCode)) {
                    // 更新已有项
                    updatePs.setInt(1, newQty);
                    updatePs.setDouble(2, price); // ✅ setDouble
                    updatePs.setInt(3, orderId);
                    updatePs.setString(4, itemCode);
                    updatePs.addBatch();
                } else {
                    // 插入新项
                    insertPs.setInt(1, orderId);
                    insertPs.setInt(2, newQty);
                    insertPs.setDouble(3, price); // ✅ setDouble
                    insertPs.setString(4, itemCode);
                    insertPs.addBatch();
                }
            }

            updatePs.executeBatch();
            insertPs.executeBatch();
        }

        // 3. 重新计算订单总金额
        recalculateOrderTotal(conn, orderId);
    }


    // 重新计算订单总金额
    public void recalculateOrderTotal(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT SUM(quantity * price_at_order) as total FROM order_items WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                double newTotal = 0.0;
                if (rs.next()) {
                    // ✅ 修正：wasNull() 不接受参数，必须在 getDouble() 后立即调用
                    newTotal = rs.getDouble("total");
                    if (rs.wasNull()) {  // 检查上一次 getDouble() 是否返回 NULL
                        newTotal = 0.0;
                    }
                }

                // 更新订单总金额
                String updateSql = "UPDATE table_orders SET total_amount = ? WHERE order_id = ?";
                try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setDouble(1, newTotal); // ✅ double
                    updatePs.setInt(2, orderId);
                    updatePs.executeUpdate();
                }
            }
        }
    }


    @Override
    public OrderItemServingStatus getServingStatus(Connection conn, int orderId, int itemId) throws SQLException {
        String sql = "SELECT quantity, served_quantity FROM order_items WHERE order_id = ? AND item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new OrderItemServingStatus(
                            rs.getInt("quantity"),
                            rs.getInt("served_quantity")
                    );
                }
                throw new SQLException("未找到訂單明細: order_id=" + orderId + ", item_id=" + itemId);
            }
        }
    }

    @Override
    public boolean incrementServedQuantity(Connection conn, int orderId, int itemId, int increment)
            throws SQLException, IllegalArgumentException {

        if (increment <= 0) {
            throw new IllegalArgumentException("增量必須大於0");
        }

        // 1. 獲取當前狀態
        OrderItemServingStatus status = getServingStatus(conn, orderId, itemId);
        int newServedQuantity = status.getServedQuantity() + increment;

        // 2. 驗證數量合法性
        if (newServedQuantity > status.getQuantity()) {
            throw new IllegalArgumentException(
                    String.format("上桌數量(%d)超過點菜數量(%d)", newServedQuantity, status.getQuantity()));
        }

        // 3. 計算新狀態（✅ 關鍵：直接使用字符串常量，不依賴 OrderStatus 枚舉）
        String newStatus = (newServedQuantity >= status.getQuantity()) ?
                "SERVED" : "PARTIALLY_SERVED";

        // 4. 執行更新
        String sql = "UPDATE order_items SET served_quantity = ?, status = ? WHERE order_id = ? AND item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newServedQuantity);
            ps.setString(2, newStatus);  // ✅ 直接傳字符串
            ps.setInt(3, orderId);
            ps.setInt(4, itemId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public int markAllItemsAsServed(Connection conn, int orderId) throws SQLException {
        //  關鍵：使用字符串常量替代不存在的枚舉
        String sql = "UPDATE order_items SET served_quantity = quantity, status = ? " +
                "WHERE order_id = ? AND status IN (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "SERVED");
            ps.setInt(2, orderId);
            ps.setString(3, "UNSERVED");
            ps.setString(4, "PARTIALLY_SERVED");
            return ps.executeUpdate();
        }
    }

    @Override
    public boolean hasUnservedItems(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM order_items WHERE order_id = ? AND served_quantity < quantity) AS has_unserved";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("has_unserved");
            }
        }
    }

    private String calculateNewStatus(int quantity, int servedQuantity) {
        if (servedQuantity == 0) {
            return "UNSERVED";
        } else if (servedQuantity < quantity) {
            return "PARTIALLY_SERVED";
        } else {
            return "SERVED";
        }
    }

    @Override
    public boolean cancelOrderItem(Connection conn, int orderId, int itemId,
                                   int cancelQuantity, String cancellationReason) throws SQLException {
        // 1. 验证撤销数量
        if (cancelQuantity <= 0) {
            throw new IllegalArgumentException("撤销数量必须大于0");
        }

        // 2. 获取当前状态
        OrderItemServingStatus status = getServingStatus(conn, orderId, itemId);
        int currentQty = status.getQuantity();
        int servedQty = status.getServedQuantity();

        // 3. 验证数量范围
        if (cancelQuantity > currentQty) {
            throw new IllegalArgumentException(
                    String.format("撤销数量(%d)超过订单数量(%d)", cancelQuantity, currentQty));
        }

        // 4. 已上桌菜品必须提供原因
        if (servedQty > 0 && (cancellationReason == null || cancellationReason.trim().isEmpty())) {
            throw new IllegalArgumentException("已上桌菜品必须提供撤销原因");
        }

        // 5. 获取撤销前状态
        String beforeStatus = null;
        String getStatusSql = "SELECT status FROM order_items WHERE order_id = ? AND item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(getStatusSql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    beforeStatus = rs.getString("status");
                }
            }
        }

        // 6. 计算新数量
        int newQty = currentQty - cancelQuantity;
        int newServedQty = Math.min(servedQty, newQty);

        // 7. 执行操作（核心：数量归零 → DELETE）
        if (newQty == 0) {
            // 完全撤销：删除订单项
            String deleteSql = "DELETE FROM order_items WHERE order_id = ? AND item_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setInt(1, orderId);
                ps.setInt(2, itemId);
                int rowsAffected = ps.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("订单项删除失败");
                }
            }

            // 记录审计日志
            if (servedQty > 0 && cancellationReason != null && !cancellationReason.trim().isEmpty()) {
                String itemCode = getItemCodeByItemId(conn, itemId);
                recordCancellation(conn, itemCode, cancelQuantity, cancellationReason, beforeStatus);
            }

        } else {
            // 部分撤销：更新数量和状态
            String newStatus = calculateNewStatus(newQty, newServedQty);

            String updateSql = "UPDATE order_items SET quantity = ?, served_quantity = ?, status = ? " +
                    "WHERE order_id = ? AND item_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, newQty);
                ps.setInt(2, newServedQty);
                ps.setString(3, newStatus);
                ps.setInt(4, orderId);
                ps.setInt(5, itemId);
                int rowsAffected = ps.executeUpdate();
                if (rowsAffected == 0) {
                    throw new SQLException("订单项更新失败");
                }
            }

            // 记录审计日志
            if (servedQty > 0 && cancellationReason != null && !cancellationReason.trim().isEmpty()) {
                String itemCode = getItemCodeByItemId(conn, itemId);
                recordCancellation(conn, itemCode, cancelQuantity, cancellationReason, beforeStatus);
            }
        }

        return true;
    }

    @Override
    public boolean hasRemainingItems(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT COUNT(*) AS count FROM order_items WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("count") > 0;
            }
        }
    }

    @Override
    public void recordCancellation(Connection conn, String itemCode, int cancelledQuantity,
                                   String reason, String beforeStatus) throws SQLException {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("撤销原因不能为空");
        }

        String sql = "INSERT INTO item_cancellations (item_code, cancelled_quantity, " +
                "cancellation_reason, before_status, cancellation_time) " +
                "VALUES (?, ?, ?, ?, NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            ps.setInt(2, cancelledQuantity);
            ps.setString(3, reason.trim());
            ps.setString(4, beforeStatus);
            ps.executeUpdate();
        }
    }

    private String getItemCodeByItemId(Connection conn, int itemId) throws SQLException {
        String sql = "SELECT item_code FROM menu_items WHERE item_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("item_code");
                }
                throw new SQLException("菜品ID不存在: " + itemId);
            }
        }
    }


    @Override
    public void deleteOrderItemsByOrderId(Connection conn, int orderId) throws SQLException {
        String sql = "DELETE FROM order_items WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }

    @Override
    public boolean hasServedItems(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT COUNT(*) AS count FROM order_items " +
                "WHERE order_id = ? AND status IN ('PARTIALLY_SERVED', 'SERVED')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        }
        return false;
    }
}