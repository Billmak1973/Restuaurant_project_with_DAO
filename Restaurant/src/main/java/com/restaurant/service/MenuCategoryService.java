package com.restaurant.service;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 菜单分类服务 - 配置数据加载器（非传统业务Service）
 * 作用：启动时一次性加载4个固定分类到内存，避免硬编码分类ID
 * 设计原则：轻量级、单例、无事务（仅配置数据）
 */
public class MenuCategoryService {
    private static final MenuCategoryService INSTANCE = new MenuCategoryService();
    private final Map<String, Integer> menuTypeToCategoryId = new ConcurrentHashMap<>();
    private final Map<Integer, String> categoryIdToPrefix = new ConcurrentHashMap<>();
    private boolean initialized = false;
    private final Object initLock = new Object();

    private MenuCategoryService() {}

    public static MenuCategoryService getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化：从数据库加载4个固定分类到内存
     * 调用时机：RestaurantModel 构造函数中（应用启动时）
     */
    public void initialize() {
        if (initialized) return;

        synchronized (initLock) {
            if (initialized) return;

            try (Connection conn = ConnectionPool.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT category_id, name, prefix FROM menu_categories ORDER BY category_id")) {

                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int categoryId = rs.getInt("category_id");
                    String name = rs.getString("name");
                    String prefix = rs.getString("prefix");

                    // 建立映射：UI类型 → 分类ID
                    switch (name) {
                        case "特色食物" -> menuTypeToCategoryId.put("FOOD", categoryId);
                        case "饮料" -> menuTypeToCategoryId.put("DRINK", categoryId);
                        case "小炒" -> menuTypeToCategoryId.put("STIRFRY", categoryId);
                        case "套餐" -> menuTypeToCategoryId.put("SETMEAL", categoryId);
                    }

                    // 建立映射：分类ID → 前缀
                    categoryIdToPrefix.put(categoryId, prefix);
                }

                initialized = true;
                System.out.println("✅ MenuCategoryService 初始化成功，加载 " + menuTypeToCategoryId.size() + " 个分类");
            } catch (SQLException e) {
                System.err.println("❌ MenuCategoryService 初始化失败: " + e.getMessage());
                e.printStackTrace();
                // 失败时使用安全默认值（与数据库初始化一致）
                menuTypeToCategoryId.put("FOOD", 1);
                menuTypeToCategoryId.put("DRINK", 2);
                menuTypeToCategoryId.put("STIRFRY", 3);
                menuTypeToCategoryId.put("SETMEAL", 4);
                categoryIdToPrefix.put(1, "A");
                categoryIdToPrefix.put(2, "B");
                categoryIdToPrefix.put(3, "C");
                categoryIdToPrefix.put(4, "D");
                initialized = true;
            }
        }
    }

    /**
     * 根据菜单类型获取分类ID（供 MenuItemDAO 使用）
     * @param menuType "FOOD"/"DRINK"/"STIRFRY"/"SETMEAL"
     * @return category_id (1/2/3/4)
     */
    public int getCategoryIdByMenuType(String menuType) {
        if (!initialized) initialize();
        Integer id = menuTypeToCategoryId.get(menuType);
        if (id == null) {
            throw new IllegalArgumentException("未知的菜单类型: " + menuType);
        }
        return id;
    }

    /**
     * 根据分类ID获取菜品前缀（A/B/C/D）
     */
    public String getPrefixByCategoryId(int categoryId) {
        if (!initialized) initialize();
        return categoryIdToPrefix.getOrDefault(categoryId, "X");
    }

    /**
     * 获取下一个可用菜品编号（线程安全）
     * @param menuType 菜单类型
     * @return 如 "A5", "B3" 等
     */
    public String getNextItemCode(String menuType) throws SQLException {
        if (!initialized) initialize();

        int categoryId = getCategoryIdByMenuType(menuType);
        String prefix = getPrefixByCategoryId(categoryId);

        try (Connection conn = ConnectionPool.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT MAX(CAST(SUBSTRING(item_code, 2) AS UNSIGNED)) as max_num " +
                             "FROM menu_items WHERE item_code LIKE ?")) {

            ps.setString(1, prefix + "%");
            ResultSet rs = ps.executeQuery();

            // 修正：正确使用 wasNull()
            int maxNum = 0;
            if (rs.next()) {
                // 获取值后立即检查是否为null
                maxNum = rs.getInt("max_num");
                if (rs.wasNull()) {
                    return prefix + "1";
                }
            }

            return prefix + (maxNum + 1);
        }
    }
}