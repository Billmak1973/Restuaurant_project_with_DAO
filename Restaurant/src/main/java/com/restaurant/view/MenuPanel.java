package com.restaurant.view;

import com.restaurant.controller.RestaurantController;
import com.restaurant.model.RestaurantModel;
import com.restaurant.service.MenuCategoryService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

public class MenuPanel extends JPanel {
    // 菜单类型常量
    public static final int FOOD = 0;
    public static final int DRINK = 1;
    public static final int STIRFRY = 2;
    public static final int SETMEAL = 3;

    private final int menuType;
    private final OrderSystemGUI frame;

    // UI 组件
    private JLabel tableNumberDisplay;
    private JEditorPane temporaryHtmlDisplay;
    private JEditorPane orderedHtmlDisplay;
    private JEditorPane menuTableDisplay;
    private JScrollPane tempScrollPane;
    private JScrollPane ordScrollPane;
    private JScrollPane menuScrollPane;

    private String currentTableNumber = "未选择";
    private static final Map<String, String> menuCache = new ConcurrentHashMap<>();
    private List<com.restaurant.entity.MenuItem> menuItems = null;

    // ===== 主题颜色定义 =====
    private final Color FOOD_COLOR = new Color(255, 228, 225);  // 温暖的粉色系
    private final Color DRINK_COLOR = new Color(220, 240, 255);  // 清爽的蓝色系
    private final Color STIRFRY_COLOR = new Color(255, 245, 220); // 温暖的黄色系
    private final Color SETMEAL_COLOR = new Color(225, 255, 240); // 清新的绿色系

    // 主题颜色配置
    private Color backgroundColor;
    private Color titleColor;
    private Color borderColor;
    private Color headerBgColor;
    private Color buttonBgColor;
    private Color buttonHoverColor;

    public MenuPanel(OrderSystemGUI frame, RestaurantModel model, RestaurantController controller, int menuType) {
        this.frame = frame;
        this.menuType = menuType;

        // ===== 初始化主题颜色 =====
        setupThemeColors();

        initializeUI();
        loadMenuItems(true);
    }

    /**
     * 设置主题颜色（根据菜单类型）
     */
    private void setupThemeColors() {
        switch (menuType) {
            case FOOD:
                backgroundColor = FOOD_COLOR;
                titleColor = new Color(200, 50, 50);
                borderColor = new Color(220, 150, 150);
                headerBgColor = new Color(255, 200, 200);
                buttonBgColor = new Color(255, 180, 180);
                buttonHoverColor = new Color(255, 150, 150);
                break;
            case DRINK:
                backgroundColor = DRINK_COLOR;
                titleColor = new Color(50, 100, 200);
                borderColor = new Color(150, 200, 255);
                headerBgColor = new Color(200, 230, 255);
                buttonBgColor = new Color(180, 220, 255);
                buttonHoverColor = new Color(150, 200, 255);
                break;
            case STIRFRY:
                backgroundColor = STIRFRY_COLOR;
                titleColor = new Color(150, 100, 0);
                borderColor = new Color(255, 220, 150);
                headerBgColor = new Color(255, 220, 180);
                buttonBgColor = new Color(255, 200, 150);
                buttonHoverColor = new Color(255, 180, 130);
                break;
            case SETMEAL:
                backgroundColor = SETMEAL_COLOR;
                titleColor = new Color(0, 100, 50);
                borderColor = new Color(150, 255, 200);
                headerBgColor = new Color(200, 255, 220);
                buttonBgColor = new Color(180, 255, 220);
                buttonHoverColor = new Color(150, 255, 200);
                break;
        }
    }


    private void initializeUI() {
        // ===== STEP 1: 主布局 - BorderLayout 三层结构 =====
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(backgroundColor); // 设置背景色

        // ===== STEP 2: NORTH 区域 - 餐桌号显示 =====
        JPanel tableNumberPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        tableNumberDisplay = new JLabel("餐桌号：" + currentTableNumber);
        tableNumberDisplay.setFont(new Font("Microsoft YaHei", Font.PLAIN, 16));
        tableNumberPanel.setBackground(backgroundColor); // ✅ 关键：设置背景色为主题色
        tableNumberPanel.setOpaque(true); // 确保背景色生效
        tableNumberPanel.add(tableNumberDisplay);
        add(tableNumberPanel, BorderLayout.NORTH);

        // ===== STEP 3: CENTER 区域 - 内容面板（订单+菜单）=====
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(backgroundColor); // 设置内容面板背景色

        // 3.1 订单区域（占40%高度）→ 关键：设置首选高度250px
        JPanel orderPanel = new JPanel(new BorderLayout());
        orderPanel.setPreferredSize(new Dimension(0, 250));
        orderPanel.setBackground(backgroundColor); // 订单区域背景色

        // 临时订单区域
        temporaryHtmlDisplay = new JEditorPane("text/html",
                "<html><body style='font-family: Microsoft YaHei; padding:10px;'>" +
                        "<p style='color:gray;'>暂无临时订单</p>" +
                        "</body></html>");
        temporaryHtmlDisplay.setEditable(false);
        tempScrollPane = new JScrollPane(temporaryHtmlDisplay);
        tempScrollPane.setBorder(BorderFactory.createTitledBorder("  临时订单  "));
        tempScrollPane.setBackground(backgroundColor);

        // 已下单食物区域
        orderedHtmlDisplay = new JEditorPane("text/html",
                "<html><body style='font-family: Microsoft YaHei; padding:10px;'>" +
                        "<p style='color:gray;'>暂无已下单食物</p>" +
                        "</body></html>");
        orderedHtmlDisplay.setEditable(false);
        ordScrollPane = new JScrollPane(orderedHtmlDisplay);
        ordScrollPane.setBorder(BorderFactory.createTitledBorder("  已下单的食物  "));
        ordScrollPane.setBackground(backgroundColor);

        // 左右面板
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(backgroundColor);
        leftPanel.add(tempScrollPane, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(backgroundColor);
        rightPanel.add(ordScrollPane, BorderLayout.CENTER);

        //  修复1：设置相等的最小/首选尺寸（防止挤压）
        leftPanel.setMinimumSize(new Dimension(300, 0));
        rightPanel.setMinimumSize(new Dimension(300, 0));
        leftPanel.setPreferredSize(new Dimension(300, 0));
        rightPanel.setPreferredSize(new Dimension(300, 0));

        // 水平分割面板（强制50/50）
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftPanel,
                rightPanel
        );
        splitPane.setResizeWeight(0.5);      // 窗口缩放时保持50/50
        splitPane.setContinuousLayout(true);
        splitPane.setBackground(backgroundColor);
        splitPane.setBorder(BorderFactory.createLineBorder(borderColor, 1));

        // 三重保障强制50%位置
        // ① 立即设置
        splitPane.setDividerLocation(0.5);

        // ② 延迟到组件显示后设置
        SwingUtilities.invokeLater(() -> {
            if (splitPane.isShowing()) {
                splitPane.setDividerLocation(0.5);
            }
        });

        // ③ 监听显示+尺寸变化（终极保障）
        splitPane.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && splitPane.isShowing()) {
                splitPane.setDividerLocation(0.5);
            }
        });
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (splitPane.isShowing() && splitPane.getWidth() > 0) {
                        splitPane.setDividerLocation(0.5);
                    }
                });
            }
        });

        orderPanel.add(splitPane, BorderLayout.CENTER);

        // 3.2 菜单区域（占60%高度）
        JPanel menuPanel = new JPanel(new BorderLayout());
        menuPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        menuPanel.setBackground(backgroundColor);

        // ===== 修改：使用主题色设置菜单标题 =====
        String title = getMenuTypeTitle();
        JLabel menuTitleLabel = new JLabel(title, SwingConstants.CENTER);
        menuTitleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        menuTitleLabel.setForeground(titleColor); // 标题文字颜色
        menuTitleLabel.setOpaque(true);
        menuTitleLabel.setBackground(headerBgColor); // 标题背景色
        menuTitleLabel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor));
        menuPanel.add(menuTitleLabel, BorderLayout.NORTH);

        // 菜品表格 - 初始显示"加载中"（数据将在构造函数中异步加载）
        menuTableDisplay = new JEditorPane("text/html",
                "<html><body style='font-family: Microsoft YaHei; text-align:center; padding:20px; color:#7f8c8d;'>"
                        + "<p>⏳ 正在加载菜品数据...</p>"
                        + "</body></html>");
        menuTableDisplay.setEditable(false);
        menuScrollPane = new JScrollPane(menuTableDisplay);
        menuScrollPane.setBorder(BorderFactory.createTitledBorder("  菜品列表  "));
        menuScrollPane.setBackground(backgroundColor);
        menuPanel.add(menuScrollPane, BorderLayout.CENTER);

        // 垂直分割：订单区域40% | 菜单区域60%
        JSplitPane contentSplitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                orderPanel,
                menuPanel
        );
        contentSplitPane.setResizeWeight(0.4);
        contentSplitPane.setContinuousLayout(true);
        contentSplitPane.setDividerLocation(0.4);
        contentSplitPane.setBackground(backgroundColor);
        contentSplitPane.setBorder(BorderFactory.createLineBorder(borderColor, 1));

        contentPanel.add(contentSplitPane, BorderLayout.CENTER);
        add(contentPanel, BorderLayout.CENTER);

        // ===== STEP 4: SOUTH 区域 - 操作按钮 =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.setBackground(backgroundColor);
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JButton orderBtn = createThemedButton("点菜", buttonBgColor);
        JButton cancelOrderBtn = createThemedButton("取消点菜", buttonBgColor);
        JButton updateStatusBtn = createThemedButton("更新菜品状态", buttonBgColor);
        JButton addItemBtn = createThemedButton("添加菜品", buttonBgColor);
        JButton removeItemBtn = createThemedButton("删除菜品", buttonBgColor);
        JButton reviseItemBtn = createThemedButton("更改菜品价格", buttonBgColor);
        JButton backBtn = createThemedButton("返回主页", buttonBgColor);

        // 返回主页按钮事件（关键：实现跳转）
        backBtn.addActionListener(e -> frame.showPanel("Home"));
        addItemBtn.addActionListener(e -> showAddItemDialog());  // ← 绑定事件处理器
        updateStatusBtn.addActionListener(e -> showUpdateStatusDialog()); // ← 新增绑定
        orderBtn.addActionListener(e -> showOrderDialog());  // ← 绑定点菜对话框
        cancelOrderBtn.addActionListener(e -> showCancelOrderDialog());
        removeItemBtn.addActionListener(e->showRemoveItemDialog());
        reviseItemBtn.addActionListener(e -> showReviseItemPriceDialog());

        buttonPanel.add(orderBtn);
        buttonPanel.add(cancelOrderBtn);
        buttonPanel.add(updateStatusBtn);
        buttonPanel.add(addItemBtn);
        buttonPanel.add(removeItemBtn);
        buttonPanel.add(reviseItemBtn);
        buttonPanel.add(backBtn);

        add(buttonPanel, BorderLayout.SOUTH);

        // ===== 关键修复：延迟设置垂直分隔条位置 =====
        SwingUtilities.invokeLater(() -> {
            if (contentSplitPane != null && contentSplitPane.isShowing()) {
                contentSplitPane.setDividerLocation(0.4);
            }
        });
    }

    /**
     * 创建主题化按钮
     */
    private JButton createThemedButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));
        button.setBackground(bgColor);
        button.setForeground(Color.DARK_GRAY);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(borderColor, 1));
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(100, 30));

        // 添加悬停效果
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(buttonHoverColor);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor);
            }
        });

        return button;
    }

    /**
     * 统一风格：简约表格（所有列文字居中）
     */
    private String generateMenuTableWithItems(List<com.restaurant.entity.MenuItem> items) {
        StringBuilder html = new StringBuilder();
        // ===== 修改：使用主题色设置表格样式 =====
        html.append("<html><body style='font-family: Microsoft YaHei; margin:10px; background-color:#")
                .append(Integer.toHexString(backgroundColor.brighter().getRGB() & 0x00FFFFFF))
                .append(";'>");
        html.append("<table border='1' cellpadding='8' cellspacing='0' style='width:95%; margin:0 auto; border-collapse:collapse; border-color:#")
                .append(Integer.toHexString(borderColor.getRGB() & 0x00FFFFFF))
                .append(";'>");

        // 表头（所有列居中）- 使用主题色
        html.append("<tr style='background-color:#")
                .append(Integer.toHexString(headerBgColor.getRGB() & 0x00FFFFFF))
                .append(";'>");
        html.append("<th style='text-align:center; padding:8px;'>编号</th>");
        html.append("<th style='text-align:center; padding:8px;'>菜品名称</th>");
        html.append("<th style='text-align:center; padding:8px;'>价格</th>");
        html.append("<th style='text-align:center; padding:8px;'>状态</th>");
        html.append("</tr>");

        // 表体
        if (items == null || items.isEmpty()) {
            html.append("<tr>");
            html.append("<td colspan='4' style='text-align:center; padding:15px; color:gray;'>");
            html.append("🍽️ 暂无菜品数据");
            html.append("</td>");
            html.append("</tr>");
        } else {
            boolean evenRow = true;
            for (com.restaurant.entity.MenuItem item : items) {
                // 状态显示：根据 is_active 字段
                String statusHtml = item.isActive()
                        ? "<span style='color:green; font-weight:bold;'>✓ 售卖中</span>"
                        : "<span style='color:red; font-weight:bold;'>✗ 已停售</span>";

                // ===== 修正：为奇偶行设置不同的背景色（修复运算符优先级）=====
                String rowBg = evenRow
                        ? "background-color: #ffffff;"
                        : "background-color: #" + Integer.toHexString(backgroundColor.brighter().getRGB() & 0x00FFFFFF) + ";";
                evenRow = !evenRow;

                html.append("<tr style='").append(rowBg).append("'>");
                // 所有列都设置 text-align:center
                html.append("<td style='text-align:center;'>").append(item.getItemCode()).append("</td>");
                html.append("<td style='text-align:center;'>").append(item.getName()).append("</td>");
                html.append("<td style='text-align:center; color:#d32f2f; font-weight:bold;'>")
                        .append(String.format("%.2f", item.getPrice())).append(" 元</td>");
                html.append("<td style='text-align:center;'>").append(statusHtml).append("</td>");
                html.append("</tr>");
            }
        }

        html.append("</table></body></html>");
        return html.toString();
    }

    // 空表格（保持完全一致的居中对齐）
    private String generateEmptyMenuTable() {
        StringBuilder html = new StringBuilder();
        // ===== 修改：使用主题色设置表格样式 =====
        html.append("<html><body style='font-family: Microsoft YaHei; margin:10px; background-color:").append(backgroundColor.brighter().getRGB() & 0x00FFFFFF).append(";'>");
        html.append("<table border='1' cellpadding='8' cellspacing='0' style='width:95%; margin:0 auto; border-collapse:collapse; border-color:").append(borderColor.getRGB() & 0x00FFFFFF).append(";'>");
        html.append("<tr style='background-color:#f0f0f0; background-color:").append(headerBgColor.getRGB() & 0x00FFFFFF).append(";'>");
        html.append("<th style='text-align:center; padding:8px;'>编号</th>");
        html.append("<th style='text-align:center; padding:8px;'>菜品名称</th>");
        html.append("<th style='text-align:center; padding:8px;'>价格</th>");
        html.append("<th style='text-align:center; padding:8px;'>状态</th>");
        html.append("</tr>");
        html.append("<tr>");
        html.append("<td colspan='4' style='text-align:center; padding:15px; color:gray;'>");
        html.append("🍽️ 暂无菜品数据");
        html.append("</td>");
        html.append("</tr>");
        html.append("</table></body></html>");
        return html.toString();
    }

    // 获取菜单标题
    private String getMenuTypeTitle() {
        return switch (menuType) {
            case FOOD -> "特色食物菜单";
            case DRINK -> "饮料菜单";
            case STIRFRY -> "小炒菜单";
            case SETMEAL -> "套餐菜单";
            default -> "未知菜单";
        };
    }

    // 修改 setCurrentTableNumber() 方法
    public void setCurrentTableNumber(String tableNumber) {
        this.currentTableNumber = tableNumber;
        if (tableNumberDisplay != null) {
            tableNumberDisplay.setText("餐桌号：" + tableNumber);
        }

        // ✅ 关键：自动刷新两个区域
        refreshTemporaryOrderDisplay();
        refreshFormalOrderDisplay(); // ← 新增
    }

    /**
     * 显示添加菜品对话框
     */
    private void showAddItemDialog() {
        JDialog dialog = new JDialog(frame, "添加新菜品 - " + getMenuTypeTitle(), true);
        // ===== 修改：设置对话框背景色 =====
        dialog.getContentPane().setBackground(backgroundColor);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(450, 280);
        dialog.setLocationRelativeTo(this);

        // 表单面板
        JPanel formPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        formPanel.setBackground(backgroundColor); // 设置表单背景色

        // 菜品名称
        formPanel.add(new JLabel("菜品名称: *"));
        JTextField nameField = new JTextField(20);
        formPanel.add(nameField);

        // 价格
        formPanel.add(new JLabel("价格 (元): *"));
        JTextField priceField = new JTextField(10);
        formPanel.add(priceField);

        // 菜品编号（自动生成）
        formPanel.add(new JLabel("菜品编号:"));
        String prefix = getPrefixForCurrentMenu();

        // ✅ 修正1: 确保 nextCode 在所有路径都有值
        String nextCodeTemp = prefix + "1"; // 默认值
        try {
            nextCodeTemp = MenuCategoryService.getInstance().getNextItemCode(getMenuTypeConstant());
        } catch (SQLException e) {
            System.err.println("生成菜品编号失败: " + e.getMessage());
            // 保留默认值 prefix + "1"
        }
        final String nextCode = nextCodeTemp;

        JLabel codeLabel = new JLabel("<html><b>" + nextCode + "</b> (自动生成)</html>");
        formPanel.add(codeLabel);

        // 分类提示（只读）
        formPanel.add(new JLabel("所属分类:"));
        formPanel.add(new JLabel("<html><b>" + getMenuTypeTitle() + "</b></html>"));

        // 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setBackground(backgroundColor);

        // 更精确的 HTML 控制（带固定宽度）
        // 在 showAddItemDialog() 方法中
        JButton confirmBtn = createThemedButton("<html><b>✓</b>&nbsp;确认添加</html>", buttonBgColor);
        JButton cancelBtn = createThemedButton("<html><b>✗</b>&nbsp;取消</html>", buttonBgColor);


        confirmBtn.addActionListener(ev -> {
            String name = nameField.getText().trim();
            String priceText = priceField.getText().trim();

            // 验证
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入菜品名称", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (priceText.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入价格", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                // 使用 double
                double price = Double.parseDouble(priceText);
                price = Math.round(price * 100.0) / 100.0;

                if (price <= 0) {
                    JOptionPane.showMessageDialog(dialog, "价格必须大于0", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                //  使用完全限定名
                com.restaurant.entity.MenuItem item = new com.restaurant.entity.MenuItem(
                        nextCode,
                        name,
                        price,
                        MenuCategoryService.getInstance().getCategoryIdByMenuType(getMenuTypeConstant())
                );


                com.restaurant.dao.MenuItemDAO menuItemDAO = new com.restaurant.dao.impl.MenuItemDAOImpl();
                if (menuItemDAO.addItem(item)) {
                    JOptionPane.showMessageDialog(dialog,
                            "菜品添加成功!\n编号: " + item.getItemCode() +
                                    "\n名称: " + item.getName() +
                                    "\n价格: " + String.format("%.2f", item.getPrice()) + "元",
                            "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();

                    // 修正：统一缓存键类型（字符串）
                    String cacheKey = String.valueOf(menuType);
                    menuCache.remove(cacheKey);      // 失效旧缓存
                    loadMenuItems(false);            // 强制重新加载

                    JOptionPane.showMessageDialog(frame,
                            " 新菜品已添加并显示在菜单中！",
                            "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    throw new SQLException("数据库插入失败");
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "请输入有效的数字价格（例如：38.50）", "输入错误", JOptionPane.ERROR_MESSAGE);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog,
                        "添加菜品失败: " + ex.getMessage(),
                        "数据库错误",
                        JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        cancelBtn.addActionListener(ev -> dialog.dispose());

        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // 辅助方法：获取当前菜单类型的常量字符串
    private String getMenuTypeConstant() {
        return switch (menuType) {
            case FOOD -> "FOOD";
            case DRINK -> "DRINK";
            case STIRFRY -> "STIRFRY";
            default -> "SETMEAL";
        };
    }

    // 辅助方法：获取当前菜单的前缀
    private String getPrefixForCurrentMenu() {
        return switch (menuType) {
            case FOOD -> "A";
            case DRINK -> "B";
            case STIRFRY -> "C";
            default -> "D";
        };
    }


private void loadMenuItems(boolean useCache) {
    String cacheKey = String.valueOf(menuType);

    if (useCache && menuCache.containsKey(cacheKey)) {
        SwingUtilities.invokeLater(() -> {
            menuTableDisplay.setText(menuCache.get(cacheKey));
            menuTableDisplay.setCaretPosition(0);
        });
        return;
    }

    SwingWorker<String, Void> worker = new SwingWorker<>() {
        @Override
        protected String doInBackground() throws Exception {
            com.restaurant.dao.MenuItemDAO dao = new com.restaurant.dao.impl.MenuItemDAOImpl();
            int categoryId = MenuCategoryService.getInstance()
                    .getCategoryIdByMenuType(getMenuTypeConstant());

            List<com.restaurant.entity.MenuItem> items = dao.findByCategory(categoryId);
            menuItems = items;  // ← 关键：保存到类字段（仅此1行新增）
            return generateMenuTableWithItems(items);
        }

        @Override
        protected void done() {
            try {
                String htmlContent = get();
                menuCache.put(cacheKey, htmlContent);
                menuTableDisplay.setText(htmlContent);
                menuTableDisplay.setCaretPosition(0);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        frame,
                        "加载菜单失败: " + e.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                );
                e.printStackTrace();
                menuTableDisplay.setText(generateEmptyMenuTable());
            }
        }
    };

    worker.execute();
}

    private void showUpdateStatusDialog() {
        JDialog dialog = new JDialog(frame, "更新菜品状态 - " + getMenuTypeTitle(), true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(450, 200);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(backgroundColor);

        // 表单面板
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        formPanel.setBackground(backgroundColor);

        // 菜品编号输入
        formPanel.add(new JLabel("菜品编号: *"));
        JTextField itemCodeField = new JTextField(15);
        itemCodeField.setBackground(backgroundColor);      //  修复输入框背景色
        itemCodeField.setOpaque(true);                    // 确保背景色生效
        formPanel.add(itemCodeField);

        // 状态选择
        formPanel.add(new JLabel("目标状态: *"));
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBackground(backgroundColor);
        JRadioButton activeRadio = new JRadioButton("✓ 售卖中", true);
        JRadioButton inactiveRadio = new JRadioButton("✗ 已售罄");

        // 修复单选按钮背景色（关键修正）
        activeRadio.setBackground(backgroundColor);
        activeRadio.setOpaque(true);
        inactiveRadio.setBackground(backgroundColor);
        inactiveRadio.setOpaque(true);

        ButtonGroup group = new ButtonGroup();
        group.add(activeRadio);
        group.add(inactiveRadio);
        statusPanel.add(activeRadio);
        statusPanel.add(inactiveRadio);
        formPanel.add(statusPanel);

        // 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setBackground(backgroundColor);

        JButton confirmBtn = createThemedButton("<html><b>✓</b>&nbsp;确认更新</html>", buttonBgColor);
        JButton cancelBtn = createThemedButton("<html><b>✗</b>&nbsp;取消</html>", buttonBgColor);

        confirmBtn.addActionListener(ev -> {
            String itemCode = itemCodeField.getText().trim().toUpperCase();

            // 验证输入
            if (itemCode.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入菜品编号", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 验证菜品编号前缀是否匹配当前菜单类型
            String expectedPrefix = getPrefixForCurrentMenu();
            if (!itemCode.startsWith(expectedPrefix)) {
                JOptionPane.showMessageDialog(dialog,
                        "菜品编号前缀错误！\n当前菜单类型应为 '" + expectedPrefix + "' 开头（如 " + expectedPrefix + "1）",
                        "输入错误",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 获取目标状态
            boolean isActive = activeRadio.isSelected();

            try {
                // ✅ DAO 调用：封装数据库操作
                com.restaurant.dao.MenuItemDAO dao = new com.restaurant.dao.impl.MenuItemDAOImpl();
                boolean success = dao.updateStatus(itemCode, isActive);

                if (success) {
                    // 更新成功：刷新缓存 + 重新加载界面
                    JOptionPane.showMessageDialog(dialog,
                            "✅ 菜品状态更新成功！\n编号: " + itemCode +
                                    "\n新状态: " + (isActive ? "✓ 售卖中" : "✗ 已售罄"),
                            "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();

                    // ✅ 关键：清除缓存并强制刷新
                    String cacheKey = String.valueOf(menuType);
                    menuCache.remove(cacheKey);
                    loadMenuItems(false); // 强制重新查询数据库

                    // 同步更新全局状态缓存（供点菜时验证）
                    if (frame != null && frame instanceof OrderSystemGUI) {
                        ((OrderSystemGUI) frame).setMenuItemStatus(itemCode, isActive);
                    }

                    JOptionPane.showMessageDialog(frame,
                            " 菜单已刷新，状态变更已生效！",
                            "成功",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(dialog,
                            " 未找到菜品编号: " + itemCode + "\n请检查编号是否正确",
                            "警告",
                            JOptionPane.WARNING_MESSAGE);
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog,
                        "更新菜品状态失败: " + ex.getMessage(),
                        "数据库错误",
                        JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        cancelBtn.addActionListener(ev -> dialog.dispose());

        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }


    /**
     * 点菜对话框
     */
    private void showOrderDialog() {
        // 验证：必须先选择餐桌
        if (currentTableNumber.isEmpty() || currentTableNumber.equals("未选择")) {
            JOptionPane.showMessageDialog(frame, "请先选择餐桌号！", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 创建对话框
        JDialog dialog = new JDialog(frame, "点菜", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(400, 200);
        dialog.setLocationRelativeTo(this);

        // 表单
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel itemIdLabel = new JLabel("菜品编号:");
        JTextField itemIdField = new JTextField();

        JLabel quantityLabel = new JLabel("数量:");
        JTextField quantityField = new JTextField("1");  // 默认数量1

        formPanel.add(itemIdLabel);
        formPanel.add(itemIdField);
        formPanel.add(quantityLabel);
        formPanel.add(quantityField);

        // 按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton confirmBtn = new JButton("确认点菜");
        JButton cancelBtn = new JButton("取消");
        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);

        // 确认事件
        confirmBtn.addActionListener(ev -> {
            String itemId = itemIdField.getText().trim().toUpperCase();
            String quantityStr = quantityField.getText().trim();

            // 验证输入
            if (itemId.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入菜品编号", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (quantityStr.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入数量", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try {
                int quantity = Integer.parseInt(quantityStr);
                if (quantity <= 0) {
                    JOptionPane.showMessageDialog(dialog, "数量必须大于0", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // === 新增：验证菜品编号前缀是否匹配当前菜单类型 ===
                String expectedPrefix = getPrefixForCurrentMenu();
                if (!itemId.startsWith(expectedPrefix)) {
                    JOptionPane.showMessageDialog(dialog,
                            "菜品编号前缀错误！\n当前菜单类型应为 '" + expectedPrefix + "' 开头（如 " + expectedPrefix + "1）",
                            "输入错误",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 验证菜品是否存在且可售卖
                if (!frame.isMenuItemAvailable(itemId)) {
                    JOptionPane.showMessageDialog(dialog, "菜品 " + itemId + " 不存在或已售罄！",
                            "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 添加到临时订单（核心！）
                boolean success = frame.addTemporaryOrder(currentTableNumber, itemId, quantity);

                if (success) {
                    JOptionPane.showMessageDialog(dialog, "点菜成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();

                    // 刷新两个 Panel 的临时订单显示
                    refreshTemporaryOrderDisplay();          // 刷新当前 MenuPanel
                    frame.refreshHomeTemporaryOrder();       // 刷新 HomePanel
                } else {
                    JOptionPane.showMessageDialog(dialog, "点菜失败", "错误", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "数量必须是整数", "输入错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelBtn.addActionListener(ev -> dialog.dispose());

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }
    /**
     * 刷新临时订单显示
     */
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

            double[] totalAmount = {0.0};

            tempOrder.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        String itemId = entry.getKey();
                        int qty = entry.getValue();

                        // ✅ 修复：优先通过全局方法查询完整菜品信息
                        String itemName = "（未知）";
                        double price = 0.0;

                        // 方案1：直接通过frame全局查询（推荐）
                        com.restaurant.entity.MenuItem item = frame.getMenuItemById(itemId);
                        if (item != null) {
                            itemName = item.getName();
                            price = item.getPrice();
                        }
                        // 方案2：作为后备，再尝试从当前菜单的menuItems查找
                        else if (menuItems != null) {
                            for (com.restaurant.entity.MenuItem mi : menuItems) {
                                if (itemId.equals(mi.getItemCode())) {
                                    itemName = mi.getName();
                                    price = mi.getPrice();
                                    break;
                                }
                            }
                        }

                        double subtotal = price * qty;
                        totalAmount[0] += subtotal;

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
        temporaryHtmlDisplay.setText(html.toString());
    }

    private void showCancelOrderDialog() {
        // 1. 验证餐桌选择
        if (currentTableNumber.isEmpty() || "未选择".equals(currentTableNumber)) {
            JOptionPane.showMessageDialog(this, "请先选择餐桌号", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 2. 获取当前餐桌的临时订单
        Map<String, Integer> tempOrder = frame.getTemporaryOrderForTable(currentTableNumber);
        if (tempOrder == null || tempOrder.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前餐桌没有临时订单可以取消", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // 3. 创建取消点菜对话框
        JDialog dialog = new JDialog(frame, "取消点菜", true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(450, 220);
        dialog.setLocationRelativeTo(this);

        // 4. 构建菜品选择下拉框（显示：编号 - 名称 (当前数量)）
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));

        JLabel itemLabel = new JLabel("选择菜品:");
        JComboBox<String> itemComboBox = new JComboBox<>();

        // 填充下拉框选项
        tempOrder.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String itemId = entry.getKey();
                    int currentQty = entry.getValue();
                    String itemName = "（未知）";

                    // 尝试获取菜品名称
                    com.restaurant.entity.MenuItem item = frame.getMenuItemById(itemId);
                    if (item != null) {
                        itemName = item.getName();
                    }

                    itemComboBox.addItem(String.format("%s - %s (当前: %d 份)",
                            itemId, itemName, currentQty));
                });

        JLabel qtyLabel = new JLabel("取消数量:");
        JTextField qtyField = new JTextField("1");

        formPanel.add(itemLabel);
        formPanel.add(itemComboBox);
        formPanel.add(qtyLabel);
        formPanel.add(qtyField);

        // 5. 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        JButton confirmBtn = new JButton("确认取消");
        JButton cancelBtn = new JButton("取消");
        buttonPanel.add(confirmBtn);
        buttonPanel.add(cancelBtn);

        // 6. 确认取消逻辑
        confirmBtn.addActionListener(ev -> {
            try {
                // 解析选中的菜品
                String selectedItem = (String) itemComboBox.getSelectedItem();
                if (selectedItem == null) {
                    JOptionPane.showMessageDialog(dialog, "请选择要取消的菜品", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 提取菜品编号（格式: "A1 - 宫保鸡丁 (当前: 2 份)"）
                String itemId = selectedItem.split(" - ")[0].trim().toUpperCase();

                // 验证数量
                String qtyStr = qtyField.getText().trim();
                if (qtyStr.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "请输入取消数量", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                int cancelQty = Integer.parseInt(qtyStr);
                if (cancelQty <= 0) {
                    JOptionPane.showMessageDialog(dialog, "取消数量必须大于0", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 获取当前数量（双重验证）
                int currentQty = tempOrder.getOrDefault(itemId, 0);
                if (cancelQty > currentQty) {
                    JOptionPane.showMessageDialog(dialog,
                            String.format("取消数量不能超过当前数量！\n当前 %s 有 %d 份，您输入了 %d 份",
                                    itemId, currentQty, cancelQty),
                            "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 7. 执行取消操作（核心：添加负数量）
                frame.addTemporaryOrder(currentTableNumber, itemId, -cancelQty);

                // 8. 刷新UI
                refreshTemporaryOrderDisplay();          // 刷新当前MenuPanel
                frame.refreshHomeTemporaryOrder();       // 刷新HomePanel

                // 9. 关闭对话框并提示成功
                dialog.dispose();
                JOptionPane.showMessageDialog(frame,
                        String.format("已取消 %s × %d 份", itemId, cancelQty),
                        "成功", JOptionPane.INFORMATION_MESSAGE);

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "取消数量必须是有效整数", "输入错误", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "取消操作失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        cancelBtn.addActionListener(ev -> dialog.dispose());

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }


    /**
     * 刷新正式订单显示（不显示总计，避免与临时订单混淆）
     */
    public void refreshFormalOrderDisplay() {
        if (currentTableNumber == null || "未选择".equals(currentTableNumber)) {
            orderedHtmlDisplay.setText(
                    "<html><body style='font-family: Microsoft YaHei; padding:15px; color:#999; text-align:center;'>" +
                            "<p>请选择餐桌查看正式订单</p></body></html>"
            );
            return;
        }

        // 生成不带总计的HTML
        String htmlContent = frame.generateFormalOrderHtml(currentTableNumber, false);
        orderedHtmlDisplay.setText(htmlContent);
    }

    // MenuPanel.java
    private void showRemoveItemDialog() {
        JDialog dialog = new JDialog(frame, "物理删除菜品 - " + getMenuTypeTitle(), true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(480, 220);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(backgroundColor);

        // 表单面板
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        formPanel.setBackground(backgroundColor);

        formPanel.add(new JLabel("菜品编号: *"));
        JTextField itemCodeField = new JTextField(15);
        itemCodeField.setBackground(backgroundColor);
        itemCodeField.setOpaque(true);
        formPanel.add(itemCodeField);

        // 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setBackground(backgroundColor);

        JButton confirmBtn = createThemedButton("<html><b>⚠️</b>&nbsp;强制删除</html>",buttonBgColor);
        JButton cancelBtn = createThemedButton("<html><b>✗</b>&nbsp;取消</html>", buttonBgColor);

        confirmBtn.addActionListener(ev -> {
            String itemCode = itemCodeField.getText().trim().toUpperCase();

            // 验证前缀匹配当前菜单类型
            String expectedPrefix = getPrefixForCurrentMenu();
            if (!itemCode.startsWith(expectedPrefix)) {
                JOptionPane.showMessageDialog(dialog,
                        "菜品编号必须以 '" + expectedPrefix + "' 开头（当前菜单类型：" + getMenuTypeTitle() + "）",
                        "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 调用 Controller 执行删除（符合 MVC）
            boolean deleted = frame.deleteMenuItemPhysically(itemCode);
            if (deleted) {
                // 刷新 UI
                String cacheKey = String.valueOf(menuType);
                menuCache.remove(cacheKey);  // 清除菜单缓存
                loadMenuItems(false);        // 重新加载菜单
                refreshTemporaryOrderDisplay();

                JOptionPane.showMessageDialog(dialog,
                        " 菜品 " + itemCode + " 已物理删除！\n" +
                                "注意：历史订单中该菜品信息已丢失。",
                        "删除成功", JOptionPane.WARNING_MESSAGE);
                dialog.dispose();
            }
        });

        cancelBtn.addActionListener(ev -> dialog.dispose());

        btnPanel.add(confirmBtn);
        btnPanel.add(cancelBtn);

        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void showReviseItemPriceDialog() {
        JDialog dialog = new JDialog(frame, "更改菜品价格 - " + getMenuTypeTitle(), true);
        dialog.setLayout(new BorderLayout(15, 15));
        dialog.setSize(450, 220);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(backgroundColor);

        // 表单面板
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        formPanel.setBackground(backgroundColor);

        formPanel.add(new JLabel("菜品编号: *"));
        JTextField itemCodeField = new JTextField(15);
        itemCodeField.setBackground(backgroundColor);
        itemCodeField.setOpaque(true);
        formPanel.add(itemCodeField);

        formPanel.add(new JLabel("新价格 (元): *"));
        JTextField priceField = new JTextField(10);
        priceField.setBackground(backgroundColor);
        priceField.setOpaque(true);
        formPanel.add(priceField);

        // 按钮面板
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        btnPanel.setBackground(backgroundColor);

        JButton confirmBtn = createThemedButton("<html><b>✓</b>&nbsp;确认修改</html>", buttonBgColor);
        JButton cancelBtn = createThemedButton("<html><b>✗</b>&nbsp;取消</html>", buttonBgColor);

        confirmBtn.addActionListener(ev -> {
            String itemCode = itemCodeField.getText().trim().toUpperCase();
            String priceText = priceField.getText().trim();

            // 1. 基础验证
            if (itemCode.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入菜品编号", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (priceText.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入新价格", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 2. 格式完整性验证（防止仅输入前缀字母）
            if (itemCode.length() <= 1 || !Character.isDigit(itemCode.charAt(1))) {
                JOptionPane.showMessageDialog(dialog,
                        "<html>菜品编号格式错误！<br>" +
                                "正确格式应为：前缀字母 + 数字（例如 A1、B2、C3）<br>" +
                                "仅输入字母（如 'A'）无效</html>",
                        "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 3. 前缀验证（防止跨菜单操作）
            String expectedPrefix = getPrefixForCurrentMenu();
            if (!itemCode.startsWith(expectedPrefix)) {
                JOptionPane.showMessageDialog(dialog,
                        "<html>菜品编号前缀错误！<br>" +
                                "当前菜单应为 '" + expectedPrefix + "' 开头（例如 " + expectedPrefix + "1）</html>",
                        "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // ✅ 4. 【关键新增】菜品存在性验证（查询数据库）
            com.restaurant.entity.MenuItem menuItem = frame.getMenuItemById(itemCode);
            if (menuItem == null) {
                JOptionPane.showMessageDialog(dialog,
                        "<html> 菜品 " + itemCode + " 不存在！<br><br>" +
                                "可能原因：<br>" +
                                "• 编号输入错误（请检查数字部分）<br>" +
                                "• 该菜品已被删除<br>" +
                                "• 请先通过「添加菜品」功能创建该菜品</html>",
                        "菜品不存在", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // 显示找到的菜品信息（增强用户体验）
            System.out.println("✓ 找到菜品: " + menuItem.getName() + " (当前价格: " + menuItem.getPrice() + "元)");

            // 5. 价格格式验证
            double newPrice;
            try {
                newPrice = Double.parseDouble(priceText);
                newPrice = Math.round(newPrice * 100.0) / 100.0; // 保留2位小数
                if (newPrice <= 0) {
                    JOptionPane.showMessageDialog(dialog, "价格必须大于0", "输入错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                //  可选：价格合理性警告（防止误操作）
                double currentPrice = menuItem.getPrice();
                double changeRatio = Math.abs(newPrice - currentPrice) / currentPrice;
                if (changeRatio > 0.5 && currentPrice > 10) { // 价格变动超过50%且原价>10元
                    int confirm = JOptionPane.showConfirmDialog(dialog,
                            "<html> 价格变动较大！<br>" +
                                    "当前价格: " + String.format("%.2f", currentPrice) + "元 → 新价格: " + String.format("%.2f", newPrice) + "元<br>" +
                                    "变动幅度: " + String.format("%.0f%%", changeRatio * 100) + "<br><br>" +
                                    "确认要修改吗？</html>",
                            "价格变动警告", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (confirm != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "请输入有效的数字价格", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 6. 调用业务方法（包含安全验证 + DAO 调用）
            boolean success = frame.updateMenuItemPrice(itemCode, newPrice);
            if (success) {
                // 7. 刷新UI
                String cacheKey = String.valueOf(menuType);
                menuCache.remove(cacheKey);  // 清除菜单缓存
                loadMenuItems(false);        // 重新加载菜单

                JOptionPane.showMessageDialog(dialog,
                        "<html> 菜品 " + itemCode + " 价格更新成功！<br>" +
                                "「" + menuItem.getName() + "」<br>" +
                                "价格已从 " + String.format("%.2f", menuItem.getPrice()) + " 元<br>" +
                                "更新为 " + String.format("%.2f", newPrice) + " 元</html>",
                        "成功", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } else {
                //  增强失败提示（虽然理论上不会走到这里，因为前面已验证存在性）
                JOptionPane.showMessageDialog(dialog,
                        "价格修改失败，请稍后重试或联系管理员",
                        "操作失败", JOptionPane.ERROR_MESSAGE);
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