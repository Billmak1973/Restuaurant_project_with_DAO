package com.restaurant.dao.impl;

import com.restaurant.dao.MenuItemDAO;
import com.restaurant.entity.MenuItem;
import com.restaurant.service.ConnectionPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MenuItemDAOImpl implements MenuItemDAO {

    @Override
    public boolean addItem(MenuItem item) throws SQLException {
        String sql = "INSERT INTO menu_items (item_code, name, price, category_id, is_active) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, item.getItemCode());
            ps.setString(2, item.getName());
            ps.setDouble(3, item.getPrice());
            ps.setInt(4, item.getCategoryId());
            ps.setBoolean(5, item.isActive());

            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public List<MenuItem> findByCategory(int categoryId) throws SQLException {
        String sql = "SELECT item_id, item_code, name, price, category_id, is_active " +
                "FROM menu_items WHERE category_id = ? ORDER BY item_code";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, categoryId);
            ResultSet rs = ps.executeQuery();

            List<MenuItem> items = new ArrayList<>();
            while (rs.next()) {
                items.add(new MenuItem(
                        rs.getInt("item_id"),
                        rs.getString("item_code"),
                        rs.getString("name"),
                        rs.getDouble("price"),  //  使用 getDouble
                        rs.getInt("category_id"),
                        rs.getBoolean("is_active")
                ));
            }
            return items;
        }
    }

    @Override
    public boolean updateStatus(String itemCode, boolean isActive) throws SQLException {
        // 标准 DAO 实现：使用预编译语句防止 SQL 注入
        String sql = "UPDATE menu_items SET is_active = ? WHERE item_code = ?";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, isActive);   // true=售卖中, false=已售罄
            ps.setString(2, itemCode);    // 菜品编号（如 "A1"）

            // 返回受影响行数 > 0 表示更新成功
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        }
    }

    @Override
    public MenuItem findById(String itemCode) throws SQLException {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            return null;
        }

        String sql = "SELECT item_id, item_code, name, price, is_active, category_id " +
                "FROM menu_items " +
                "WHERE item_code = ?";

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemCode.trim().toUpperCase());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToMenuItem(rs);
                }
                return null; // 未找到
            }
        }
    }

    /**
     * 将 ResultSet 映射为 MenuItem 对象（使用构造函数，因实体类无完整 setter）
     */
    private MenuItem mapResultSetToMenuItem(ResultSet rs) throws SQLException {
        int itemId = rs.getInt("item_id");
        String itemCode = rs.getString("item_code");
        String name = rs.getString("name");
        double price = rs.getDouble("price");
        boolean isActive = rs.getBoolean("is_active");
        int categoryId = rs.getInt("category_id");

        //  使用完整构造函数（实体类无 setter，必须用构造函数）
        return new MenuItem(itemId, itemCode, name, price, categoryId, isActive);
    }


    @Override
    public boolean deletePhysically(String itemCode) throws SQLException {
        //  仅操作 menu_items 表，绝不碰 order_items
        String sql = "DELETE FROM menu_items WHERE item_code = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            return ps.executeUpdate() > 0; // 无外键约束时成功；有约束时数据库自动拒绝（返回0）
        }
        // 注意：不主动抛出异常，让 executeUpdate() 返回 0 表示删除失败
    }

    @Override
    public boolean existsInOrderItems(String itemCode) throws SQLException {
        String sql = """
        SELECT EXISTS(
            SELECT 1 FROM order_items oi
            JOIN menu_items mi ON oi.item_id = mi.item_id
            WHERE mi.item_code = ?
        ) AS exists_flag
        """;
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("exists_flag");
            }
        }
    }

    @Override
    public boolean updatePrice(String itemCode, double newPrice) throws SQLException {
        // DAO层只做数据持久化，不包含业务验证
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("菜品编号不能为空");
        }
        if (newPrice <= 0) {
            throw new IllegalArgumentException("价格必须大于0");
        }

        //  使用PreparedStatement防止SQL注入
        String sql = "UPDATE menu_items SET price = ? WHERE item_code = ?";
        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);          // 严格使用setDouble处理价格
            ps.setString(2, itemCode.trim().toUpperCase());
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;            // 返回是否找到并更新了记录
        }
    }

    @Override
    public Integer findItemIdByCode(Connection conn, String itemCode) throws SQLException {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("菜品编号不能为空");
        }
        String sql = "SELECT item_id FROM menu_items WHERE item_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemCode.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("item_id") : null;
            }
        }
    }

}
