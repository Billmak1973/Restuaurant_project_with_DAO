package com.restaurant.dao.impl;

import com.restaurant.dao.OrderDAO;
import com.restaurant.entity.OrderItemGroup;
import com.restaurant.entity.Tables;

import java.sql.*;
import java.sql.Date;
import java.util.*;


public class OrderDAOImpl implements OrderDAO {


    @Override
    public int createOrder(Connection conn, int tableId, double totalAmount) throws SQLException {
        String sql = "INSERT INTO table_orders (table_id, order_time, status, total_amount, is_checked_out) " +
                "VALUES (?, NOW(), 'ORDERED', ?, FALSE)";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, tableId);
            ps.setDouble(2, totalAmount);

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("创建订单失败，未插入记录");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1); // 返回生成的订单ID
                } else {
                    throw new SQLException("创建订单失败，未获取到订单ID");
                }
            }
        }
    }
    @Override
    public Integer findActiveOrderIdByTableId(Connection conn, int tableId) throws SQLException {
        String sql = "SELECT order_id FROM table_orders " +
                "WHERE table_id = ? AND status = 'ORDERED' LIMIT 1";


        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("order_id"); // 返回订单ID
                }
                return null; // 无活跃订单
            }
        }

    }



    @Override
    public boolean deleteOrder(Connection conn, int orderId) throws SQLException {
        // 1. 验证订单是否为空
        String checkSql = "SELECT COUNT(*) AS item_count FROM order_items WHERE order_id = ?";
        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            checkPs.setInt(1, orderId);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next() && rs.getInt("item_count") > 0) {
                    throw new SQLException("订单仍有菜品，不能删除");
                }
            }
        }

        // 2. 删除订单
        String deleteSql = "DELETE FROM table_orders WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setInt(1, orderId);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        }
    }

    @Override
    public double getOrderTotalAmount(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT total_amount FROM table_orders " +
                "WHERE order_id = ? AND status = 'ORDERED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("total_amount");
                }
                throw new SQLException("订单不存在或非活跃状态");
            }
        }
    }

    @Override
    public Timestamp getOrderCreateTime(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT order_time FROM table_orders " +
                "WHERE order_id = ? AND status = 'ORDERED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("order_time");
                }
                throw new SQLException("订单不存在或非活跃状态");
            }
        }
    }

    @Override
    public void checkoutOrder(Connection conn, int orderId) throws SQLException {
        String sql = "UPDATE table_orders SET status = 'CHECKED_OUT', is_checked_out = TRUE " +
                "WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateDailyRevenue(Connection conn, double amount, Date revenueDate) throws SQLException {
        String sql = "UPDATE business_status SET daily_revenue = daily_revenue + ? " +
                "WHERE business_date = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setDate(2, revenueDate);
            int rowsAffected = ps.executeUpdate();

            if (rowsAffected == 0) {
                String insertSql = "INSERT INTO business_status (business_date, daily_revenue) " +
                        "VALUES (?, ?)";
                try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    insertPs.setDate(1, revenueDate);
                    insertPs.setDouble(2, amount);
                    insertPs.executeUpdate();
                }
            }
        }
    }

    @Override
    public void recordQuarterlySales(Connection conn, int orderId) throws SQLException {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        String quarter = (month <= 3) ? "Q1" : (month <= 6) ? "Q2" : (month <= 9) ? "Q3" : "Q4";

        Map<String, OrderItemGroup> itemGroups = new HashMap<>();
        String sql = "SELECT mi.item_code, mi.name, oi.price_at_order, oi.quantity " +
                "FROM order_items oi " +
                "JOIN menu_items mi ON oi.item_id = mi.item_id " +
                "WHERE oi.order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String itemCode = rs.getString("item_code");
                    String itemName = rs.getString("name");
                    double salePrice = rs.getDouble("price_at_order");
                    int quantity = rs.getInt("quantity");
                    String key = itemCode + "|" + itemName + "|" + salePrice;
                    itemGroups.computeIfAbsent(key, k ->
                            new OrderItemGroup(itemCode, itemName, salePrice, 0)
                    ).addQuantity(quantity);
                }
            }
        }

        if (itemGroups.isEmpty()) return;

        List<OrderItemGroup> itemsToUpdate = new ArrayList<>();
        List<OrderItemGroup> itemsToInsert = new ArrayList<>();

        String checkSql = "SELECT sales_id FROM item_quarterly_sales " +
                "WHERE item_code = ? AND item_name = ? AND sale_price = ? " +
                "AND year = ? AND quarter = ?";
        try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
            for (OrderItemGroup group : itemGroups.values()) {
                checkPs.setString(1, group.getItemCode());
                checkPs.setString(2, group.getItemName());
                checkPs.setDouble(3, group.getSalePrice());
                checkPs.setInt(4, year);
                checkPs.setString(5, quarter);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        group.setSalesId(rs.getInt("sales_id"));
                        itemsToUpdate.add(group);
                    } else {
                        itemsToInsert.add(group);
                    }
                }
            }
        }

        if (!itemsToUpdate.isEmpty()) {
            String updateSql = "UPDATE item_quarterly_sales " +
                    "SET quantity_sold = quantity_sold + ?, " +
                    "sale_timestamp = NOW() " +
                    "WHERE sales_id = ?";
            try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                for (OrderItemGroup group : itemsToUpdate) {
                    updatePs.setInt(1, group.getQuantity());
                    updatePs.setInt(2, group.getSalesId());
                    updatePs.addBatch();
                }
                updatePs.executeBatch();
            }
        }

        if (!itemsToInsert.isEmpty()) {
            String insertSql = "INSERT INTO item_quarterly_sales " +
                    "(item_code, item_name, sale_price, quantity_sold, sale_timestamp, year, quarter) " +
                    "VALUES (?, ?, ?, ?, NOW(), ?, ?)";
            try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                for (OrderItemGroup group : itemsToInsert) {
                    insertPs.setString(1, group.getItemCode());
                    insertPs.setString(2, group.getItemName());
                    insertPs.setDouble(3, group.getSalePrice());
                    insertPs.setInt(4, group.getQuantity());
                    insertPs.setInt(5, year);
                    insertPs.setString(6, quarter);
                    insertPs.addBatch();
                }
                insertPs.executeBatch();
            }
        }
    }

    // 添加到 OrderDAOImpl.java 类中
    @Override
    public Map<String, Object> getActiveOrderHeaderByTableId(Connection conn, int tableId) throws SQLException {
        String sql = "SELECT o.order_time, o.total_amount, o.order_id " +
                "FROM table_orders o " +
                "WHERE o.table_id = ? AND o.status = 'ORDERED' " +
                "ORDER BY o.order_time DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> header = new HashMap<>();
                    header.put("orderTime", rs.getTimestamp("order_time"));
                    header.put("totalAmount", rs.getDouble("total_amount"));
                    header.put("orderId", rs.getInt("order_id"));
                    return header;
                }
                return null; // 无活跃订单
            }
        }
    }

    @Override
    public List<Map<String, Object>> getOrderItemsByOrderId(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT mi.item_code, mi.name, oi.quantity, oi.served_quantity, oi.price_at_order " +
                "FROM order_items oi " +
                "JOIN menu_items mi ON oi.item_id = mi.item_id " +
                "WHERE oi.order_id = ?";
        List<Map<String, Object>> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("itemCode", rs.getString("item_code"));
                    item.put("itemName", rs.getString("name"));
                    item.put("quantity", rs.getInt("quantity"));
                    item.put("servedQuantity", rs.getInt("served_quantity"));
                    item.put("price", rs.getDouble("price_at_order"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    // OrderDAOImpl.java 实现
    @Override
    public Integer findCheckedOutOrderIdByTableId(Connection conn, int tableId) throws SQLException {
        String sql = "SELECT order_id FROM table_orders " +
                "WHERE table_id = ? AND status = 'CHECKED_OUT' " +
                "ORDER BY order_time DESC LIMIT 1";  // ✅ 修复：created_time → order_time
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("order_id") : null;
        }
    }


    @Override
    public void updateOrderStatusAndAmount(Connection conn, int orderId, Tables.OrderStatus status, double amount) throws SQLException {
        // 映射 Java 枚举 → 数据库 ENUM 值
        String dbStatus = (status == Tables.OrderStatus.CHECKED_OUT) ? "CHECKED_OUT" : "ORDERED";

        String sql = "UPDATE table_orders SET status = ?, total_amount = ? WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbStatus);  // ✅ 传入数据库接受的值
            ps.setDouble(2, amount);
            ps.setInt(3, orderId);
            ps.executeUpdate();
        }
    }
    @Override
    public boolean isOrderPreviouslyCheckedOut(Connection conn, int tableId) throws SQLException {
        // 查询是否存在 is_checked_out=TRUE 的订单记录
        String sql = "SELECT 1 FROM table_orders " +
                "WHERE table_id = ? AND is_checked_out = TRUE LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next(); // 存在即返回 true
            }
        }
    }

    public void deleteTableOrdersByTableId(Connection conn, int tableId) throws SQLException {
        String sql = "DELETE FROM table_orders WHERE table_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            ps.executeUpdate();
        }
    }


    public void migrateOrdersToTable(Connection conn, int oldTableId, int newTableId) throws SQLException {
        String sql = "UPDATE table_orders SET table_id = ? WHERE table_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newTableId);
            ps.setInt(2, oldTableId);
            ps.executeUpdate();
        }
    }

    @Override
    public void migrateAllOrders(Connection conn, int fromTableId, int toTableId) throws SQLException {
        // 严格匹配您的 table_orders 表结构
        String sql = "UPDATE table_orders SET table_id = ? WHERE table_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, toTableId);
            ps.setInt(2, fromTableId);
            ps.executeUpdate();
            System.out.println("✅ 迁移订单: 从餐桌 #" + fromTableId + " → #" + toTableId);
        }
    }

    @Override
    public boolean hasAnyOrders(Connection conn, int tableId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM table_orders WHERE table_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    @Override
    public Tables.OrderStatus getLatestOrderStatus(Connection conn, int tableId) throws SQLException {
        // 步骤1: 查询订单是否存在
        String orderSql = "SELECT status FROM table_orders WHERE table_id = ? AND status = 'ORDERED' LIMIT 1";
        boolean hasActiveOrder = false;
        try (PreparedStatement ps = conn.prepareStatement(orderSql)) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                hasActiveOrder = rs.next();
            }
        }

        if (!hasActiveOrder) {
            // 无活跃订单 → 检查是否有历史结账订单
            String checkedOutSql = "SELECT 1 FROM table_orders WHERE table_id = ? AND status = 'CHECKED_OUT' LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(checkedOutSql)) {
                ps.setInt(1, tableId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Tables.OrderStatus.CHECKED_OUT : Tables.OrderStatus.NO_ORDER;
                }
            }
        }

        // 步骤2: 有活跃订单 → 检查上菜状态（关键！）
        String servedSql = "SELECT COUNT(*) AS unserved_count " +
                "FROM order_items oi " +
                "JOIN table_orders o ON oi.order_id = o.order_id " +
                "WHERE o.table_id = ? AND o.status = 'ORDERED' AND oi.served_quantity < oi.quantity";

        try (PreparedStatement ps = conn.prepareStatement(servedSql)) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt("unserved_count") > 0) {
                    return Tables.OrderStatus.ORDERED_UNFINISHED; // 有未上桌菜品
                } else {
                    return Tables.OrderStatus.ORDERED_FINISHED;  // 全部上桌但未结账 正确状态
                }
            }
        }
    }

    // OrderDAOImpl.java 末尾添加实现

    @Override
    public List<Map<String, Object>> getQuarterlyDishSalesReport(Connection conn, int year, String quarter, String category) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();

        StringBuilder sqlBuilder = new StringBuilder(
                "SELECT item_code, item_name, " +
                        "SUM(quantity_sold) AS total_quantity, " +
                        "SUM(quantity_sold * sale_price) AS total_revenue, " +
                        "AVG(sale_price) AS avg_price, " +
                        "COUNT(DISTINCT DATE(sale_timestamp)) AS active_days " +
                        "FROM item_quarterly_sales " +
                        "WHERE year = ? AND quarter = ? "
        );

        int paramIndex = 1;
        if (!"全部".equals(category)) {
            sqlBuilder.append("AND item_code LIKE ? ");
            paramIndex = 3;
        }

        sqlBuilder.append("GROUP BY item_code, item_name ORDER BY total_revenue DESC");

        try (PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {
            pstmt.setInt(1, year);
            pstmt.setString(2, quarter);
            if (!"全部".equals(category)) {
                pstmt.setString(3, category + "%");
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> itemData = new HashMap<>();
                    itemData.put("itemCode", rs.getString("item_code"));
                    itemData.put("itemName", rs.getString("item_name"));
                    itemData.put("category", rs.getString("item_code").substring(0, 1));
                    itemData.put("total_quantity", rs.getInt("total_quantity"));
                    itemData.put("total_revenue", rs.getDouble("total_revenue"));
                    itemData.put("avg_price", rs.getDouble("avg_price"));
                    itemData.put("active_days", rs.getInt("active_days"));
                    result.add(itemData);
                }
            }
        }
        return result;
    }

    @Override
    public List<String> getAvailableYearsForDishSales(Connection conn) throws SQLException {
        List<String> years = new ArrayList<>();

        String sql = "SELECT DISTINCT year FROM item_quarterly_sales ORDER BY year DESC";

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                years.add(String.valueOf(rs.getInt("year")));
            }
        }

        // 无数据时返回当前年份
        if (years.isEmpty()) {
            years.add(String.valueOf(java.time.LocalDate.now().getYear()));
        }
        return years;
    }
}