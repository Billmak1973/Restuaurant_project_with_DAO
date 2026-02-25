package com.restaurant.view;

import com.restaurant.controller.RestaurantController;
import com.restaurant.dao.MenuItemDAO;
import com.restaurant.dao.impl.MenuItemDAOImpl;
import com.restaurant.entity.OrderItem;
import com.restaurant.entity.Tables;
import com.restaurant.model.RestaurantModel;
import com.restaurant.service.ConnectionPool;

import javax.swing.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * com.restaurant.view.OrderSystemGUI - 订单系统主窗口（纯容器框架）
 * 仅负责面板切换，不参与任何布局细节
 */
public class OrderSystemGUI extends JFrame {
    private final RestaurantController controller;
    private final RestaurantModel model;

    // 面板缓存（懒加载）
    private HomePanel homePanel;
    private MenuPanel foodPanel;
    private MenuPanel drinkPanel;
    private MenuPanel stirFryPanel;
    private MenuPanel setMealPanel;

    private String currentTableNumber = "";
    private final Map<String, Boolean> menuItemStatusCache = new ConcurrentHashMap<>();

    // 临时订单缓存：餐桌号 -> (菜品ID -> 数量) - 改为 ConcurrentHashMap 保持一致性
    private final Map<String, Map<String, Integer>> temporaryOrders = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * @param controller 餐厅控制器（当前仅传递，未使用）
     * @param model 餐厅模型（当前仅传递，未使用）
     */
    public OrderSystemGUI(RestaurantController controller, RestaurantModel model) {
        this.controller = controller;
        this.model = model;

        // 窗口基础设置
        setTitle("订单系统");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);

        // 默认显示 com.restaurant.view.HomePanel
        showPanel("Home");
    }

    /**
     * 面板切换方法（卡片式切换）
     * @param panelType 面板类型：Home/Food/Drink/StirFry/SetMeal
     */
    public void showPanel(String panelType) {
        getContentPane().removeAll();  // 清空当前内容

        switch (panelType.toLowerCase()) {
            case "home":
                if (homePanel == null) {
                    homePanel = new HomePanel(this, model, controller);
                }
                getContentPane().add(homePanel);
                break;

            case "food":
                if (foodPanel == null) {
                    foodPanel = new MenuPanel(this, model, controller, MenuPanel.FOOD);
                }
                foodPanel.setCurrentTableNumber(currentTableNumber);
                getContentPane().add(foodPanel);
                break;

            case "drink":
                if (drinkPanel == null) {
                    drinkPanel = new MenuPanel(this, model, controller, MenuPanel.DRINK);
                }
                drinkPanel.setCurrentTableNumber(currentTableNumber);
                getContentPane().add(drinkPanel);
                break;

            case "stirfry":
                if (stirFryPanel == null) {
                    stirFryPanel = new MenuPanel(this, model, controller, MenuPanel.STIRFRY);
                }
                stirFryPanel.setCurrentTableNumber(currentTableNumber);
                getContentPane().add(stirFryPanel);
                break;

            case "setmeal":
                if (setMealPanel == null) {
                    setMealPanel = new MenuPanel(this, model, controller, MenuPanel.SETMEAL);
                }
                setMealPanel.setCurrentTableNumber(currentTableNumber);
                getContentPane().add(setMealPanel);
                break;
        }

        revalidate();
        repaint();
    }

    // OrderSystemGUI.java - 修复后
    public void setCurrentTableNumber(String tableNumber) {
        this.currentTableNumber = tableNumber.trim();

        // 同步到所有Panel并强制刷新
        if (homePanel != null) {
            homePanel.setCurrentTableNumber(this.currentTableNumber);
            homePanel.refreshTemporaryOrderDisplay();  // ✅ 强制刷新
        }
        if (foodPanel != null) foodPanel.setCurrentTableNumber(this.currentTableNumber);
        if (drinkPanel != null) drinkPanel.setCurrentTableNumber(this.currentTableNumber);
        if (stirFryPanel != null) stirFryPanel.setCurrentTableNumber(this.currentTableNumber);
        if (setMealPanel != null) setMealPanel.setCurrentTableNumber(this.currentTableNumber);
    }

    /**
     * 设置菜品状态缓存
     * @param itemCode 菜品编号（如 "A1"）
     * @param isActive true=售卖中, false=已售罄
     */
    public void setMenuItemStatus(String itemCode, boolean isActive) {
        menuItemStatusCache.put(itemCode.toUpperCase(), isActive);
    }

    /**
     * 检查菜品是否可用
     * @param itemCode 菜品编号
     * @return true=可用, false=已售罄
     */
    public boolean isMenuItemAvailable(String itemCode) {
        Boolean status = menuItemStatusCache.get(itemCode.toUpperCase());
        if (status != null) {
            return status;
        }

        // 缓存中没有，查询数据库
        com.restaurant.entity.MenuItem item = getMenuItemById(itemCode);
        boolean available = (item != null && item.isActive());
        menuItemStatusCache.put(itemCode.toUpperCase(), available);
        return available;
    }
    /**
     * 添加/减少临时订单（支持负数实现取消）
     * @param tableNumber 餐桌号
     * @param itemId 菜品编号
     * @param quantity 正数=增加，负数=减少（取消）
     * @return 操作是否成功
     */
    public boolean addTemporaryOrder(String tableNumber, String itemId, int quantity) {
        // 1. 基础验证
        if (tableNumber == null || tableNumber.trim().isEmpty() ||
                "未选择".equals(tableNumber.trim()) ||
                itemId == null || itemId.isEmpty()) {
            return false;
        }

        String normalizedTableNumber = tableNumber.trim();
        String normalizedItemId = itemId.trim().toUpperCase();

        // 2. 获取该餐桌的临时订单 Map（不存在则创建）
        Map<String, Integer> tableOrders = temporaryOrders.get(normalizedTableNumber);
        if (tableOrders == null) {
            if (quantity <= 0) {
                // 尝试减少不存在的订单 → 无效操作
                return false;
            }
            tableOrders = new HashMap<>();
            temporaryOrders.put(normalizedTableNumber, tableOrders);
        }

        // 3. 计算新数量
        int currentQty = tableOrders.getOrDefault(normalizedItemId, 0);
        int newQty = currentQty + quantity;

        // 4. 处理归零/负数情况（自动清理）
        if (newQty <= 0) {
            tableOrders.remove(normalizedItemId);

            // 如果该餐桌订单变为空，移除整个餐桌记录
            if (tableOrders.isEmpty()) {
                temporaryOrders.remove(normalizedTableNumber);
            }

            System.out.println("临时订单清理 - 餐桌" + normalizedTableNumber + ": " +
                    normalizedItemId + " (原数量: " + currentQty + ", 取消: " + (-quantity) + ")");
        } else {
            // 正常更新数量
            tableOrders.put(normalizedItemId, newQty);
            System.out.println("临时订单更新 - 餐桌" + normalizedTableNumber + ": " +
                    normalizedItemId + " × " + quantity + " (新数量: " + newQty + ")");
        }

        return true;
    }

    /**
     * 获取指定餐桌的临时订单
     */
    public Map<String, Integer> getTemporaryOrderForTable(String tableNumber) {
        if (tableNumber == null || tableNumber.isEmpty()) {
            return Collections.emptyMap();
        }
        return temporaryOrders.getOrDefault(tableNumber, Collections.emptyMap());
    }


    /**
     * 刷新 HomePanel 的临时订单显示
     */
    public void refreshHomeTemporaryOrder() {
        if (homePanel != null) {
            homePanel.refreshTemporaryOrderDisplay();
        }
    }

    /**
     * 根据菜品编号获取完整菜品对象（含名称和价格）
     */
    public com.restaurant.entity.MenuItem getMenuItemById(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        try {
            com.restaurant.dao.MenuItemDAO dao = new com.restaurant.dao.impl.MenuItemDAOImpl();
            return dao.findById(itemId.trim().toUpperCase());
        } catch (Exception e) {
            System.err.println("查询菜品失败: " + e.getMessage());
            return null;
        }
    }
    /**
     * 清除指定餐桌的临时订单（新增方法）
     */
    public void clearTemporaryOrder(String tableNumber) {
        if (tableNumber != null && !tableNumber.trim().isEmpty()) {
            temporaryOrders.remove(tableNumber.trim());
        }
    }

    public void refreshAllPanels() {
        refreshHomeTemporaryOrder(); // 刷新HomePanel临时订单
       // refreshMenuPanels(); // 刷新所有MenuPanel
    }

    /**
     * 从数据库加载指定餐桌的正式订单明细（已提交但未结账）
     * @param tableNumber 餐桌显示ID（如 "7" 或 "7a"）
     * @return 订单项列表
     */
    public List<OrderItem> loadFormalOrderItems(String tableNumber) {
        if (tableNumber == null || tableNumber.trim().isEmpty() || "未选择".equals(tableNumber)) {
            return Collections.emptyList();
        }

        List<OrderItem> items = new ArrayList<>();
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            // ✅ 修正：将 mi.item_name 改为 mi.name AS item_name
            String sql = """
        SELECT oi.order_item_id, oi.order_id, oi.item_id, oi.quantity,
               oi.served_quantity, oi.status, oi.price_at_order,
               mi.item_code, mi.name AS item_name
        FROM table_orders o
        JOIN order_items oi ON o.order_id = oi.order_id
        JOIN menu_items mi ON oi.item_id = mi.item_id
        JOIN restaurant_tables t ON o.table_id = t.table_id
        WHERE t.display_id = ? AND o.status = 'ORDERED'
        ORDER BY oi.order_item_id ASC  -- ✅ 按订单项ID排序（反映下单顺序）
        """;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tableNumber.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        OrderItem item = new OrderItem(
                                rs.getInt("order_id"),
                                rs.getInt("item_id"),
                                rs.getString("item_code"),
                                rs.getString("item_name"),  // ✅ 使用 AS 重命名后的列名
                                rs.getInt("quantity"),
                                rs.getDouble("price_at_order")
                        );
                        item.setOrderItemId(rs.getInt("order_item_id"));
                        item.setServedQuantity(rs.getInt("served_quantity"));
                        item.setStatus(rs.getString("status"));
                        items.add(item);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("加载正式订单失败: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "加载订单数据失败: " + e.getMessage(),
                    "数据库错误",
                    JOptionPane.ERROR_MESSAGE);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { /* ignore */ }
            }
        }
        return items;
    }



    public String generateFormalOrderHtml(String tableNumber, boolean includeTotal) {
        List<OrderItem> items = loadFormalOrderItems(tableNumber);

        if (items.isEmpty()) {
            return "<html><body style='font-family: Microsoft YaHei; padding:10px; color:#999; text-align:center;'>" +
                    "<p>📭 暂无正式订单</p></body></html>";
        }

        // 按状态分组
        Map<String, List<OrderItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(OrderItem::getStatus));

        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Microsoft YaHei; padding:10px;'>");

        html.append("<table border='1' cellpadding='6' cellspacing='0' ")
                .append("style='width:100%; table-layout:fixed; border-collapse:collapse;'>");

        // 表头
        html.append("<tr style='background-color:#f5f5f5;'>")
                .append("<th style='width:50px; text-align:center;'>序号</th>")
                .append("<th style='width:100px; text-align:center;'>状态</th>")
                .append("<th style='width:80px; text-align:left;'>编号</th>")
                .append("<th style='width:200px; text-align:left;'>菜品</th>")
                .append("<th style='width:90px; text-align:center;'>数量（已上/总数）</th>") // ← 优化表头文字
                .append("<th style='width:90px; text-align:right;'>单价</th>")
                .append("<th style='width:100px; text-align:right;'>小计</th>")
                .append("</tr>");

        double totalAmount = 0.0;
        int itemNumber = 1;

        // 固定显示顺序
        for (String status : Arrays.asList("UNSERVED", "PARTIALLY_SERVED", "SERVED")) {
            List<OrderItem> group = grouped.get(status);
            if (group == null || group.isEmpty()) continue;

            String statusText = switch (status) {
                case "UNSERVED" -> "🔴 未上桌";
                case "PARTIALLY_SERVED" -> "🟠 部分上桌";
                case "SERVED" -> "🟢 已上桌";
                default -> status;
            };

            String statusColor = switch (status) {
                case "UNSERVED" -> "#ff6b6b";
                case "PARTIALLY_SERVED" -> "#ffa500";
                case "SERVED" -> "#4caf50";
                default -> "#2196f3";
            };

            for (OrderItem item : group) {
                double subtotal = item.getQuantity() * item.getPriceAtOrder();
                totalAmount += subtotal;

                html.append("<tr>");

                // 序号
                html.append(String.format(
                        "<td style='text-align:center; font-family:monospace;'>%d</td>",
                        itemNumber++
                ));

                // 状态（每一行都输出，绝不 rowspan）
                html.append(String.format(
                        "<td style='background-color:%s; color:white; font-weight:bold; text-align:center;'>%s</td>",
                        statusColor, statusText
                ));

                // 编号 / 菜名
                html.append(String.format(
                        "<td style='white-space:nowrap;'>%s</td>" +
                                "<td style='white-space:nowrap;'>%s</td>",
                        item.getItemCode(),
                        item.getItemName()
                ));

                // ===== 核心修复：数量列显示进度 "已上/总数" =====
                String quantityProgress = String.format("%d/%d",
                        item.getServedQuantity(),
                        item.getQuantity());

                // 部分上桌时高亮背景（浅橙色）+ 加粗
                String quantityStyle = "PARTIALLY_SERVED".equals(item.getStatus())
                        ? "background-color:#fff3e0; font-weight:bold;"
                        : "";

                html.append(String.format(
                        "<td style='text-align:center; font-family:monospace; %s'>%s</td>",
                        quantityStyle,
                        quantityProgress
                ));
                // ============================================

                // 单价 / 小计
                html.append(String.format(
                        "<td style='text-align:right; font-family:monospace;'>%.2f</td>" +
                                "<td style='text-align:right; font-weight:bold; color:#d32f2f; font-family:monospace;'>%.2f</td>",
                        item.getPriceAtOrder(),
                        subtotal
                ));

                html.append("</tr>");
            }
        }

        html.append("</table>");

        // 总计
        if (includeTotal && totalAmount > 0) {
            html.append(String.format(
                    "<div style='margin-top:15px; padding:12px; background-color:#e8f5e9; " +
                            "text-align:right; font-size:18px; font-weight:bold; font-family:monospace;'>" +
                            "订单总计：<span style='color:#c62828;'>%.2f 元</span></div>",
                    totalAmount
            ));
        }

        html.append("</body></html>");
        return html.toString();
    }
    /**
     * 物理删除菜品
     * @param itemCode 菜品编号
     * @return 删除成功返回 true
     */
    public boolean deleteMenuItemPhysically(String itemCode) {
        // 1. 基础验证
        if (itemCode == null || itemCode.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "菜品编号不能为空", "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        itemCode = itemCode.trim().toUpperCase();

        // 2. 检查临时订单
        for (Map<String, Integer> order : temporaryOrders.values()) {
            if (order != null && order.containsKey(itemCode)) {
                JOptionPane.showMessageDialog(this,
                        "该菜品在临时订单中，无法删除！", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }

        try {
            MenuItemDAO dao = new MenuItemDAOImpl();

            // 3. 检查历史订单引用（关键：存在则拒绝，不删除）
            if (dao.existsInOrderItems(itemCode)) {
                // ✅ 静默拒绝：不抛异常，仅提示后返回 false
                JOptionPane.showMessageDialog(this,
                        "该菜品存在于历史订单中，禁止物理删除（保护数据完整性）",
                        "删除失败", JOptionPane.ERROR_MESSAGE);
                return false; // 拒绝删除，不抛 SQLException
            }

            // 4. 执行纯物理删除（仅操作 menu_items）
            boolean deleted = dao.deletePhysically(itemCode);

            if (deleted) {
                menuItemStatusCache.remove(itemCode);
                return true;
            } else {
                // 静默失败：可能是外键约束阻止（返回0），不暴露 SQLException
                JOptionPane.showMessageDialog(this, "删除失败：菜品不存在或被保护", "错误", JOptionPane.ERROR_MESSAGE);
                return false;
            }

        } catch (SQLException e) {
            //  完全禁止 SQLException 详情暴露
            JOptionPane.showMessageDialog(this, "数据库操作失败", "错误", JOptionPane.ERROR_MESSAGE);
            return false; // 静默失败
        }
    }

    public boolean updateMenuItemPrice(String itemCode, double newPrice) {
        itemCode = itemCode.trim().toUpperCase();

        // === 业务安全验证（Controller层职责）===
        // 1. 检查临时订单（内存中，无需DAO）
        for (Map<String, Integer> order : temporaryOrders.values()) {
            if (order != null && order.containsKey(itemCode)) {
                JOptionPane.showMessageDialog(this,
                        " 无法修改价格：菜品 " + itemCode + " 在临时订单中",
                        "操作受限", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }

        // 2. 检查历史订单（调用DAO方法，不写SQL！）
        try {
            MenuItemDAO dao = new MenuItemDAOImpl();
            //  正确用法：调用DAO的existsInOrderItems进行数据查询
            if (dao.existsInOrderItems(itemCode)) {
                JOptionPane.showMessageDialog(this,
                        " 无法修改价格：菜品 " + itemCode + " 已存在于历史订单中",
                        "操作受限", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "数据库检查失败: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // === 执行数据持久化（调用DAO）===
        try {
            MenuItemDAO dao = new MenuItemDAOImpl();
            boolean updated = dao.updatePrice(itemCode, newPrice); //  DAO方法

            if (updated) {
                // 清除缓存确保数据一致性
                menuItemStatusCache.remove(itemCode.toUpperCase());
                return true;
            }
            return false;
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "数据库更新失败: " + e.getMessage(),
                    "数据库错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

}