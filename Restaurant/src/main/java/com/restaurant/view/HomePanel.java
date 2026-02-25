package com.restaurant.view;

import com.restaurant.controller.RestaurantController;
import com.restaurant.entity.MenuItem;
import com.restaurant.entity.OrderItem;
import com.restaurant.entity.Tables;
import com.restaurant.model.RestaurantModel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
/**
 * com.restaurant.view.HomePanel - 订单系统主页面板
 * 采用三层 BorderLayout 嵌套实现
 */
public class HomePanel extends JPanel {
    // ===== 标签组件（需在多个方法访问，保留为字段）=====
    private final JLabel tableNumberLabel = new JLabel("餐桌号：");
    private final JLabel totalPriceLabel = new JLabel("总价格：");
    private final JLabel statusLabel = new JLabel("订单情况：");

    // ===== 滚动区域组件（需在多个方法访问，保留为字段）=====
    private final JEditorPane tempOrderEditor = new JEditorPane();
    private final JEditorPane orderedItemsEditor = new JEditorPane();
    private final JScrollPane tempScrollPane = new JScrollPane(tempOrderEditor);
    private final JScrollPane ordScrollPane = new JScrollPane(orderedItemsEditor);
    private JTextField tableNumberField;  // 提升为类字段，替代原局部变量
    private String currentTableNumber = "";

    private final OrderSystemGUI frame;
    private final RestaurantModel model;
    private final RestaurantController controller;

    public HomePanel(OrderSystemGUI frame, RestaurantModel model, RestaurantController controller) {
        this.frame = frame;
        this.model = model;
        this.controller = controller;
        initializeUI();
    }


    // 修改 setCurrentTableNumber() 方法，在设置餐桌号后自动加载正式订单
    public void setCurrentTableNumber(String tableNumber) {
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            this.currentTableNumber = "未选择";
            tableNumberLabel.setText("餐桌号：未选择");
        } else {
            this.currentTableNumber = tableNumber.trim();
            tableNumberLabel.setText("餐桌号：" + this.currentTableNumber);

            if (tableNumberField != null && !this.currentTableNumber.equals("未选择")) {
                tableNumberField.setText(this.currentTableNumber);
            }
        }

        //  关键：自动刷新临时订单 + 正式订单
        refreshTemporaryOrderDisplay();
        refreshFormalOrderDisplay(); // ← 新增
    }

    private void initializeUI() {
        // ===== STEP 1: 主布局 - BorderLayout 三层结构 =====
        setLayout(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // ===== STEP 2: NORTH 区域 - 2x2 按钮网格 =====
        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 创建四个彩色按钮（添加事件监听器实现跳转）
        JButton foodBtn = createSquareButton("特色食物", new Color(255, 204, 204));
        JButton drinkBtn = createSquareButton("饮料", new Color(204, 229, 255));
        JButton stirFryBtn = createSquareButton("小炒", new Color(255, 230, 180));
        JButton setMealBtn = createSquareButton("套餐", new Color(204, 255, 204));

        // === 关键：添加事件监听器实现面板切换 ===
        foodBtn.addActionListener(e -> frame.showPanel("Food"));
        drinkBtn.addActionListener(e -> frame.showPanel("Drink"));
        stirFryBtn.addActionListener(e -> frame.showPanel("StirFry"));
        setMealBtn.addActionListener(e -> frame.showPanel("SetMeal"));

        // 按顺序添加到网格（自动排列为2x2）
        buttonPanel.add(foodBtn);
        buttonPanel.add(drinkBtn);
        buttonPanel.add(stirFryBtn);
        buttonPanel.add(setMealBtn);

        add(buttonPanel, BorderLayout.NORTH);

        // ===== STEP 3: CENTER 区域 - 嵌套 BorderLayout =====
        JPanel middlePanel = new JPanel(new BorderLayout(0, 10));
        middlePanel.setBorder(new EmptyBorder(10, 0, 10, 0));

        // -- 3.1 标签栏 (FlowLayout 左对齐) --
        JPanel labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        // 设置标签字体（普通粗细，16号字）
        Font labelFont = new Font("Microsoft YaHei", Font.PLAIN, 16);
        tableNumberLabel.setFont(labelFont);
        totalPriceLabel.setFont(labelFont);
        statusLabel.setFont(labelFont);

        labelPanel.add(tableNumberLabel);
        labelPanel.add(totalPriceLabel);
        labelPanel.add(statusLabel);
        middlePanel.add(labelPanel, BorderLayout.NORTH);

        // -- 3.2 左右分栏 (GridLayout 1x2 水平分割) --
        JPanel contentPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        // 左侧面板：临时订单
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(new JLabel("  临时订单", SwingConstants.LEFT), BorderLayout.NORTH);
        leftPanel.add(tempScrollPane, BorderLayout.CENTER);

        // 右侧面板：已下单食物
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(new JLabel("  已下单的食物", SwingConstants.LEFT), BorderLayout.NORTH);
        rightPanel.add(ordScrollPane, BorderLayout.CENTER);

        contentPanel.add(leftPanel);
        contentPanel.add(rightPanel);
        middlePanel.add(contentPanel, BorderLayout.CENTER);

        add(middlePanel, BorderLayout.CENTER);

        // ===== STEP 4: SOUTH 区域 - 操作按钮 (FlowLayout 居中) =====
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        // === 关键修改：组件声明移至 initializeUI() 内部 ===
        tableNumberField = new JTextField(10);
        JButton confirmTableBtn = new JButton("确认餐桌");
        JButton confirmOrderBtn = new JButton("确认下单");
        JButton confirmServedBtn = new JButton("确认上桌");
        JButton cancelOrderItemBtn = new JButton("撤销菜品");
        JButton cancelReorderBtn = new JButton("取消重新点餐");

        bottomPanel.add(new JLabel("餐桌号："));
        bottomPanel.add(tableNumberField);
        bottomPanel.add(confirmTableBtn);
        bottomPanel.add(confirmOrderBtn);
        bottomPanel.add(confirmServedBtn);
        bottomPanel.add(cancelOrderItemBtn);
        bottomPanel.add(cancelReorderBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        confirmTableBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String tableNumStr = tableNumberField.getText().trim();
                handleConfirmTable(tableNumStr);//将清理后的餐桌编号字符串 tableNumStr 传递给业务方法 handleConfirmTable
            }
        });

        confirmOrderBtn.addActionListener(this::handleConfirmOrder);  // 绑定确认下单事件
        confirmServedBtn.addActionListener(e -> showConfirmServedDialog());
        cancelOrderItemBtn.addActionListener(e -> showCancelOrderItemDialog());
        cancelReorderBtn.addActionListener(e -> showCancelReorderDialog());

        tempOrderEditor.setContentType("text/html");
        tempOrderEditor.setEditable(false);
        orderedItemsEditor.setContentType("text/html");
        orderedItemsEditor.setEditable(false);
    }

    // 创建统一尺寸的圆角彩色按钮
    private JButton createSquareButton(String text, Color bgColor) {
        JButton btn = new JButton(text);
        btn.setBackground(bgColor);
        btn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));  // 普通粗细
        btn.setPreferredSize(new Dimension(150, 150));
        btn.setFocusPainted(false);
        return btn;
    }

    /**
     * 处理餐桌号确认逻辑（完整业务验证）
     * @param tableNumStr 用户输入的餐桌号（如 "7" 或 "7a"）
     */
    private void handleConfirmTable(String tableNumStr) {
        if (!tableNumStr.isEmpty()) {
            try {
                // 1. 解析餐桌ID和后缀
                int tableId;
                String suffix = "";
                if (tableNumStr.matches("\\d+[a-zA-Z]")) {
                    tableId = Integer.parseInt(tableNumStr.replaceAll("[^0-9]", ""));
                    suffix = tableNumStr.replaceAll("[^a-zA-Z]", "");
                } else if (tableNumStr.matches("\\d+")) {
                    tableId = Integer.parseInt(tableNumStr);
                } else {
                    showErrorDialog("餐桌编号格式无效（例如7或7a）", "输入错误");
                    return;
                }

                // 2. 从模型中查找餐桌（直接使用完整 displayId）
                Tables targetTable = model.getTableById(tableNumStr);
                if (targetTable == null) {
                    showErrorDialog("未找到餐桌 #" + tableNumStr, "错误");
                    return;
                }

                String displayId = targetTable.getDisplayId();
                Tables.TableStatus status = targetTable.getStatus();

                // 3. 检查餐桌状态
                if (status != Tables.TableStatus.OCCUPIED) {
                    String statusText = "";
                    switch (status) {
                        case VACANT:
                            statusText = "空闲";
                            break;
                        case SETTING_UP:
                            statusText = "准备中";
                            break;
                        case SPLITTING:
                            statusText = "拆分中";
                            break;
                        default:
                            statusText = "未知状态";
                    }

                    JOptionPane.showMessageDialog(frame,
                            "餐桌 " + displayId + " 当前处于【" + statusText + "】状态，不能进行点餐操作。",
                            "无效操作",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // 4. 检查合并餐桌状态
                if (!checkAndWarnIfNotMainOrderTable(displayId)) {
                    return; // 不是主餐桌，操作被阻止
                }

                // 5. 通过所有验证，设置当前餐桌
                frame.setCurrentTableNumber(displayId);
                tableNumberField.setText("");
                frame.refreshAllPanels();
                JOptionPane.showMessageDialog(frame, "已成功选择餐桌：" + displayId, "成功", JOptionPane.INFORMATION_MESSAGE);

            } catch (NumberFormatException ex) {
                showErrorDialog("请输入有效的餐桌号（整数）", "输入错误");
            } catch (Exception e) {
                showErrorDialog("系统错误: " + e.getMessage(), "错误");
                e.printStackTrace();
            }
        } else {
            showErrorDialog("请输入餐桌号", "输入错误");
        }
    }

    /**
     * 显示错误对话框
     * @param message 错误信息
     * @param title 对话框标题
     */
    private void showErrorDialog(String message, String title) {
        JOptionPane.showMessageDialog(frame, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * 检查餐桌是否为合并餐桌的主桌，如果不是则提示警告
     *
     * @param displayId 餐桌显示ID
     * @return 如果允许操作返回true，否则返回false
     */
    private boolean checkAndWarnIfNotMainOrderTable(String displayId) {
        Tables targetTable = model.getTableById(displayId);
        if (targetTable == null) {
            return false;
        }

        // 如果不是合并餐桌，直接允许操作
        if (targetTable.getTableType() != Tables.TableType.MERGED) {
            return true;
        }

        // 获取合并餐桌伙伴
        String partnerDisplayId = targetTable.getMergedWith();
        if (partnerDisplayId == null || partnerDisplayId.isEmpty()) {
            return true; // 没有合并伙伴，按普通餐桌处理
        }

        Tables partnerTable = model.getTableById(partnerDisplayId);
        if (partnerTable == null) {
            return true; // 合并伙伴不存在，按普通餐桌处理
        }

        // 确定主餐桌（ID较小的餐桌）
        int currentId = Integer.parseInt(displayId.replaceAll("[^0-9]", ""));
        int partnerId = Integer.parseInt(partnerDisplayId.replaceAll("[^0-9]", ""));

        // 如果当前餐桌不是编号最小的 → 提示并阻止操作
        if (currentId > partnerId) {
            String warningMessage = "该合并桌只能通过编号较小的餐桌（" + partnerId + "）进行操作。\n请切换至餐桌 " + partnerId + " 进行相关操作。";
            JOptionPane.showMessageDialog(frame,
                    warningMessage,
                    "操作受限",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true; // 允许操作
    }



    public void refreshTemporaryOrderDisplay() {
        Map<String, Integer> tempOrder = frame.getTemporaryOrderForTable(currentTableNumber);
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: Microsoft YaHei; padding: 10px;'>");

        if (tempOrder == null || tempOrder.isEmpty()) {
            html.append("<p style='color: #999; text-align: center;'>（暂无临时订单）</p>");
        } else {
            html.append("<table border='1' cellpadding='5' cellspacing='0' style='width: 100%; border-collapse: collapse;'>");
            html.append("<tr style='background-color: #f0f0f0;'>");
            html.append("<th style='padding: 8px; text-align: left;'>菜品编号</th>");
            html.append("<th style='padding: 8px; text-align: left;'>菜品名称</th>");
            html.append("<th style='padding: 8px; text-align: center;'>数量</th>");
            html.append("<th style='padding: 8px; text-align: right;'>单价(元)</th>");
            html.append("<th style='padding: 8px; text-align: right;'>小计(元)</th>");
            html.append("</tr>");

            // 使用数组包装解决 Lambda 中修改变量的问题
            double[] totalAmount = {0.0};

            tempOrder.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        String itemId = entry.getKey();
                        int qty = entry.getValue();

                        // 通过 frame 查询完整菜品信息（名称+价格）
                        com.restaurant.entity.MenuItem item = frame.getMenuItemById(itemId);
                        String itemName = "（未知）";
                        double price = 0.0;

                        if (item != null) {
                            itemName = item.getName();
                            price = item.getPrice();
                        }

                        double subtotal = price * qty;
                        totalAmount[0] += subtotal;  // 正确：修改数组元素

                        html.append("<tr style='border-top: 1px solid #ddd;'>");
                        html.append("<td style='padding: 8px;'>").append(itemId).append("</td>");
                        html.append("<td style='padding: 8px;'>").append(itemName).append("</td>");
                        html.append("<td style='padding: 8px; text-align: center;'>").append(qty).append("</td>");
                        html.append("<td style='padding: 8px; text-align: right;'>").append(String.format("%.2f", price)).append("</td>");
                        html.append("<td style='padding: 8px; text-align: right; font-weight: bold; color: #d32f2f;'>")
                                .append(String.format("%.2f", subtotal)).append("</td>");
                        html.append("</tr>");
                    });

            html.append("</table>");

            // 显示总金额
            html.append("<div style='margin-top: 15px; padding: 10px; background-color: #e8f5e9; border-radius: 4px; text-align: right;'>");
            html.append("<span style='font-size: 16px; font-weight: bold;'>订单总金额：</span>");
            html.append("<span style='font-size: 20px; color: #c62828; font-weight: bold;'>")
                    .append(String.format("%.2f", totalAmount[0])).append(" 元</span>");
            html.append("</div>");
        }

        html.append("</body></html>");
        tempOrderEditor.setText(html.toString());
    }



    /**
     * 订单确认UI交互入口（View层）
     *
     * 职责边界：
     * - 纯前端验证：临时订单非空、餐桌已选择
     * - 用户二次确认：防止误操作
     * - 数据转换：Map<String,Integer> → List<OrderItem>
     * - 不含业务逻辑：仅组装数据并调用Controller
     *
     * 关键约束：
     * 1. 验证顺序：先UI验证 → 再用户确认 → 最后调用Controller
     * 2. 回调设计：所有UI刷新（清空临时订单/刷新面板）放在onSuccess中
     *    确保仅在数据库事务提交成功后执行，避免状态不一致
     * 3. 防御性编程：菜品不存在时立即终止，不传递无效数据到Controller
     *
     * @param e 按钮点击事件（由Swing事件分发线程触发）
     */
    private void handleConfirmOrder(ActionEvent e) {
        // 验证临时订单
        Map<String, Integer> tempOrder = frame.getTemporaryOrderForTable(currentTableNumber);
        if (tempOrder == null || tempOrder.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "没有临时订单可以确认。");
            return;
        }

        // 验证餐桌
        if (currentTableNumber == null || "未选择".equals(currentTableNumber)) {
            JOptionPane.showMessageDialog(frame, "请先选择餐桌！");
            return;
        }

        Tables currentTable = null;
        try {
            currentTable = model.getTableById(currentTableNumber);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "未找到当前餐桌！");
            return;
        }

        // 检查已结账状态（从内存获取）
        boolean isReorderAfterCheckout = false;
        if (currentTable.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT) {
            int confirm = JOptionPane.showConfirmDialog(
                    frame,
                    "餐桌 " + currentTableNumber + " 已结账，是否要再次点单？",
                    "确认再次点单",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            isReorderAfterCheckout = true;
        }

        // 用户最终确认
        int confirm = JOptionPane.showConfirmDialog(
                frame,
                "是否确认已完成所有点菜？",
                "确认下单",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        // 构建订单项
        List<OrderItem> orderItems = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : tempOrder.entrySet()) {
            String itemCode = entry.getKey();
            int qty = entry.getValue();

            MenuItem menuItem = frame.getMenuItemById(itemCode);
            if (menuItem == null) {
                JOptionPane.showMessageDialog(frame, "菜品不存在：" + itemCode);
                return;
            }

            orderItems.add(new OrderItem(
                    0,
                    menuItem.getItemId(),
                    itemCode,
                    menuItem.getName(),
                    qty,
                    menuItem.getPrice()
            ));
        }

        // 调用 Controller（传递重单标志）
        controller.handleConfirmOrder(
                currentTableNumber,
                orderItems,
                isReorderAfterCheckout,
                () -> {
                    frame.clearTemporaryOrder(currentTableNumber);
                    refreshTemporaryOrderDisplay();
                    refreshFormalOrderDisplay();
                    JOptionPane.showMessageDialog(frame, "订单已成功提交！");
                }
        );
    }

    public void refreshFormalOrderDisplay() {
        if (currentTableNumber == null || "未选择".equals(currentTableNumber)) {
            orderedItemsEditor.setText(
                    "<html><body style='font-family: Microsoft YaHei; padding:20px; color:#999; text-align:center;'>" +
                            "<p>请选择餐桌查看正式订单</p></body></html>"
            );
            totalPriceLabel.setText("总价格：0.00元");
            statusLabel.setText("订单情况："); // 重置狀態
            return;
        }

        // 替换硬编码判断：直接读内存状态（零查库）
        statusLabel.setText(controller.getOrderStatusDisplay(currentTableNumber));

        // 使用带编号的HTML
        String htmlContent = frame.generateFormalOrderHtml(currentTableNumber, true);
        orderedItemsEditor.setText(htmlContent);
        // 更新总价格标签
        double total = calculateFormalOrderTotal(currentTableNumber);
        totalPriceLabel.setText(String.format("总价格：%.2f元", total));
    }

    /**
     * 计算正式订单总金额
     */
    private double calculateFormalOrderTotal(String tableNumber) {
        List<OrderItem> items = frame.loadFormalOrderItems(tableNumber);
        return items.stream().mapToDouble(i -> i.getQuantity() * i.getPriceAtOrder()).sum();
    }

    /**
     * 部分標記菜品為已上桌（View 層 - 僅 UI 驗證 + 事件轉發）
     * @param parentComponent 父組件（用於彈窗）
     * @param tableNumber 餐桌編號（String，如 "7"）
     * @param itemCode 菜品編號（String，如 "A1"）← 關鍵：接收 String 類型
     * @param quantity 數量
     * @return 操作是否成功
     */
    private boolean markItemsAsServed(Component parentComponent, String tableNumber, String itemCode, int quantity) {
        // 1. UI 驗證：餐桌號
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parentComponent, "餐桌号不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // 2. UI 驗證：合併餐桌規則（只讀操作，可保留在 View 層）
        if (!checkAndWarnIfNotMainOrderTable(tableNumber)) {
            return false;
        }

        // 3. ✅ 關鍵：從內存緩存獲取 item_id（String → int 轉換在 View 層完成）
        com.restaurant.entity.MenuItem menuItem = frame.getMenuItemById(itemCode.trim().toUpperCase());
        if (menuItem == null) {
            JOptionPane.showMessageDialog(parentComponent, "菜品 " + itemCode + " 不存在或已停售", "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        int itemId = menuItem.getItemId(); // View 層完成 String → int 轉換

        // 4. ✅ 僅轉發事件，不碰數據庫
        try {
            controller.handleMarkItemsAsServed(tableNumber, itemId, quantity); // ← 傳遞 int 類型

            // 成功後刷新 UI
            SwingUtilities.invokeLater(() -> {
                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
                if (controller != null) {
                    controller.refreshOrderStatusOnly();
                }
            });
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentComponent,
                    "標記上桌失敗: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /**
     * 一鍵標記所有菜品為已上桌（View 層 - 僅 UI 驗證 + 事件轉發）
     * @param parentComponent 父組件
     * @param tableNumber 餐桌編號（String）
     * @return 操作是否成功
     */
    private boolean markAllItemsAsServed(Component parentComponent, String tableNumber) {
        // 1. UI 驗證：餐桌號
        if (tableNumber == null || tableNumber.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parentComponent, "餐桌号不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // 2. UI 驗證：合併餐桌規則
        if (!checkAndWarnIfNotMainOrderTable(tableNumber)) {
            return false;
        }

        // 3. ✅ 僅轉發事件，不碰數據庫
        try {
            controller.handleMarkAllItemsAsServed(tableNumber); // ← 單行調用

            // 成功後刷新 UI
            SwingUtilities.invokeLater(() -> {
                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
                if (controller != null) {
                    controller.refreshOrderStatusOnly();
                }
                JOptionPane.showMessageDialog(parentComponent,
                        "已將餐桌 " + tableNumber + " 的所有菜品標記為已上桌！",
                        "成功", JOptionPane.INFORMATION_MESSAGE);
            });
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentComponent,
                    "標記全部菜品失敗: " + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    /**
     * 顯示菜品上桌確認對話框
     */
    private void showConfirmServedDialog() {
        // 創建對話框（保持您現有的 UI 代碼不變）
        JFrame dialog = new JFrame("確認上桌");
        dialog.setSize(650, 280);
        dialog.setLocationRelativeTo(null);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 操作選擇面板
        JPanel optionPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        JRadioButton manualOption = new JRadioButton("手動指定菜品", true);
        JRadioButton allOption = new JRadioButton("一鍵全部確認");
        ButtonGroup operationGroup = new ButtonGroup();
        operationGroup.add(manualOption);
        operationGroup.add(allOption);
        optionPanel.add(manualOption);
        optionPanel.add(allOption);
        mainPanel.add(optionPanel, BorderLayout.NORTH);

        // 輸入面板
        JPanel inputPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        JLabel tableNumberLabel = new JLabel("餐桌号:");
        JTextField tableNumberField = new JTextField();
        JLabel itemIdLabel = new JLabel("菜品编号（用逗号分隔多个菜品ID）:");
        JTextField itemIdField = new JTextField();
        JLabel quantityLabel = new JLabel("<html>數量（建議將數量 &gt;1 的菜品編號寫在前面）<br>用逗號“,”分隔；未填數量的菜品默認為 1：</html>");
        JTextField quantityField = new JTextField("1");

        inputPanel.add(tableNumberLabel);
        inputPanel.add(tableNumberField);
        inputPanel.add(itemIdLabel);
        inputPanel.add(itemIdField);
        inputPanel.add(quantityLabel);
        inputPanel.add(quantityField);

        if (!"未选择".equals(currentTableNumber)) {
            tableNumberField.setText(currentTableNumber);
        }
        mainPanel.add(inputPanel, BorderLayout.CENTER);

        // 按鈕面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton confirmBtn = new JButton("確認");
        JButton cancelBtn = new JButton("取消");
        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.add(mainPanel, BorderLayout.CENTER);

        // 切換可見性（保持您現有的邏輯）
        manualOption.addActionListener(evt -> {
            itemIdLabel.setVisible(true);
            itemIdField.setVisible(true);
            quantityLabel.setVisible(true);
            quantityField.setVisible(true);
            dialog.setSize(650, 280);
            dialog.revalidate();
            dialog.repaint();
        });

        allOption.addActionListener(evt -> {
            itemIdLabel.setVisible(false);
            itemIdField.setVisible(false);
            quantityLabel.setVisible(false);
            quantityField.setVisible(false);
            dialog.setSize(650, 280);
            dialog.revalidate();
            dialog.repaint();
        });

        // 取消按鈕
        cancelBtn.addActionListener(evt -> dialog.dispose());

        // 確認按鈕（✅ 關鍵：調用重構後的方法）
        confirmBtn.addActionListener(evt -> {
            String tableNumber = tableNumberField.getText().trim();
            if (tableNumber.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "餐桌号不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Tables targetTable = model.getTableById(tableNumber);
            if (targetTable == null) {
                JOptionPane.showMessageDialog(dialog, "未找到餐桌：" + tableNumber, "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!checkAndWarnIfNotMainOrderTable(tableNumber)) {
                dialog.dispose();
                return;
            }

            if (manualOption.isSelected()) {
                String itemId = itemIdField.getText().trim();
                String quantityStr = quantityField.getText().trim();

                if (itemId.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "菜品编号不能为空！", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String[] itemIds = itemId.split(",");
                String[] quantityStrs = quantityStr.split(",");
                int[] quantities = new int[itemIds.length];

                boolean allValid = true;
                for (int i = 0; i < itemIds.length; i++) {
                    try {
                        int quantity = i < quantityStrs.length ?
                                Integer.parseInt(quantityStrs[i].trim()) : 1;
                        if (quantity <= 0) {
                            JOptionPane.showMessageDialog(dialog, "数量必须大于0！", "错误", JOptionPane.ERROR_MESSAGE);
                            allValid = false;
                            break;
                        }
                        quantities[i] = quantity;
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(dialog, "请输入有效的数量（整数）！", "错误", JOptionPane.ERROR_MESSAGE);
                        allValid = false;
                        break;
                    }
                }

                if (!allValid) return;

                boolean anySuccess = false;
                for (int i = 0; i < itemIds.length; i++) {
                    String id = itemIds[i].trim().toUpperCase();
                    if (!id.isEmpty()) {
                        // ✅ 關鍵：調用重構後的方法
                        if (markItemsAsServed(dialog, tableNumber, id, quantities[i])) {
                            anySuccess = true;
                        }
                    }
                }

                if (anySuccess) {
                    refreshTemporaryOrderDisplay();
                    refreshFormalOrderDisplay();
                    if (controller != null) {
                        controller.refreshOrderStatusOnly();
                    }
                    JOptionPane.showMessageDialog(dialog, "部分或全部菜品已成功標記為已上桌。");
                } else {
                    JOptionPane.showMessageDialog(dialog, "未找到匹配的未上桌菜品。", "提示", JOptionPane.INFORMATION_MESSAGE);
                }

            } else if (allOption.isSelected()) {
                // 調用重構後的方法
                if (markAllItemsAsServed(dialog, tableNumber)) {
                    refreshTemporaryOrderDisplay();
                    refreshFormalOrderDisplay();
                    if (controller != null) {
                        controller.refreshOrderStatusOnly();
                    }
                }
            }

            dialog.dispose();
        });

        dialog.setVisible(true);
    }

    private void showCancelOrderItemDialog() {
        // 3. 创建对话框（无条件弹出，不受餐桌号限制）
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "撤销菜品", true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);

        // 4. 表单面板
        JPanel formPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        // ✅ 餐桌号字段可编辑（允许用户手动输入）
        JTextField tableField = new JTextField();
        if (currentTableNumber != null && !"未选择".equals(currentTableNumber)) {
            tableField.setText(currentTableNumber); // 有当前餐桌号时预填
        }
        // 不设置 setEditable(false)，保持可编辑

        JTextField itemCodeField = new JTextField();
        JTextField quantityField = new JTextField("1");

        formPanel.add(new JLabel("餐桌号: *"));
        formPanel.add(tableField);
        formPanel.add(new JLabel("菜品编号: *"));
        formPanel.add(itemCodeField);
        formPanel.add(new JLabel("撤销数量: *"));
        formPanel.add(quantityField);

        // 5. 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton confirmBtn = new JButton("确认撤销");
        JButton cancelBtn = new JButton("取消");

        // 6. 确认事件处理
        confirmBtn.addActionListener(e -> {
            try {
                // 6.1 验证餐桌号（移到此处，对话框内输入后验证）
                String tableNumber = tableField.getText().trim();
                if (tableNumber.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "请输入餐桌号", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (!checkAndWarnIfNotMainOrderTable(tableNumber)) {
                    return; // 主桌验证失败则终止
                }

                // 6.2 验证菜品编号
                String itemCode = itemCodeField.getText().trim().toUpperCase();
                if (itemCode.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "请输入菜品编号", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 6.3 验证数量
                int quantity;
                try {
                    quantity = Integer.parseInt(quantityField.getText().trim());
                    if (quantity <= 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(dialog, "请输入有效的撤销数量（>0）", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 6.4 检查菜品是否存在
                com.restaurant.entity.MenuItem menuItem = frame.getMenuItemById(itemCode);
                if (menuItem == null) {
                    JOptionPane.showMessageDialog(dialog, "菜品 " + itemCode + " 不存在", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                boolean requiresReason = false;
                List<OrderItem> items = frame.loadFormalOrderItems(tableNumber);
                if (items != null) {
                    for (OrderItem item : items) {
                        if (itemCode.equals(item.getItemCode()) && item.getServedQuantity() > 0) {
                            requiresReason = true;
                            break;
                        }
                    }
                }

                String cancellationReason = null;
                if (requiresReason) {
                    cancellationReason = JOptionPane.showInputDialog(
                            dialog,
                            "该菜品已有部分/全部上桌，请输入撤销原因:",
                            "需要撤销原因",
                            JOptionPane.WARNING_MESSAGE
                    );
                    if (cancellationReason == null || cancellationReason.trim().isEmpty()) {
                        JOptionPane.showMessageDialog(
                                dialog,
                                "撤销已上桌菜品必须提供原因",
                                "输入错误",
                                JOptionPane.ERROR_MESSAGE
                        );
                        return;
                    }
                    cancellationReason = cancellationReason.trim();
                }

                // 6.5 调用 Controller 执行撤销
                controller.handleCancelOrderItem(
                        tableNumber,  // 使用对话框内输入的餐桌号
                        itemCode,
                        quantity,
                        cancellationReason
                );

                // 6.6 刷新 UI
                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
                if (controller != null) {
                    controller.refreshOrderStatusOnly();
                }
                JOptionPane.showMessageDialog(dialog, "菜品撤销成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        dialog,
                        "撤销失败: " + ex.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                );
                ex.printStackTrace();
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());
        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }


    private void showCancelReorderDialog() {
        JDialog dialog = new JDialog(frame, "取消重新点餐", true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(450, 180); // 高度从280→180（更窄）
        dialog.setLocationRelativeTo(this);

        // ✅ 关键修复：使用水平布局 + 加宽输入框
        JPanel formPanel = new JPanel(new BorderLayout(10, 0)); // 水平布局，间距10px
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25)); // 减小底部边距

        // 标签左对齐
        JLabel label = new JLabel("餐桌号: *", SwingConstants.LEFT);
        label.setPreferredSize(new Dimension(80, 25)); // 固定标签宽度

        // 输入框加宽（边框更长）
        JTextField tableField = new JTextField(25); // 25列 → 输入框更长
        if (currentTableNumber != null && !"未选择".equals(currentTableNumber)) {
            tableField.setText(currentTableNumber);
        }

        // 组合标签+输入框
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(label, BorderLayout.WEST);
        inputPanel.add(tableField, BorderLayout.CENTER);

        formPanel.add(inputPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton confirmBtn = new JButton("确认恢复");
        JButton cancelBtn = new JButton("取消");

        confirmBtn.addActionListener(ev -> {
            String tableNumber = tableField.getText().trim();
            if (tableNumber.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入餐桌号", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Tables table = model.getTableById(tableNumber);
            if (table == null) {
                JOptionPane.showMessageDialog(dialog, "未找到餐桌 #" + tableNumber, "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (table.getStatus() != Tables.TableStatus.OCCUPIED) {
                JOptionPane.showMessageDialog(dialog,
                        "餐桌 " + tableNumber + " 当前状态为【" + table.getStatus() + "】，不能操作",
                        "无效操作", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (!checkAndWarnIfNotMainOrderTable(tableNumber)) {
                return;
            }

            dialog.dispose();

            try {
                controller.handleCancelReorder(tableNumber);

                refreshTemporaryOrderDisplay();
                refreshFormalOrderDisplay();
                frame.refreshAllPanels();
                controller.refreshOrderStatusOnly();

                JOptionPane.showMessageDialog(
                        frame,
                        "餐桌 " + tableNumber + " 已恢复为已结账状态",
                        "成功",
                        JOptionPane.INFORMATION_MESSAGE
                );

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        frame,
                        ex.getMessage(),
                        "操作失败",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        cancelBtn.addActionListener(ev -> dialog.dispose());
        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
}