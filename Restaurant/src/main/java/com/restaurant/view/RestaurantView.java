package com.restaurant.view;

import com.restaurant.controller.RestaurantController;
import com.restaurant.entity.CustomerGroup;
import com.restaurant.entity.Tables;
import com.toedter.calendar.JDateChooser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.labels.ItemLabelAnchor;
import org.jfree.chart.labels.ItemLabelPosition;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.plot.*;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.chart.util.Rotation;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.general.PieDataset;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.AttributedString;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class RestaurantView extends JFrame {
    private RestaurantController controller;
    private JPanel tablesPanel, rightPanel, bottomPanel;
    private JTextArea queueDisplay, activityLogDisplay, tableStatusDisplay;
    private Color color2Seat = new Color(200, 180, 255);  // 2人桌颜色
    private Color color4Seat = new Color(255, 150, 100);  // 4人桌颜色
    private Color color6Seat = new Color(100, 200, 200); // 6人桌颜色
    private final Color colorVacant = new Color(255, 255, 255);    // 空闲
    private final Color colorOccupied = new Color(136, 255, 103); // 占用中
    private final Color colorSettingUp = new Color(255, 126, 0); // 准备中
    private final Color colorMerged = new Color(216, 191, 216);
    private JTextField groupSizeInput; // 组人数输入框
    private JButton addGroupButton, splitTableButton, recombineTableButton, orderButton, checkoutButton, changeTableButton, clearAllButton, queueManagementButton, selectTableButton, closeDayButton, reportButton;
    private LinkedList<String> logEntries = new LinkedList<>();
    private Map<String, Component> tableComponentMap = new HashMap<>(); // 新增：餐桌ID到组件的映射
    private JLabel statusLabel; // 添加这个成员变量


    public void setController(RestaurantController controller) {
        this.controller = controller;

        //  初始化时同步按钮文本和状态显示
        if (controller != null && controller.model != null) {
            boolean isOpen = controller.model.isOpenForBusiness();

            // 设置按钮文本
            setCloseDayButtonText(isOpen ? "结束营业" : "开始营业");

            // 更新状态显示
            updateBusinessStatusDisplay(isOpen);
        }
    }

    public RestaurantView() {
        tableComponentMap = new HashMap<>();
        setTitle("餐厅管理系统");
        setSize(1500, 1000);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(245, 245, 245));

        // 左侧面板：餐桌可视化
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(1000, 800));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        tablesPanel = new JPanel(new GridLayout(0, 3, 15, 15)); // 3列布局，水平和垂直间隙15像素
        JScrollPane leftScroll = new JScrollPane(tablesPanel);
        leftScroll.setBorder(BorderFactory.createTitledBorder("餐桌状态可视化"));
        leftPanel.add(leftScroll, BorderLayout.CENTER);
        add(leftPanel, BorderLayout.CENTER);

        // 右侧面板：三部分垂直分割
        rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(400, 800));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rightPanel.setBorder(BorderFactory.createTitledBorder("系统信息面板"));

        // 1. 创建队列状态显示区域
        queueDisplay = new JTextArea();
        queueDisplay.setEditable(false);
        queueDisplay.setLineWrap(true);
        queueDisplay.setWrapStyleWord(true);
        queueDisplay.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        JScrollPane queueScrollPane = new JScrollPane(queueDisplay);
        queueScrollPane.setBorder(BorderFactory.createTitledBorder("当前队列状态"));
        updateQueueDisplay(new LinkedList<>(), new LinkedList<>(), new LinkedList<>());

        // 2. 创建动态日志显示区域
        activityLogDisplay = new JTextArea();
        activityLogDisplay.setEditable(false);
        activityLogDisplay.setLineWrap(true);
        activityLogDisplay.setWrapStyleWord(true);
        activityLogDisplay.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JScrollPane logScrollPane = new JScrollPane(activityLogDisplay);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("操作日志"));

        // 3. 创建餐桌状态详情显示区域
        tableStatusDisplay = new JTextArea();
        tableStatusDisplay.setEditable(false);
        tableStatusDisplay.setLineWrap(true);
        tableStatusDisplay.setWrapStyleWord(true);
        tableStatusDisplay.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        JScrollPane tableStatusScrollPane = new JScrollPane(tableStatusDisplay);
        tableStatusScrollPane.setBorder(BorderFactory.createTitledBorder("餐桌状态详情"));

        // 4. 创建内层分割面板 - 垂直分割餐桌状态和日志
        JSplitPane innerSplitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                tableStatusScrollPane,  // 餐桌状态详情（第三部分）
                logScrollPane           // 操作日志（第二部分）
        );
        innerSplitPane.setDividerLocation(300); // 设置内层分割位置
        innerSplitPane.setResizeWeight(0.65);   // 内层：餐桌状态65%，日志35%

        // 5. 创建外层分割面板 - 垂直分割队列和内层面板
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                queueScrollPane,   // 队列状态（第一部分）
                innerSplitPane     // 内层分割面板（包含餐桌状态和日志）
        );
        splitPane.setDividerLocation(150); // 设置外层分割位置
        splitPane.setResizeWeight(0.2);   // 外层：队列20%，其余80%给内层

        // 6. 设置分割面板的最小尺寸限制
        queueScrollPane.setMinimumSize(new Dimension(100, 80));
        tableStatusScrollPane.setMinimumSize(new Dimension(100, 150));
        logScrollPane.setMinimumSize(new Dimension(100, 100));

        rightPanel.add(splitPane, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        // 底部面板：控制按钮
        bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bottomPanel.setBackground(new Color(230, 230, 230));

        groupSizeInput = new JTextField(5);
        groupSizeInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));


        addGroupButton = createStyledButton("添加顾客组", new Color(70, 130, 180));
        queueManagementButton = createStyledButton("管理队列", new Color(150, 100, 50));
        selectTableButton = createStyledButton("选餐桌", new Color(160, 82, 181));
        splitTableButton = createStyledButton("拆分餐桌", new Color(150, 200, 50));
        recombineTableButton = createStyledButton("合并餐桌", new Color(50, 200, 50));
        clearAllButton = createStyledButton("清空所有餐桌", new Color(205, 92, 92));
        closeDayButton = createStyledButton("结束营业", new Color(178, 34, 34));
        changeTableButton = createStyledButton("换餐桌", new Color(255, 165, 0));
        orderButton = createStyledButton("点餐", new Color(0, 100, 255));
        checkoutButton = createStyledButton("结账", new Color(205, 185, 0));
        reportButton = createStyledButton("营业报表", new Color(0, 128, 128));
        bottomPanel.add(new JLabel("组人数:"));
        bottomPanel.add(groupSizeInput);
        bottomPanel.add(addGroupButton);
        bottomPanel.add(splitTableButton);
        bottomPanel.add(recombineTableButton);
        bottomPanel.add(orderButton);
        bottomPanel.add(checkoutButton);
        bottomPanel.add(changeTableButton);
        bottomPanel.add(clearAllButton);
        bottomPanel.add(queueManagementButton);
        bottomPanel.add(selectTableButton);
        bottomPanel.add(closeDayButton);
        bottomPanel.add(reportButton);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("微软雅黑", Font.BOLD, 12));//按钮大小
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    public void setTables(List<Tables> tables) {
        updateTablesDisplay(tables);
    }

    public void updateTablesDisplay(List<Tables> tables) {
        tablesPanel.removeAll();
        tableComponentMap.clear(); // 清空旧映射表

        // 定義餐桌顏色映射
        Color[] tableColors = new Color[16];
        for (int i = 1; i <= 15; i++) {
            if (i <= 6) {
                tableColors[i] = color2Seat;  // 1-6號2人桌
            } else if (i <= 12) {
                tableColors[i] = color4Seat;  // 7-12號4人桌
            } else {
                tableColors[i] = color6Seat;  // 13-15號6人桌
            }
        }

        // 為每個餐桌創建按鈕
        for (Tables table : tables) {
            JButton tableButton = createTableButton(table, tableColors);
            tablesPanel.add(tableButton);

            // 建立餐桌ID到UI組件的映射
            tableComponentMap.put(table.getDisplayId(), tableButton);
        }

        tablesPanel.revalidate();
        tablesPanel.repaint();

        if (controller != null) {
            controller.updateQueueDisplay();
        }
    }

    private JButton createTableButton(Tables table, Color[] tableColors) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout());
        button.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        button.setPreferredSize(new Dimension(180, 180));

        // 設置按鈕背景色
        updateButtonBackground(button, table);

        // 設置餐桌圖標 - 修復：使用基礎ID而非解析displayId
        // 因為拆分子桌後displayId會包含字母(如"3a")，無法直接轉為整數
        int baseId = table.getBaseId(); // 使用基礎ID獲取顏色
        // 確保baseId在有效範圍內
        if (baseId < 1 || baseId >= tableColors.length) {
            baseId = 1; // 預設使用第一個顏色
        }
        button.setIcon(table.createTableIcon(tableColors[baseId]));

        // 創建多行標籤（使用HTML實現格式控制）
        JLabel infoLabel = createTableInfoLabel(table);
        button.add(infoLabel, BorderLayout.SOUTH);

        // 添加點擊事件
        button.addActionListener(e -> {
            try {
                handleTableButtonClick(table);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this,
                        "加載餐桌詳情失敗: " + ex.getMessage(),
                        "錯誤",
                        JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        // 將按鈕添加到組件映射表
        tableComponentMap.put(table.getDisplayId(), button);

        return button;
    }

    // 修改：創建餐桌按鈕的專用方法 - 支持多行顯示
    private JLabel createTableInfoLabel(Tables table) {
        String displayId = table.getDisplayId();
        String statusText = getStatusText(table);

        // 顧客組信息
        String groupInfo = "";
        if (table.getCurrentGroup() != null) {
            groupInfo = String.format("<br>顧客組: <b>#%d</b> (%d人)",
                    table.getCurrentGroup().getCallNumber(),
                    table.getCurrentGroup().getSize());
        } else if (table.getCurrentGroupId() != null) {
            groupInfo = "<br>顧客組: #" + table.getCurrentGroupId() + " (加載中)";
        }

        // 使用HTML實現多行格式 - 原始字體和格式
        String html = "<html><center>" +
                "<b>餐桌 #" + displayId + "</b><br>" +
                "容量: " + table.getCapacity() + "人 &bull; " + statusText + groupInfo +
                "</center></html>";

        JLabel label = new JLabel(html, SwingConstants.CENTER);
        label.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        return label;
    }

    // 修改：狀態文本生成方法
    private String getStatusText(Tables table) {
        // 直接使用枚举中定义的显示名称
        String statusText = table.getStatus().toString();

        // 保留合并状态的特殊处理逻辑
        if (table.getTableType() == Tables.TableType.MERGED &&
                table.getStatus() == Tables.TableStatus.OCCUPIED) {
            statusText += " (合并中)";
        }

        return statusText;
    }

    // 保持背景色更新方法
    private void updateButtonBackground(JButton button, Tables table) {
        if (table.getTableType() == Tables.TableType.MERGED &&
                table.getStatus() == Tables.TableStatus.OCCUPIED) {
            button.setBackground(colorMerged);
        } else {
            switch (table.getStatus()) {
                case VACANT -> button.setBackground(colorVacant);
                case OCCUPIED -> button.setBackground(colorOccupied);
                case SETTING_UP -> button.setBackground(colorSettingUp);
                case SPLITTING -> button.setBackground(new Color(255, 215, 0)); // 金色表示拆分狀態
            }
        }
    }

    /**
     * 获取输入的组人数
     */
    public String getGroupSizeInput() {
        return groupSizeInput.getText();
    }

    public void clearGroupSizeInput() {
        groupSizeInput.setText("");
    }

    /**
     * 设置添加顾客组按钮的监听器
     *
     * @param listener 事件监听器
     */
    public void setAddGroupListener(ActionListener listener) {
        addGroupButton.addActionListener(listener);
    }

    private void handleTableButtonClick(Tables table) throws SQLException {
        if (table.getStatus() == Tables.TableStatus.OCCUPIED) {
            // === 通过 Model 层封装方法检测合并关系（不使用 getAllTables()）===
            Tables partnerTable = controller.model.getMergedPartnerTable(table.getDisplayId());
            boolean isMergedTable = (partnerTable != null);

            // 验证伙伴餐桌状态
            if (isMergedTable && partnerTable.getStatus() != Tables.TableStatus.OCCUPIED) {
                JOptionPane.showMessageDialog(
                        this,
                        String.format("⚠️ 伙伴餐桌 #%s 状态异常（当前状态: %s），无法完成合并离店操作",
                                partnerTable.getDisplayId(), partnerTable.getStatus()),
                        "状态错误",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // 获取顾客组信息（合并餐桌共享同一个顾客组）
            CustomerGroup group = table.getCurrentGroup();
            if (group == null && partnerTable != null) {
                group = partnerTable.getCurrentGroup();
            }
            if (group == null) {
                JOptionPane.showMessageDialog(
                        this,
                        "⚠️ 餐桌无关联顾客组，无法完成离店",
                        "数据错误",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            // 计算用餐时长
            LocalDateTime now = LocalDateTime.now();
            String duration = "0分钟";
            if (table.getStartTime() != null) {
                long minutes = java.time.Duration.between(table.getStartTime(), now).toMinutes();
                duration = minutes + "分钟";
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String endTimeFormatted = now.format(formatter);

            // === 构建合并餐桌专用确认消息 ===
            String message;
            if (isMergedTable) {
                message = String.format(
                        "<html><b>合并餐桌 #%s + #%s 离店确认</b><br><br>" +
                                "<font color='#d32f2f'>⚠️ 此操作将同时处理两张餐桌</font><br><br>" +
                                "<b>当前餐桌:</b> #%s (%s)<br>" +
                                "<b>伙伴餐桌:</b> #%s (%s)<br><br>" +
                                "顾客组: <b>#%d</b> (<font color='#1976d2'>%d人</font>)<br>" +
                                "开始时间: %s<br>" +
                                "结束时间: %s<br>" +
                                "总用餐时长: <b>%s</b><br><br>" +
                                "<font color='#d32f2f'><b>确认让此组合并餐桌的顾客离开?</b></font><br>" +
                                "<small>（两张餐桌将同时变为「准备中」状态）</small></html>",
                        table.getDisplayId(),
                        partnerTable.getDisplayId(),
                        table.getDisplayId(),
                        table.getStatus().getDisplayName(),
                        partnerTable.getDisplayId(),
                        partnerTable.getStatus().getDisplayName(),
                        group.getCallNumber(),
                        group.getSize(),
                        (table.getStartTime() != null) ? table.getFormattedStartTime() : "未知",
                        endTimeFormatted,
                        duration
                );
            } else {
                // 单餐桌标准消息
                message = String.format(
                        "<html><b>餐桌 #%s 详情</b><br><br>" +
                                "状态: <font color='#1a75ff'>占用中</font><br>" +
                                "开始时间: %s<br>" +
                                "结束时间: %s<br>" +
                                "总时长: <b>%s</b><br><br>" +
                                "顾客组: <b>#%d</b> (<font color='#1976d2'>%d人</font>)<br><br>" +
                                "<font color='red'><b>确认让此桌顾客离开?</b></font></html>",
                        table.getDisplayId(),
                        (table.getStartTime() != null) ? table.getFormattedStartTime() : "未知",
                        endTimeFormatted,
                        duration,
                        group.getCallNumber(),
                        group.getSize()
                );
            }

            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    message,
                    isMergedTable ? "合并餐桌离店确认" : "确认顾客离开",
                    JOptionPane.YES_NO_OPTION,
                    isMergedTable ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE
            );

            if (confirm == JOptionPane.YES_OPTION) {
                // 仅传递当前点击的餐桌，由 Model 层自动处理合并关系
                processCustomerDeparture(table);
            }
        } else if (table.getStatus() == Tables.TableStatus.SETTING_UP) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "确定清理餐桌 #" + table.getDisplayId() + " 吗？",
                    "确认清理",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                controller.model.cleanTable(table.getDisplayId());
                controller.updateView();
                updateLogDisplay("餐桌 #" + table.getDisplayId() + " 已清理完成，恢复为空闲状态。");
            }
        } else if (table.getStatus() == Tables.TableStatus.SPLITTING) {
            JOptionPane.showMessageDialog(this,
                    "餐桌 #" + table.getDisplayId() + " 当前处于拆分状态，不能直接操作。",
                    "操作受限",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }


    public void appendToLog(String message) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        activityLogDisplay.append("[" + timestamp + "] " + message + "\n");
        activityLogDisplay.setCaretPosition(activityLogDisplay.getDocument().getLength());
    }

    public void updateLogDisplay(String log) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);
        String newLog = "[" + timestamp + "] " + log;
        // 添加新日志到列表
        logEntries.addLast(newLog);

        // 如果超过30条，移除最旧的一条
        if (logEntries.size() > 30) {
            logEntries.removeFirst();
        }

        // 重新构建日志显示内容
        StringBuilder logContent = new StringBuilder();
        for (String entry : logEntries) {
            logContent.append(entry).append("\n");
        }

        activityLogDisplay.setText(logContent.toString());
        activityLogDisplay.setCaretPosition(activityLogDisplay.getDocument().getLength());
    }


    private void processCustomerDeparture(Tables table) {
        try {
            if (table.getCurrentGroup() == null) {
                JOptionPane.showMessageDialog(
                        this,
                        "无法清理餐桌 #" + table.getDisplayId() + "\n该餐桌处于占用状态但没有关联的顾客组",
                        "操作失败",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            int groupSize = table.getCurrentGroup().getSize();
            int callNumber = table.getCurrentGroup().getCallNumber();

            // 调用 Model 层处理离店
            boolean success = controller.model.finishMeal(table.getDisplayId());

            if (success) {
                //  只有成功才刷新 UI
                controller.updateView();
                JOptionPane.showMessageDialog(
                        this,
                        "餐桌 #" + table.getDisplayId() + " (" + callNumber + "号顾客组) 的顾客 (共" + groupSize + "人) 已成功离开",
                        "操作成功",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                //  失败时不修改任何状态，Model 层已处理回滚
                // finishMeal 内部已验证订单状态并返回 false
                JOptionPane.showMessageDialog(
                        this,
                        "餐桌 #" + table.getDisplayId() + " 离店失败\n" +
                                "原因：有未完成订单或未结账订单\n" +
                                "请先完成结账操作后再离店",
                        "操作失败",
                        JOptionPane.WARNING_MESSAGE
                );
                //  不调用 setStatus，保持原状态不变
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this,
                    "系统错误：" + e.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE
            );
            e.printStackTrace();
            //  不调用 setStatus，避免修改 startTime
            controller.updateView();  // 仅刷新 UI 显示
        }
    }

    public void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "错误", JOptionPane.ERROR_MESSAGE);
    }

    public void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "警告", JOptionPane.WARNING_MESSAGE);
    }

    public void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 更新队列显示
     */
    public void updateQueueDisplay(Queue<CustomerGroup> q2, Queue<CustomerGroup> q4, Queue<CustomerGroup> q6) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前队列\n\n");

        // 2人桌队列
        sb.append("2人桌队列:\n");
        if (q2.isEmpty()) {
            sb.append("• 无等待顾客\n");
        } else {
            int position = 1;
            for (CustomerGroup group : q2) {
                sb.append(String.format("• 排队号#%d (%d人) - 位置: %d\n",
                        group.getCallNumber(), group.getSize(), position++));
            }
        }
        sb.append("\n");

        // 4人桌队列
        sb.append("4人桌队列:\n");
        if (q4.isEmpty()) {
            sb.append("• 无等待顾客\n");
        } else {
            int position = 1;
            for (CustomerGroup group : q4) {
                sb.append(String.format("• 排队号#%d (%d人) - 位置: %d\n",
                        group.getCallNumber(), group.getSize(), position++));
            }
        }
        sb.append("\n");

        // 6人桌队列
        sb.append("6人桌队列:\n");
        if (q6.isEmpty()) {
            sb.append("• 无等待顾客\n");
        } else {
            int position = 1;
            for (CustomerGroup group : q6) {
                sb.append(String.format("• 排队号#%d (%d人) - 位置: %d\n",
                        group.getCallNumber(), group.getSize(), position++));
            }
        }

        queueDisplay.setText(sb.toString());
    }

    /**
     * 單一餐桌更新 - 真正的局部刷新
     */


    public void updateSingleTable(Tables table) {
        Component comp = tableComponentMap.get(table.getDisplayId());
        if (comp == null || !(comp instanceof JButton)) {
            System.out.println("⚠️ 未找到餐桌 #" + table.getDisplayId() + " 的UI組件，執行全量刷新");
            if (controller != null) {
                controller.updateView();
            }
            return;
        }

        JButton button = (JButton) comp;
        updateButtonBackground(button, table);

        // 更新图标（颜色逻辑保持不变）
        Color[] tableColors = new Color[16];
        for (int i = 1; i <= 15; i++) {
            if (i <= 6) tableColors[i] = color2Seat;
            else if (i <= 12) tableColors[i] = color4Seat;
            else tableColors[i] = color6Seat;
        }
        int baseId = table.getBaseId();
        if (baseId < 1 || baseId >= tableColors.length) baseId = 1;
        button.setIcon(table.createTableIcon(tableColors[baseId]));

        // 修复关键：使用 HTML 格式更新标签文本（确保换行）
        Component[] children = button.getComponents();
        for (Component child : children) {
            if (child instanceof JLabel label) {
                // 生成与 createTableInfoLabel() 一致的 HTML 文本
                String statusText = getStatusText(table);
                StringBuilder html = new StringBuilder("<html><center><b>餐桌 #")
                        .append(table.getDisplayId())
                        .append("</b><br>容量: ")
                        .append(table.getCapacity())
                        .append("人 &bull; ")
                        .append(statusText);

                // 添加顾客组信息（如有）
                if (table.getCurrentGroup() != null) {
                    html.append("<br>顧客組: <b>#")
                            .append(table.getCurrentGroup().getCallNumber())
                            .append("</b> (")
                            .append(table.getCurrentGroup().getSize())
                            .append("人)");
                } else if (table.getCurrentGroupId() != null) {
                    html.append("<br>顧客組: #")
                            .append(table.getCurrentGroupId())
                            .append(" (加載中)");
                }

                html.append("</center></html>");
                label.setText(html.toString());
                break;
            }
        }

        button.revalidate();
        button.repaint();
        System.out.println(" 局部刷新餐桌 #" + table.getDisplayId());
    }

    public void setSplitTableListener(ActionListener listener) {
        splitTableButton.addActionListener(listener);
    }

    // 添加设置合并餐桌监听器的方法
    public void setRecombineTableListener(ActionListener listener) {
        recombineTableButton.addActionListener(listener);
    }

    // com.restaurant.view.RestaurantView.java
    public void setOrderListener(ActionListener listener) {
        // 将外部传入的监听器绑定到 orderButton
        orderButton.addActionListener(listener);
    }

    public void updateTableStatusDisplay(List<Tables> tables) {
        StringBuilder sb = new StringBuilder();

        if (tables == null || tables.isEmpty()) {
            tableStatusDisplay.setText("暂无餐桌信息");
            return;
        }

        for (Tables table : tables) {
            // 1. 订单状态（仅占用中餐桌）
            String orderStatusText = "";
            if (controller != null && table.getStatus() == Tables.TableStatus.OCCUPIED) {
                // ✅ 修复核心：先检查活跃订单，再检查已结账状态
                boolean hasActiveOrder = controller.hasAnyOrderForTable(table.getDisplayId());//沒有
                if (hasActiveOrder) {
                    // 有活跃订单 → 检查上菜状态
                    if (controller.hasUnservedItems(table.getDisplayId())) {//沒有
                        orderStatusText = " |  订单情况：已下单(未完成)";
                    } else {
                        orderStatusText = " |  订单情况：已下单(已完成)";
                    }
                } else {
                    // 无活跃订单 → 检查是否已结账
                    if (controller.isTableCheckedOut(table.getDisplayId())) {//沒有
                        orderStatusText = " |  订单情况：已结账"; // ✅ 正确识别已结账状态
                    } else {
                        orderStatusText = " |  订单情况：未下单"; // 真正的未下单
                    }
                }
            }

            // 2. 顾客组信息
            String customerGroupInfo = "";
            if (table.getStatus() == Tables.TableStatus.OCCUPIED && table.getCurrentGroup() != null) {
                customerGroupInfo = String.format(" | 顾客组: #%d (%d人)",
                        table.getCurrentGroup().getCallNumber(),
                        table.getCurrentGroup().getSize());
            }

            // 3. 构建基础信息
            String statusText = table.getStatus().toString();
            sb.append(String.format(
                    "餐桌 #%s | 容量：%d人 | 状态：%s%s%s",//必須存在的
                    table.getDisplayId(),
                    table.getCapacity(),
                    statusText,
                    orderStatusText,
                    customerGroupInfo
            ));

            // 4. 时间信息（仅占用中）
            if (table.getStatus() == Tables.TableStatus.OCCUPIED && table.getStartTime() != null) {
                sb.append(String.format(" | 用餐時段：%s → %s",
                        table.getFormattedStartTime(),
                        table.getEndTime() != null ? table.getFormattedEndTime() : "进行中"));
            }

            sb.append("\n\n");
        }

        tableStatusDisplay.setText(sb.toString());

        // 滚动到顶部（EDT安全）
        SwingUtilities.invokeLater(() -> {
            if (tableStatusDisplay.getDocument().getLength() > 0) {
                tableStatusDisplay.setCaretPosition(0);
            }
        });
    }

    // 添加设置结账按钮监听器的方法
    public void setCheckoutListener(ActionListener listener) {
        checkoutButton.addActionListener(listener);
    }

    /**
     * 显示结账餐桌选择对话框
     *
     * @return 餐桌号或取消时返回null
     */
    public String showCheckoutDialog() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 5, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel("请输入要结账的餐桌号:");
        label.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));

        JTextField textField = new JTextField(15);
        textField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));

        panel.add(label);
        panel.add(textField);

        int result = JOptionPane.showConfirmDialog(this, panel, "结账", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            return textField.getText().trim();
        }
        return null;
    }

    /**
     * 显示餐桌结账界面（含订单详情/支付输入/找零计算）
     *
     * @note 独立窗口，通过Controller处理结账逻辑
     */
    public void showCheckoutInterface(String tableNumber) {
        JFrame dialog = new JFrame("结账 - 餐桌 " + tableNumber);
        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 订单信息面板
        JPanel orderPanel = new JPanel(new BorderLayout(10, 10));

        // 订单列表
        JEditorPane orderDisplay = new JEditorPane("text/html", "");
        orderDisplay.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(orderDisplay);
        scrollPane.setPreferredSize(new Dimension(650, 250));

        // 订单状态标签
        JLabel orderStatusLabel = new JLabel("订单状态: 加载中...");
        orderStatusLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));

        // 总金额标签
        JLabel totalLabel = new JLabel("总金额: 0.00元");
        totalLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        totalLabel.setForeground(Color.RED);

        // 支付面板
        JPanel paymentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JLabel paymentLabel = new JLabel("支付金额:");
        paymentLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));

        JTextField paymentField = new JTextField(10);
        paymentField.setFont(new Font("Microsoft YaHei", Font.PLAIN, 14));

        JButton checkoutButton = new JButton("确认结账");
        checkoutButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        checkoutButton.setBackground(new Color(0, 128, 0)); // 绿色
        checkoutButton.setForeground(Color.WHITE);

        paymentPanel.add(paymentLabel);
        paymentPanel.add(paymentField);
        paymentPanel.add(checkoutButton);

        // 添加到订单面板
        orderPanel.add(orderStatusLabel, BorderLayout.NORTH);
        orderPanel.add(scrollPane, BorderLayout.CENTER);
        orderPanel.add(totalLabel, BorderLayout.SOUTH);

        // 添加到主面板
        mainPanel.add(orderPanel, BorderLayout.CENTER);
        mainPanel.add(paymentPanel, BorderLayout.SOUTH);

        // 添加到对话框
        dialog.add(mainPanel, BorderLayout.CENTER);

        // 添加关闭监听器
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dialog.dispose();
            }
        });

        // 通过Controller加载订单数据
        new Thread(() -> {
            Map<String, Object> orderDetails = controller.getOrderDetails(tableNumber);//沒有
            SwingUtilities.invokeLater(() -> {
                renderOrderDetails(orderDetails, orderDisplay, orderStatusLabel, totalLabel);
            });
        }).start();

        // 确认结账按钮事件
        checkoutButton.addActionListener(e -> {
            try {
                String paymentStr = paymentField.getText().trim();
                if (paymentStr.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "请输入支付金额", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                double paymentAmount = Double.parseDouble(paymentStr);
                String totalText = totalLabel.getText();
                // 从标签中提取总金额（更健壮的方法）
                double totalAmount = 0.0;
                if (totalText.contains("总金额: ") && totalText.contains("元")) {
                    String amountStr = totalText.replace("总金额: ", "").replace("元", "").trim();
                    totalAmount = Double.parseDouble(amountStr);
                } else {
                    JOptionPane.showMessageDialog(dialog, "无法获取订单总金额", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (paymentAmount < totalAmount) {
                    JOptionPane.showMessageDialog(dialog, "支付金额不足！", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // 确认结账
                int confirm = JOptionPane.showConfirmDialog(dialog,
                        "确认结账?\n总金额: " + String.format("%.2f", totalAmount) + "元\n支付金额: " + String.format("%.2f", paymentAmount) + "元\n找零: " + String.format("%.2f", paymentAmount - totalAmount) + "元",
                        "确认结账",
                        JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    // 通过Controller处理结账
                    new Thread(() -> {
                        controller.handleCheckoutSubmit(tableNumber, paymentAmount);
                        SwingUtilities.invokeLater(() -> {
                            if (controller != null) {
                                controller.refreshOrderStatusOnly();
                            }
                            dialog.dispose();
                        });
                    }).start();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "请输入有效的支付金额", "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        dialog.setVisible(true);
    }

    /**
     * 渲染订单详情HTML（含上菜状态颜色标识）
     *
     * @note 失败时显示红色错误信息
     */
    private void renderOrderDetails(Map<String, Object> details, JEditorPane orderDisplay, JLabel statusLabel, JLabel totalLabel) {
        if (details.containsKey("error")) {
            statusLabel.setText("错误: " + details.get("error"));
            statusLabel.setForeground(Color.RED);
            totalLabel.setText("总金额: 0.00元");
            orderDisplay.setText("<html><body style='font-family: Microsoft YaHei; color: red;'>错误: " + details.get("error") + "</body></html>");
            return;
        }

        try {
            Timestamp orderTime = (Timestamp) details.get("orderTime");
            double totalAmount = (Double) details.get("totalAmount");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) details.get("items");

            // 构建HTML内容
            StringBuilder htmlContent = new StringBuilder();
            htmlContent.append("<html><body style='font-family: Microsoft YaHei; margin: 10px;'>");
            htmlContent.append("<h3 style='color: #2c3e50;'>订单详情 (").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(orderTime)).append(")</h3>");
            htmlContent.append("<table border='1' cellpadding='8' cellspacing='0' style='width: 100%; border-collapse: collapse; border-color: #ddd;'>");
            htmlContent.append("<tr style='background-color: #f8f9fa; font-weight: bold;'>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>菜品编号</th>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>菜品名称</th>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>数量</th>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>已上桌</th>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>单价</th>");
            htmlContent.append("<th style='border: 1px solid #ddd; padding: 8px;'>小计</th>");
            htmlContent.append("</tr>");

            boolean allServed = true;
            for (Map<String, Object> item : items) {
                String itemCode = (String) item.get("itemCode");
                String itemName = (String) item.get("itemName");
                int quantity = (Integer) item.get("quantity");
                int servedQuantity = (Integer) item.get("servedQuantity");
                double price = (Double) item.get("price");
                double subtotal = price * quantity;

                String statusColor = "green";
                String statusText = "已上桌";
                if (servedQuantity < quantity) {
                    statusColor = "orange";
                    statusText = "部分上桌";
                    allServed = false;
                }
                if (servedQuantity == 0) {
                    statusColor = "red";
                    statusText = "未上桌";
                    allServed = false;
                }

                htmlContent.append("<tr style='border: 1px solid #ddd;'>");
                htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px; text-align: center;'>").append(itemCode).append("</td>");
                htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px;'>").append(itemName).append("</td>");
                htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px; text-align: center;'>").append(quantity).append("</td>");
                htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px; color: ").append(statusColor).append("; text-align: center;'>");
                htmlContent.append(servedQuantity).append("/").append(quantity).append(" (").append(statusText).append(")</td>");
                htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px; text-align: right;'>¥").append(String.format("%.2f", price)).append("</td>");
                htmlContent.append("<td style='border: 1px solid #ddd; padding: 8px; text-align: right; font-weight: bold;'>¥").append(String.format("%.2f", subtotal)).append("</td>");
                htmlContent.append("</tr>");
            }
            htmlContent.append("</table>");
            htmlContent.append("<div style='margin-top: 10px; padding: 10px; background-color: #e8f4fd; border-radius: 5px;'>");
            htmlContent.append("<strong>总计:</strong> ").append(items.size()).append(" 个菜品，总数量: ").append(
                    items.stream().mapToInt(item -> (Integer) item.get("quantity")).sum()
            ).append(" 份");
            htmlContent.append("</div>");
            htmlContent.append("</body></html>");

            orderDisplay.setText(htmlContent.toString());
            totalLabel.setText("总金额: " + String.format("%.2f", totalAmount) + "元");

            if (allServed) {
                statusLabel.setText("订单状态: 所有菜品已上桌 ✓");
                statusLabel.setForeground(new Color(0, 128, 0)); // 深绿色
            } else {
                statusLabel.setText("订单状态: 部分菜品未上桌 ⚠");
                statusLabel.setForeground(new Color(255, 153, 0)); // 橙色
            }

        } catch (Exception ex) {
            statusLabel.setText("错误: 渲染订单详情失败");
            statusLabel.setForeground(Color.RED);
            orderDisplay.setText("<html><body style='font-family: Microsoft YaHei; color: red;'>错误: " + ex.getMessage() + "</body></html>");
            ex.printStackTrace();
        }
    }

    public void setChangeTableListener(ActionListener listener) {
        changeTableButton.addActionListener(listener);
    }

    public String[] showChangeTableDialog() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));

        JLabel fromLabel = new JLabel("请输入要换桌的餐桌ID（如 7 或 7a）:");
        JTextField fromField = new JTextField(10);

        JLabel toLabel = new JLabel("请输入目标空闲餐桌ID（如 8 或 8a）:");
        JTextField toField = new JTextField(10);

        panel.add(fromLabel);
        panel.add(fromField);
        panel.add(toLabel);
        panel.add(toField);

        int result = JOptionPane.showConfirmDialog(this, panel, "换桌", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String fromInput = fromField.getText().trim();
            String toInput = toField.getText().trim();

            if (fromInput.isEmpty() || toInput.isEmpty()) {
                showError("请输入完整的餐桌ID！");
                return null;
            }
            // fromInput：要换桌的餐桌编号（如 "7a"）；
            //toInput：目标餐桌编号（如 "8"）；
            // 然后返回一个包含这两个值的字符串数组：
            return new String[]{fromInput, toInput};
        } else {
            return null; // 用户点击取消
        }
    }


    public void setClearAllListener(ActionListener listener) {
        clearAllButton.addActionListener(listener);
    }

    public void setQueueManagementListener(ActionListener listener) {
        queueManagementButton.addActionListener(listener);
    }

    public void showQueueManagementDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel callNumberLabel = new JLabel("排队号码：");
        JTextField callNumberField = new JTextField(10);
        JLabel customerCountLabel = new JLabel("客户数量：");
        JTextField customerCountField = new JTextField(10);

        // 添加新的选项
        JCheckBox addGroupCheckbox = new JCheckBox("增加顾客组");
        JCheckBox editGroupSizeCheckbox = new JCheckBox("编辑顾客组人数");
        JCheckBox deleteGroupCheckbox = new JCheckBox("删除顾客组");

        //  检查餐厅是否营业
        boolean isOpenForBusiness = controller != null && controller.model != null && controller.model.isOpenForBusiness();

        //只检查是否有VACANT状态的主餐桌
        boolean hasVacantTables = false;
        if (controller != null && controller.model != null) {
            for (Tables table : controller.model.getTables()) {
                // 跳过子桌，只检查主餐桌
                if (table.getSubTableSuffix() != null && !table.getSubTableSuffix().isEmpty()) {
                    continue;
                }
                // 仅检查VACANT状态（完全空闲的餐桌）
                if (table.getStatus() == Tables.TableStatus.VACANT) {
                    hasVacantTables = true;
                    break;
                }
            }
        }

        //  检查是否有排队顾客
        boolean hasWaitingCustomers = controller != null && controller.hasWaitingCustomers();

        // 只有当没有VACANT餐桌且餐厅在营业时才启用"增加顾客组"
        boolean canAddGroups = !hasVacantTables && isOpenForBusiness;
        addGroupCheckbox.setEnabled(canAddGroups);

        // 设置精确的工具提示
        if (!isOpenForBusiness) {
            addGroupCheckbox.setToolTipText("餐厅已结束营业，不能添加新顾客组");
        } else if (canAddGroups) {
            addGroupCheckbox.setToolTipText("没有空闲餐桌，新顾客必须加入队列");
        } else {
            addGroupCheckbox.setToolTipText("有空闲餐桌，新顾客应直接入座，无需加入队列");
        }

        // 单选按钮组（保持互斥选择）
        ButtonGroup group = new ButtonGroup();
        group.add(addGroupCheckbox);
        group.add(editGroupSizeCheckbox);
        group.add(deleteGroupCheckbox);

        //  修正的智能默认选择逻辑 - 考虑营业状态
        if (!isOpenForBusiness) {
            // 餐厅不营业：不选择任何选项
            group.clearSelection();
        } else if (canAddGroups) {
            // 有营业且没有空闲餐桌：默认选择"增加顾客组"
            addGroupCheckbox.setSelected(true);
        } else if (hasWaitingCustomers) {
            // 有营业、有空闲餐桌且有排队顾客：默认选择"编辑顾客组人数"
            editGroupSizeCheckbox.setSelected(true);
        } else {
            // 有营业、有空闲餐桌且无排队顾客：不选择任何选项
            group.clearSelection();
        }

        // 复选框监听器 - 全面控制UI状态
        ActionListener checkboxListener = e -> {
            boolean isAddSelected = addGroupCheckbox.isSelected();
            boolean isEditSelected = editGroupSizeCheckbox.isSelected();
            boolean isDeleteSelected = deleteGroupCheckbox.isSelected();

            // 控制排队号码字段
            callNumberField.setEnabled(!isAddSelected);
            callNumberField.setEditable(!isAddSelected);
            callNumberLabel.setEnabled(!isAddSelected);

            // 控制客户数量字段
            customerCountField.setEnabled(isAddSelected || isEditSelected);
            customerCountField.setEditable(isAddSelected || isEditSelected);
            customerCountLabel.setEnabled(isAddSelected || isEditSelected);

            // 清空不必要的字段
            if (isAddSelected) {
                callNumberField.setText("");
            }
            if (!isAddSelected && !isEditSelected) {
                customerCountField.setText("");
            }
        };

        addGroupCheckbox.addActionListener(checkboxListener);
        editGroupSizeCheckbox.addActionListener(checkboxListener);
        deleteGroupCheckbox.addActionListener(checkboxListener);

        // 初始状态设置 - 必须在设置监听器后调用
        checkboxListener.actionPerformed(null);

        // 添加状态说明标签（增强用户体验）
        JLabel statusLabel = new JLabel();
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        //  根据营业状态更新状态标签
        if (!isOpenForBusiness) {
            statusLabel.setText("⛔ 餐厅已结束营业，不能添加新顾客");
            statusLabel.setForeground(new Color(180, 0, 0)); // 深红色
        } else if (canAddGroups) {
            statusLabel.setText("⚠️ 所有餐桌已满，新顾客必须加入队列");
            statusLabel.setForeground(new Color(180, 0, 0)); // 深红色
        } else {
            statusLabel.setText("✅ 有空闲餐桌，新顾客应直接入座");
            statusLabel.setForeground(new Color(0, 120, 0)); // 深绿色
        }

        // 布局设置
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        panel.add(callNumberLabel, gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        panel.add(callNumberField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(customerCountLabel, gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        panel.add(customerCountField, gbc);

        // 添加状态说明标签
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(statusLabel, gbc);

        // 添加新选项到布局
        gbc.gridy = 3;
        panel.add(addGroupCheckbox, gbc);

        gbc.gridy = 4;
        panel.add(editGroupSizeCheckbox, gbc);

        gbc.gridy = 5;
        panel.add(deleteGroupCheckbox, gbc);

        // 显示对话框
        int result = JOptionPane.showConfirmDialog(this, panel, "排队管理", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            boolean isAdd = addGroupCheckbox.isSelected();
            boolean isEdit = editGroupSizeCheckbox.isSelected();
            boolean isDelete = deleteGroupCheckbox.isSelected();

            // 验证至少选择了一个操作
            if (!isAdd && !isEdit && !isDelete) {
                showError("请选择一个操作！");
                return;
            }

            // ✅ 额外检查：如果餐厅不营业且用户尝试添加顾客组
            if (!isOpenForBusiness && isAdd) {
                showError("餐厅已结束营业，无法添加新顾客组！");
                return;
            }

            try {
                int callNumber = -1;
                int customerCount = -1;

                // 仅当不是"增加顾客组"时才验证排队号码
                if (!isAdd) {
                    String callNumberStr = callNumberField.getText().trim();
                    if (callNumberStr.isEmpty()) {
                        showError("请输入排队号码！");
                        return;
                    }
                    callNumber = Integer.parseInt(callNumberStr);
                }

                // 仅当是"增加"或"编辑"时才验证客户数量
                if (isAdd || isEdit) {
                    String customerCountStr = customerCountField.getText().trim();
                    if (customerCountStr.isEmpty()) {
                        showError("请填写客户数量！");
                        return;
                    }
                    customerCount = Integer.parseInt(customerCountStr);
                    if (customerCount <= 0) {
                        showError("客户数量必须大于0！");
                        return;
                    }
                }

                // 通过控制器处理队列管理操作
                if (controller != null) {
                    controller.handleQueueManagementAction(callNumber, customerCount, isAdd, isEdit, isDelete);
                }
            } catch (NumberFormatException ex) {
                showError("排队号码和客户数量必须是有效数字！");
            }
        }
    }

    public void setSelectTableListener(ActionListener listener) {
        selectTableButton.addActionListener(listener);
    }


    public void showSelectTableDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        // ===== 1. 操作模式选择（顶部！）=====
        gbc.gridy = 0;
        JLabel modeLabel = new JLabel("📌 操作模式:");
        modeLabel.setFont(modeLabel.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(modeLabel, gbc);

        gbc.gridy = 1;
        JRadioButton newCustomerRadio = new JRadioButton("新顾客入座", true);
        JRadioButton fromQueueRadio = new JRadioButton("从队列分配顾客");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(newCustomerRadio);
        modeGroup.add(fromQueueRadio);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        modePanel.add(newCustomerRadio);
        modePanel.add(fromQueueRadio);
        panel.add(modePanel, gbc);

        // ===== 2. 顾客组叫号输入（仅队列模式显示）=====
        gbc.gridy = 2;
        JLabel callNumberLabel = new JLabel("排隊號（如 5）:");
        panel.add(callNumberLabel, gbc);

        gbc.gridy = 3;
        JTextField callNumberField = new JTextField(12);
        panel.add(callNumberField, gbc);

        // 初始隐藏（默认新顾客模式）
        callNumberLabel.setVisible(false);
        callNumberField.setVisible(false);

        // ===== 3. 餐桌编号 =====
        gbc.gridy = 4;
        JLabel tableIdLabel = new JLabel("餐桌编号（如 7）:");
        panel.add(tableIdLabel, gbc);

        gbc.gridy = 5;
        JTextField tableIdField = new JTextField(12);
        panel.add(tableIdField, gbc);

        // ===== 4. 第二张餐桌（紧挨着上一个输入框，初始隐藏）=====
        gbc.gridy = 6;
        JLabel secondTableIdLabel = new JLabel("第二张餐桌编号（如 8）:");
        panel.add(secondTableIdLabel, gbc);

        gbc.gridy = 7;
        JTextField secondTableIdField = new JTextField(12);
        panel.add(secondTableIdField, gbc);

        secondTableIdLabel.setVisible(false);
        secondTableIdField.setVisible(false);

        // ===== 5. 人数输入（仅新顾客模式）=====
        gbc.gridy = 8;
        JLabel peopleCountLabel = new JLabel("人数:");
        panel.add(peopleCountLabel, gbc);

        gbc.gridy = 9;
        JTextField peopleCountField = new JTextField(12);
        panel.add(peopleCountField, gbc);

        // ===== 6. 餐桌容量选项 =====
        gbc.gridy = 10;
        JLabel tableTypeLabel = new JLabel("餐桌容量:");
        tableTypeLabel.setFont(tableTypeLabel.getFont().deriveFont(Font.BOLD));
        panel.add(tableTypeLabel, gbc);

        gbc.gridy = 11;
        JCheckBox twoSeatOption = new JCheckBox("2人桌（1-2人）", true);
        JCheckBox fourSeatOption = new JCheckBox("4人桌（1-4人）");
        JCheckBox sixSeatOption = new JCheckBox("6人桌（4-6人）");
        ButtonGroup seatGroup = new ButtonGroup();
        seatGroup.add(twoSeatOption);
        seatGroup.add(fourSeatOption);
        seatGroup.add(sixSeatOption);
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        typePanel.add(twoSeatOption);
        typePanel.add(fourSeatOption);
        typePanel.add(sixSeatOption);
        panel.add(typePanel, gbc);

        // ===== 7. 餐桌操作类型 =====
        gbc.gridy = 12;
        JLabel operationLabel = new JLabel("餐桌操作类型:");
        operationLabel.setFont(operationLabel.getFont().deriveFont(Font.BOLD));
        panel.add(operationLabel, gbc);

        gbc.gridy = 13;
        JCheckBox addGuestsOption = new JCheckBox("往桌子添加客人", true);
        JCheckBox mergeOption = new JCheckBox("合并桌子");
        JCheckBox shareOption = new JCheckBox("共享餐桌（拼桌）");
        JPanel operationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        operationPanel.add(addGuestsOption);
        operationPanel.add(mergeOption);
        operationPanel.add(shareOption);
        panel.add(operationPanel, gbc);

        // ===== 交互逻辑 =====
        fromQueueRadio.addActionListener(e -> {
            boolean isQueueMode = fromQueueRadio.isSelected();
            callNumberLabel.setVisible(isQueueMode);
            callNumberField.setVisible(isQueueMode);
            peopleCountLabel.setVisible(!isQueueMode);
            peopleCountField.setVisible(!isQueueMode);
            tableIdLabel.setText(isQueueMode ? "分配到餐桌编号:" : "餐桌编号（如 7）:");
            if (isQueueMode) {
                addGuestsOption.setToolTipText(
                        "排队顾客组分配规则： " + "• 不勾选 → 分配到空桌（标准操作）✅ " +
                                "• 勾选 → 尝试追加到已有顾客组（业务禁止）❌ " +
                                " 提示：排队顾客组是完整独立群体，不能拆分追加"
                );
            }
            panel.revalidate();
            panel.repaint();
        });

        newCustomerRadio.addActionListener(e -> {
            boolean isNewMode = newCustomerRadio.isSelected();
            callNumberLabel.setVisible(!isNewMode);
            callNumberField.setVisible(!isNewMode);
            peopleCountLabel.setVisible(isNewMode);
            peopleCountField.setVisible(isNewMode);
            tableIdLabel.setText("餐桌编号（如 7）:");
            if (isNewMode) {
                addGuestsOption.setToolTipText(
                        "将新顾客追加到已有顾客的餐桌（同一顾客组增加人数） " +
                                "例如：2人桌已有1人，再加1人变为2人"
                );
            }
            panel.revalidate();
            panel.repaint();
        });

        addGuestsOption.addActionListener(e -> {
            if (addGuestsOption.isSelected()) {
                mergeOption.setSelected(false);
                shareOption.setSelected(false);
                sixSeatOption.setVisible(true);
                secondTableIdLabel.setVisible(false);
                secondTableIdField.setVisible(false);
                twoSeatOption.setText("2人桌（1-2人）");
                fourSeatOption.setText("4人桌（1-4人）");
            }
            panel.revalidate();
            panel.repaint();
        });

        mergeOption.addActionListener(e -> {
            boolean isMerge = mergeOption.isSelected();
            if (isMerge) {
                addGuestsOption.setSelected(false);
                shareOption.setSelected(false);
                sixSeatOption.setSelected(false);
                sixSeatOption.setVisible(false);
                secondTableIdLabel.setVisible(true);
                secondTableIdField.setVisible(true);
                tableIdLabel.setText("第一張餐桌编号（如 7）:");
                twoSeatOption.setText("合并2人桌（3-4人）");
                fourSeatOption.setText("合并4人桌（5-8人）");
                twoSeatOption.setSelected(true);
            } else {
                sixSeatOption.setVisible(true);
                secondTableIdLabel.setVisible(false);
                secondTableIdField.setVisible(false);
                tableIdLabel.setText("餐桌编号（如 7）:");
                twoSeatOption.setText("2人桌（1-2人）");
                fourSeatOption.setText("4人桌（1-4人）");
                twoSeatOption.setSelected(true);
            }
            panel.revalidate();
            panel.repaint();
        });

        shareOption.addActionListener(e -> {
            if (shareOption.isSelected()) {
                addGuestsOption.setSelected(false);
                mergeOption.setSelected(false);
                sixSeatOption.setSelected(false);
                sixSeatOption.setVisible(false);
                secondTableIdLabel.setVisible(false);
                secondTableIdField.setVisible(false);
                tableIdLabel.setText("餐桌编号（如 7）:");
                twoSeatOption.setText("2人桌（1-2人）");
                fourSeatOption.setText("4人桌（1-4人）");
            } else {
                sixSeatOption.setVisible(true);
            }
            panel.revalidate();
            panel.repaint();
        });

        addGuestsOption.addActionListener(e -> {
            if (addGuestsOption.isSelected()) {
                mergeOption.setSelected(false);
                shareOption.setSelected(false);
                sixSeatOption.setVisible(true);
                secondTableIdLabel.setVisible(false);
                secondTableIdField.setVisible(false);
                tableIdLabel.setText("餐桌编号（如 7）:");
            }
            panel.revalidate();
            panel.repaint();
        });

        // ===== 创建对话框 =====
        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(this, "🍽️ 选择餐桌");
        dialog.setSize(520, 680);
        dialog.setLocationRelativeTo(null);

        // ===== 🔹 仅禁用"新顾客入座"模式（关闭营业时）=====
        if (controller != null && controller.model != null && !controller.model.isOpenForBusiness()) {
            newCustomerRadio.setEnabled(false);
            newCustomerRadio.setToolTipText("⛔ 餐厅已结束营业，不能添加新顾客");
            if (newCustomerRadio.isSelected() && fromQueueRadio.isEnabled()) {
                fromQueueRadio.setSelected(true);
                callNumberLabel.setVisible(true);
                callNumberField.setVisible(true);
                peopleCountLabel.setVisible(false);
                peopleCountField.setVisible(false);
                tableIdLabel.setText("分配到餐桌编号:");
            }
            panel.revalidate();
            panel.repaint();
        }

        // ===== ✅ 关键修复1：检测所有主桌是否被占用 =====
        boolean allMainTablesOccupied = false;
        if (controller != null && controller.model != null) {
            List<Tables> tables = controller.model.getTables();
            allMainTablesOccupied = tables.stream()
                    .filter(table -> table.getTableType() == Tables.TableType.MAIN)
                    .allMatch(table -> table.getStatus() == Tables.TableStatus.OCCUPIED);
        }
        if (allMainTablesOccupied) {
            mergeOption.setEnabled(false);
            shareOption.setEnabled(false);
            mergeOption.setSelected(false);
            shareOption.setSelected(false);
            mergeOption.setToolTipText("⚠️ 所有主桌已被占用，无法合并");
            shareOption.setToolTipText("⚠️ 所有主桌已被占用，无法共享");
            operationPanel.setToolTipText("⚠️ 所有主桌已被占用，合并/共享操作不可用");
            panel.revalidate();
            panel.repaint();
        }

        // ===== ✅ 关键修复2：检测队列是否为空（纯内存检查）=====
        boolean allQueuesEmpty = true;
        if (controller != null && controller.model != null) {
            Queue<CustomerGroup> q2 = controller.model.getQueue2Seat();
            Queue<CustomerGroup> q4 = controller.model.getQueue4Seat();
            Queue<CustomerGroup> q6 = controller.model.getQueue6Seat();
            allQueuesEmpty = q2.isEmpty() && q4.isEmpty() && q6.isEmpty();
        }
        if (allQueuesEmpty) {
            fromQueueRadio.setEnabled(false);
            fromQueueRadio.setSelected(false);
            newCustomerRadio.setSelected(true);
            callNumberLabel.setVisible(false);
            callNumberField.setVisible(false);
            peopleCountLabel.setVisible(true);
            peopleCountField.setVisible(true);
            tableIdLabel.setText("餐桌编号（如 7）:");
            fromQueueRadio.setToolTipText("⚠️ 当前无排队顾客，无法从队列分配");
            modePanel.setToolTipText("⚠️ 所有队列为空，仅支持新顾客入座");
            panel.revalidate();
            panel.repaint();
        }

        // ===== 🔹 关闭营业且无队列时禁用"确定"按钮（关键修复）=====
        // 先计算条件，再显示对话框
        boolean isClosed = (controller != null && controller.model != null &&
                !controller.model.isOpenForBusiness());
        boolean queuesEmpty = allQueuesEmpty;  // 复用上面已计算的值

        // 显示对话框（模态阻塞）
        dialog.setVisible(true);

        // ✅ 关键：对话框关闭后，如果用户点了"确定"但条件不满足 → 拦截并提示
        if (isClosed && queuesEmpty &&
                optionPane.getValue() != null &&
                optionPane.getValue().equals(JOptionPane.OK_OPTION)) {
            // 用户试图在禁用状态下点击确定 → 拦截并提示
            showError("⛔ 餐厅已打烊且无排队顾客，无法执行此操作！");
            return;  // 阻止后续所有处理
        }
        // ===== 🔹 禁用逻辑结束 =====

        // ===== 处理结果 =====
        if (optionPane.getValue() != null &&
                optionPane.getValue().equals(JOptionPane.OK_OPTION)) {

            String tableIdInput = tableIdField.getText().trim();
            if (tableIdInput.isEmpty()) {
                showError("请输入餐桌编号！");
                return;
            }

            boolean isFromQueue = fromQueueRadio.isSelected();
            int peopleCount = 0;
            int callNumber = 0;

            if (isFromQueue) {
                String callNumberInput = callNumberField.getText().trim();
                if (callNumberInput.isEmpty()) {
                    showError("请输入顾客组叫号！");
                    return;
                }
                try {
                    callNumber = Integer.parseInt(callNumberInput);
                    if (callNumber <= 0) {
                        showError("叫号必须大于0！");
                        return;
                    }
                } catch (NumberFormatException ex) {
                    showError("叫号必须为整数！");
                    return;
                }
            } else {
                String peopleInput = peopleCountField.getText().trim();
                if (peopleInput.isEmpty()) {
                    showError("请输入人数！");
                    return;
                }
                try {
                    peopleCount = Integer.parseInt(peopleInput);
                    if (peopleCount <= 0) {
                        showError("人数必须大于0！");
                        return;
                    }
                } catch (NumberFormatException ex) {
                    showError("人数必须为整数！");
                    return;
                }
            }

            boolean isMerge = mergeOption.isSelected();
            boolean isTwoSeat = twoSeatOption.isSelected();
            boolean isFourSeat = fourSeatOption.isSelected();
            boolean isSixSeat = sixSeatOption.isSelected();
            boolean isAddGuests = addGuestsOption.isSelected();
            boolean isShare = shareOption.isSelected();

            String secondTableIdInput = isMerge ? secondTableIdField.getText().trim() : null;
            if (isMerge && (secondTableIdInput == null || secondTableIdInput.isEmpty())) {
                showError("请输入第二张餐桌编号！");
                return;
            }

            controller.handleManualTableAssignment(
                    tableIdInput,
                    peopleCount,
                    isFromQueue,
                    callNumber,
                    isMerge,
                    isTwoSeat,
                    isFourSeat,
                    isSixSeat,
                    isAddGuests,
                    isShare,
                    secondTableIdInput
            );
        }
    }

    public void setCloseDayListener(ActionListener listener) {
        closeDayButton.addActionListener(listener);
    }

    public void setCloseDayButtonText(String text) {
        closeDayButton.setText(text);
        // 根据营业状态调整按钮颜色
        if ("开始营业".equals(text)) {
            closeDayButton.setBackground(new Color(178, 34, 34)); // 深红色
        } else {
            closeDayButton.setBackground(new Color(0, 150, 0)); // 绿色
        }
    }

    /**
     * 更新营业状态显示（绿色=营业中/红色=打烊）
     *
     * @note 仅更新UI标题样式，无业务逻辑
     */
    public void updateBusinessStatusDisplay(boolean isOpen) {
        // 1. 状态配置
        String title = isOpen ? "🟢 餐厅状态：营业中" : "🔴 餐厅状态：已打烊";
        Color titleColor = isOpen ? new Color(0, 120, 0) : new Color(180, 40, 40);
        Color bgColor = isOpen ?
                new Color(232, 245, 233) :  // 浅绿色背景
                new Color(255, 235, 235);   // 浅红色背景
        Color borderColor = isOpen ?
                new Color(76, 175, 80) :     // 绿色边框
                new Color(244, 67, 54);      // 红色边框

        // 2. 创建带圆角的边框
        Border lineBorder = BorderFactory.createLineBorder(borderColor, 2);
        Border emptyBorder = BorderFactory.createEmptyBorder(8, 12, 8, 12);
        Border compoundBorder = BorderFactory.createCompoundBorder(lineBorder, emptyBorder);

        // 3. 创建标题边框（带字体和颜色）
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                compoundBorder,
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Microsoft YaHei UI", Font.BOLD, 14),
                titleColor
        );
        titledBorder.setTitleJustification(TitledBorder.LEFT);

        // 4. 应用边框并设置背景
        bottomPanel.setBorder(titledBorder);
        bottomPanel.setBackground(bgColor);
        bottomPanel.setOpaque(true);  // 确保背景色生效

        // 5. 刷新显示
        bottomPanel.revalidate();
        bottomPanel.repaint();

        // 6. 可选：添加状态切换动画效果
        animateStatusChange(bottomPanel, isOpen);
    }

    /**
     * 添加轻微的状态切换动画（淡入效果）
     */
    private void animateStatusChange(JPanel panel, boolean isOpen) {
        // 简单脉冲效果：轻微缩放 + 透明度变化
        Timer timer = new Timer(30, e -> {
            // 实际项目中可使用 TimingFramework 或自定义动画
            // 这里仅示意，可根据需要扩展
        });
        timer.setRepeats(false);
        timer.start();
    }

    public void setReportListener(ActionListener listener) {
        reportButton.addActionListener(listener);
    }

    public void showBusinessReportDialog() {
        JDialog reportDialog = new JDialog(this, "营业报表统计", true);
        reportDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // 创建主滚动面板
        JScrollPane mainScrollPane = new JScrollPane();
        mainScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.getViewport().setBackground(Color.WHITE);

        // 创建内容面板
        JPanel contentPanel = new JPanel(new BorderLayout(15, 15));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 创建选项卡面板
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("微软雅黑", Font.BOLD, 12));
        tabbedPane.setPreferredSize(new Dimension(1050, 500)); // 设置选项卡面板的首选大小

        // 1. 营业总览面板 - 现在包含统计范围选择
        JPanel overviewPanel = createBusinessOverviewPanel();
        tabbedPane.addTab("营业总览", overviewPanel);

        // 2. 菜品销售分析面板 - 为了兼容性保留日期选择器参数
        JPanel emptyPanelForDish = new JPanel();
        JPanel dishAnalysisPanel = createDishAnalysisPanel(null, null, null, null, statusLabel);
        tabbedPane.addTab("菜品销售分析", dishAnalysisPanel);

        // 状态栏
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusLabel = new JLabel("就绪. 就緒.");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        // 操作按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton exportButton = new JButton("导出Excel");
        exportButton.setPreferredSize(new Dimension(100, 30));
        JButton printButton = new JButton("打印报表");
        printButton.setPreferredSize(new Dimension(100, 30));
        JButton closeButton = new JButton("关闭");
        closeButton.setPreferredSize(new Dimension(80, 30));
        buttonPanel.add(exportButton);
        buttonPanel.add(printButton);
        buttonPanel.add(closeButton);

        // 底部面板
        JPanel bottomPanel = new JPanel(new BorderLayout(0, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 5, 0));
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(statusPanel, BorderLayout.SOUTH);

        // 将各组件添加到内容面板
        JScrollPane tabScrollPane = new JScrollPane(tabbedPane);
        tabScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tabScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        contentPanel.add(tabScrollPane, BorderLayout.CENTER);
        contentPanel.add(bottomPanel, BorderLayout.SOUTH);

        // 设置主滚动面板的内容
        mainScrollPane.setViewportView(contentPanel);

        // 设置对话框内容
        reportDialog.add(mainScrollPane);

        // 设置合理的初始大小，同时保留滚动功能
        reportDialog.setSize(1150, 850);

        // 导出按钮
        exportButton.addActionListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex == 0) { // 营业总览
                JTable reportTable = getTableFromPanel(overviewPanel);
                if (reportTable == null) {
                    JOptionPane.showMessageDialog(reportDialog, "表格未初始化", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                DefaultTableModel tableModel = (DefaultTableModel) reportTable.getModel();
                if (tableModel == null || tableModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(reportDialog, "没有数据可导出", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                exportReportToExcel(reportTable);
            } else { // 菜品销售分析
                JTable dishTable = getDishTableFromPanel(dishAnalysisPanel);
                if (dishTable == null) {
                    JOptionPane.showMessageDialog(reportDialog, "表格未初始化", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                DefaultTableModel tableModel = (DefaultTableModel) dishTable.getModel();
                if (tableModel == null || tableModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(reportDialog, "没有数据可导出", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                exportDishSalesToExcel(dishTable);
            }
        });

        // 打印按钮
        printButton.addActionListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex == 0) { // 营业总览
                JTable reportTable = getTableFromPanel(overviewPanel);
                if (reportTable == null) {
                    JOptionPane.showMessageDialog(reportDialog, "表格未初始化", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                DefaultTableModel tableModel = (DefaultTableModel) reportTable.getModel();
                if (tableModel == null || tableModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(reportDialog, "没有数据可打印", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                try {
                    boolean complete = reportTable.print();
                    if (complete) {
                        showTimeMessage("打印任务已发送到打印机", "操作成功");
                    } else {
                        JOptionPane.showMessageDialog(reportDialog, "打印被取消", "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(reportDialog, "打印失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            } else { // 菜品销售分析
                JTable dishTable = getDishTableFromPanel(dishAnalysisPanel);
                if (dishTable == null) {
                    JOptionPane.showMessageDialog(reportDialog, "表格未初始化", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                DefaultTableModel tableModel = (DefaultTableModel) dishTable.getModel();
                if (tableModel == null || tableModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(reportDialog, "没有数据可打印", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                try {
                    boolean complete = dishTable.print();
                    if (complete) {
                        showTimeMessage("打印任务已发送到打印机", "操作成功");
                    } else {
                        JOptionPane.showMessageDialog(reportDialog, "打印被取消", "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(reportDialog, "打印失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // 关闭按钮
        closeButton.addActionListener(e -> reportDialog.dispose());

        reportDialog.setLocationRelativeTo(this);
        reportDialog.setVisible(true);
    }

    /**
     * 递归查找容器内指定名称的组件
     *
     * @param container 起始容器（支持嵌套组件树）
     * @param name      目标组件的getName()标识
     * @return 首个匹配组件，无匹配返回null
     * @note 1. **搜索策略**：
     * - 深度优先遍历组件树
     * - 精确字符串匹配（区分大小写）
     * 2. **典型用途**：
     * - 从复杂面板结构中定位特定组件
     * - 替代硬编码组件引用（解耦UI结构）
     * 3. **性能警告**：
     * - 避免在高频操作中调用（遍历整个子树）
     * - 深层嵌套容器可能影响性能
     * 4. **约束**：
     * - 仅匹配显式设置setName()的组件
     * - 返回首个匹配项（不保证唯一性）
     * @example 在tabbedPane中查找名为"chartPanel"的子面板：
     * JPanel chart = (JPanel)findComponentByName(tabbedPane, "chartPanel");
     */
    private Component findComponentByName(Container container, String name) {
        for (int i = 0; i < container.getComponentCount(); i++) {
            Component comp = container.getComponent(i);
            if (name.equals(comp.getName())) {
                return comp;
            }
            if (comp instanceof Container) {
                Component found = findComponentByName((Container) comp, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 创建营业概览分析面板（交互式报表生成器）
     *
     * @return 营业数据可视化面板，包含日期选择器/数据表格/双图表面板
     * @note 1. **双模式数据查询**：
     * - 单日模式：精确分析特定日期经营状况
     * - 范围模式：对比多日趋势（自动校验日期顺序）
     * 2. **智能数据展示**：
     * - 表格自动高亮总计行（蓝色背景+粗体）
     * - 交错行颜色提升可读性
     * - 双图表联动（营业额+顾客数量趋势）
     * 3. **性能优化**：
     * - 后台线程加载数据（SwingWorker）
     * - 按钮状态管理（加载中禁用）
     * - 内存安全：表格模型动态重置
     * 4. **用户体验设计**：
     * - 日期选择器即时启用/禁用
     * - 列宽精确控制（日期/金额列右对齐）
     * - 操作成功浮动提示（showTimeMessage）
     * 5. **错误防御**：
     * - 空日期选择验证
     * - 日期范围逻辑校验（开始≤结束）
     * - 异常捕获不中断主线程
     * 6. **典型业务流程**：
     * ① 选择2024-01-15单日 → ② 生成报表 →
     * ③ 查看当日客单价(¥85.3)和订单量(12) →
     * ④ 切换至2024-01-01至2024-01-31范围分析月趋势
     * @warning 1. 依赖controller.getDailyBusinessReport()实现
     * 2. 要求statusLabel已初始化（避免NPE）
     * 3. JDateChooser需处理时区问题（使用java.util.Date）
     */
    private JPanel createBusinessOverviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ===== 保持原有结构不变，只在顶部添加统计范围面板 =====
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        controlPanel.setBorder(BorderFactory.createTitledBorder("选择统计范围"));

        // 模式选择
        ButtonGroup modeGroup = new ButtonGroup();
        JRadioButton singleDayRadio = new JRadioButton("单日统计", true);
        JRadioButton rangeRadio = new JRadioButton("日期范围统计", false);
        modeGroup.add(singleDayRadio);
        modeGroup.add(rangeRadio);

        controlPanel.add(singleDayRadio);
        controlPanel.add(new JLabel("日期:"));

        // 日期选择器
        JDateChooser singleDayChooser = new JDateChooser(new java.util.Date());
        singleDayChooser.setDateFormatString("yyyy-MM-dd");
        singleDayChooser.setPreferredSize(new Dimension(120, 28));
        controlPanel.add(singleDayChooser);

        controlPanel.add(rangeRadio);
        controlPanel.add(new JLabel("从:"));

        JDateChooser startDateChooser = new JDateChooser();
        startDateChooser.setDateFormatString("yyyy-MM-dd");
        startDateChooser.setPreferredSize(new Dimension(120, 28));
        startDateChooser.setEnabled(false);
        controlPanel.add(startDateChooser);

        controlPanel.add(new JLabel("到:"));

        JDateChooser endDateChooser = new JDateChooser(new java.util.Date());
        endDateChooser.setDateFormatString("yyyy-MM-dd");
        endDateChooser.setPreferredSize(new Dimension(120, 28));
        endDateChooser.setEnabled(false);
        controlPanel.add(endDateChooser);

        // 模式切换监听器
        singleDayRadio.addActionListener(e -> {
            singleDayChooser.setEnabled(true);
            startDateChooser.setEnabled(false);
            endDateChooser.setEnabled(false);
            singleDayChooser.requestFocus();
        });

        rangeRadio.addActionListener(e -> {
            singleDayChooser.setEnabled(false);
            startDateChooser.setEnabled(true);
            endDateChooser.setEnabled(true);
            startDateChooser.requestFocus();
        });

        // 生成报表按钮
        JButton generateButton = new JButton("生成报表");
        generateButton.setPreferredSize(new Dimension(100, 30));
        generateButton.setFont(new Font("微软雅黑", Font.BOLD, 12));
        controlPanel.add(generateButton);
        // ===== 统计范围面板结束 =====

        // 创建表格面板 - 保持原有代码不变
        String[] columnNames = {"日期", "总营业额(元)", "顾客总数", "平均客单价(元)", "订单数量"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 表格不可编辑
            }
        };

        JTable reportTable = new JTable(tableModel);
        reportTable.setRowHeight(25);
        reportTable.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        reportTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));
        reportTable.setFillsViewportHeight(true);

        // 设置表格列宽
        reportTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        reportTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        reportTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        reportTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        reportTable.getColumnModel().getColumn(4).setPreferredWidth(80);

        // 设置表格渲染器
        reportTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (row == table.getRowCount() - 1 && value != null && value.toString().contains("总计")) {
                    // 总计行高亮显示
                    c.setFont(new Font("微软雅黑", Font.BOLD, 12));
                    c.setBackground(new Color(220, 230, 255));
                } else if (row % 2 == 0) {
                    // 交错行颜色
                    c.setBackground(new Color(245, 245, 245));
                } else {
                    c.setBackground(Color.WHITE);
                }
                setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
                return c;
            }
        });

        JScrollPane tableScrollPane = new JScrollPane(reportTable);
        tableScrollPane.setName("reportTableScrollPane");
        tableScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        tableScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tableScrollPane.setPreferredSize(new Dimension(950, 250)); // 保持原有高度

        // 图表区域 - 保持原有代码不变
        JPanel chartPanel = new JPanel();
        chartPanel.setBorder(BorderFactory.createTitledBorder("数据可视化"));
        chartPanel.setLayout(new GridLayout(1, 2, 10, 10));
        chartPanel.setPreferredSize(new Dimension(950, 300));
        chartPanel.setMinimumSize(new Dimension(400, 250));
        chartPanel.setName("chartPanel");

        // 将各面板添加到主面板 - 保持原有结构
        panel.add(controlPanel, BorderLayout.NORTH); // 添加统计范围面板在顶部
        panel.add(tableScrollPane, BorderLayout.CENTER);
        panel.add(chartPanel, BorderLayout.SOUTH);

        // 添加事件处理 - 保持原有功能
        generateButton.addActionListener(e -> {
            try {
                if (singleDayRadio.isSelected()) {
                    java.util.Date selectedDate = singleDayChooser.getDate();
                    if (selectedDate == null) {
                        JOptionPane.showMessageDialog(panel, "请选择一个日期");
                        return;
                    }
                    String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(selectedDate);

                    statusLabel.setText("正在加载单日数据...");
                    generateButton.setEnabled(false);

                    SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
                        @Override
                        protected List<Map<String, Object>> doInBackground() throws Exception {
                            return controller.getDailyBusinessReport(dateStr);
                        }

                        @Override
                        protected void done() {
                            try {
                                List<Map<String, Object>> reportData = get();
                                displayReportData(reportData, reportTable, tableModel, chartPanel);
                                statusLabel.setText("单日报表加载完成");
                                showTimeMessage("单日报表生成成功", "操作成功");
                            } catch (Exception ex) {
                                statusLabel.setText("加载失败: " + ex.getMessage());
                                JOptionPane.showMessageDialog(panel, "生成单日报表失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                                ex.printStackTrace();
                            } finally {
                                generateButton.setEnabled(true);
                            }
                        }
                    };
                    worker.execute();

                } else {
                    java.util.Date startDate = startDateChooser.getDate();
                    java.util.Date endDate = endDateChooser.getDate();
                    if (startDate == null || endDate == null) {
                        JOptionPane.showMessageDialog(panel, "请选择开始日期和结束日期");
                        return;
                    }
                    if (startDate.after(endDate)) {
                        JOptionPane.showMessageDialog(panel, "开始日期不能晚于结束日期");
                        return;
                    }
                    String startDateStr = new SimpleDateFormat("yyyy-MM-dd").format(startDate);
                    String endDateStr = new SimpleDateFormat("yyyy-MM-dd").format(endDate);

                    statusLabel.setText("正在加载日期范围数据...");
                    generateButton.setEnabled(false);

                    SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
                        @Override
                        protected List<Map<String, Object>> doInBackground() throws Exception {
                            return controller.getDateRangeBusinessReport(startDateStr, endDateStr);
                        }

                        @Override
                        protected void done() {
                            try {
                                List<Map<String, Object>> reportData = get();
                                displayReportData(reportData, reportTable, tableModel, chartPanel);
                                statusLabel.setText("日期范围报表加载完成");
                                showTimeMessage("日期范围报表生成成功", "操作成功");
                            } catch (Exception ex) {
                                statusLabel.setText("加载失败: " + ex.getMessage());
                                JOptionPane.showMessageDialog(panel, "生成日期范围报表失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                                ex.printStackTrace();
                            } finally {
                                generateButton.setEnabled(true);
                            }
                        }
                    };
                    worker.execute();
                }
            } catch (Exception ex) {
                statusLabel.setText("错误: " + ex.getMessage());
                JOptionPane.showMessageDialog(panel, "生成报表失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        });

        return panel;
    }

    /**
     * 创建菜品销售分析面板（完整交互式报表界面）
     *
     * @param singleDayChooser 保留参数（未使用，为接口兼容）
     * @param startDateChooser 保留参数（未使用，为接口兼容）
     * @param endDateChooser   保留参数（未使用，为接口兼容）
     * @param rangeRadio       保留参数（未使用，为接口兼容）
     * @param panelStatusLabel 状态标签（用于显示加载/错误信息）
     * @note 1. **核心功能模块**：
     * - 顶部控制区：年份/季度/类别/数量/图表类型选择器
     * - 中部数据表：菜品销售明细（编号/名称/销量/销售额等）
     * - 底部图表区：动态生成柱状图或饼图（双图并列）
     * 2. **智能交互设计**：
     * - 年份输入框支持手动输入+实时验证（1990-当前年+1）
     * - 图表类型实时切换（柱状图/饼图）
     * - 数据量动态限制（全部/前10/25/50）
     * - 菜品类别筛选（A/B/C/D/全部）
     * 3. **数据加载优化**：
     * - 后台线程加载（SwingWorker）
     * - 加载中显示进度条
     * - 空数据友好提示
     * 4. **异常处理**：
     * - 无效年份输入实时标红
     * - 数据库错误弹窗提示
     * - 图表生成失败保留界面
     * 5. **布局细节**：
     * - 表格列宽精确控制
     * - 图表区域可滚动（适应大尺寸）
     * - 响应式边距（BorderLayout+GridLayout组合）
     * 6. **典型使用流程**：
     * ① 选择2024年Q2数据 → ② 筛选B类(饮料) →
     * ③ 限制前25项 → ④ 切换饼图查看占比
     * @warning 1. 依赖controller.getQuarterlyDishSalesReport()实现
     * 2. 要求panelStatusLabel非空（避免NPE）
     * 3. 初始状态显示引导文本（需点击加载）
     */
    private JPanel createDishAnalysisPanel(JDateChooser singleDayChooser, JDateChooser startDateChooser,
                                           JDateChooser endDateChooser, JRadioButton rangeRadio,
                                           JLabel panelStatusLabel) {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 1. 顶部控制面板
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        controlPanel.setBorder(BorderFactory.createEtchedBorder());

        // 年份选择 - 修复：允许手动输入
        JPanel yearPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        yearPanel.add(new JLabel("年份:"));

        // 创建可编辑的年份组合框
        JComboBox<String> yearCombo = new JComboBox<>();
        yearCombo.setEditable(true);

        // 添加年份选项
        List<String> years = controller.getAvailableYearsForDishSales();
        for (String year : years) {
            yearCombo.addItem(year);
        }

        // 添加当前年份（如果不在列表中）
        String currentYear = String.valueOf(java.time.LocalDate.now().getYear());
        if (!years.contains(currentYear)) {
            yearCombo.addItem(currentYear);
        }

        // 设置默认选择
        yearCombo.setSelectedItem(currentYear);
        yearCombo.setName("yearCombo");
        yearCombo.setPreferredSize(new Dimension(100, 25));

        // 添加输入验证
        JTextField yearEditor = (JTextField) yearCombo.getEditor().getEditorComponent();
        yearEditor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String input = yearEditor.getText();
                if (!input.isEmpty()) {
                    try {
                        int year = Integer.parseInt(input);
                        if (year < 1990 || year > java.time.LocalDate.now().getYear() + 1) {
                            yearEditor.setForeground(Color.RED);
                        } else {
                            yearEditor.setForeground(Color.BLACK);
                        }
                    } catch (NumberFormatException ex) {
                        yearEditor.setForeground(Color.RED);
                    }
                }
            }
        });

        yearPanel.add(yearCombo);

        // 季度选择
        JPanel quarterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        quarterPanel.add(new JLabel("季度:"));
        JComboBox<String> quarterCombo = new JComboBox<>(new String[]{"Q1", "Q2", "Q3", "Q4"});
        quarterCombo.setSelectedItem("Q" + ((java.time.LocalDate.now().getMonthValue() - 1) / 3 + 1));
        quarterCombo.setName("quarterCombo");
        quarterCombo.setPreferredSize(new Dimension(80, 25));
        quarterPanel.add(quarterCombo);

        // 新增：添加数量选择器
        JPanel limitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        limitPanel.add(new JLabel("显示数量:"));
        String[] limits = {"全部", "前10", "前25", "前50"};
        JComboBox<String> limitCombo = new JComboBox<>(limits);
        limitCombo.setSelectedIndex(0); // 默认"全部"
        limitCombo.setName("limitCombo");
        limitCombo.setPreferredSize(new Dimension(100, 25));
        limitPanel.add(limitCombo);

        // 新增：图表类型选择
        JPanel chartTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        chartTypePanel.add(new JLabel("图表类型:"));
        ButtonGroup chartTypeGroup = new ButtonGroup();
        JRadioButton barChartRadio = new JRadioButton("柱状图", true); // 默认选中柱状图
        JRadioButton pieChartRadio = new JRadioButton("扇形图", false);
        chartTypeGroup.add(barChartRadio);
        chartTypeGroup.add(pieChartRadio);
        chartTypePanel.add(barChartRadio);
        chartTypePanel.add(pieChartRadio);

        // 分类选择器 - 新增
        JPanel categoryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        categoryPanel.add(new JLabel("类别:"));
        String[] categories = {"全部", "A", "B", "C", "D"}; // A=特色食物, B=饮料, C=小炒, D=套餐
        JComboBox<String> categoryCombo = new JComboBox<>(categories);
        categoryCombo.setSelectedIndex(0); // 默认"全部"
        categoryCombo.setName("categoryCombo");
        categoryCombo.setPreferredSize(new Dimension(100, 25));
        categoryPanel.add(categoryCombo);

        // 加载按钮
        JButton loadButton = new JButton("加载数据");
        loadButton.setPreferredSize(new Dimension(100, 30));
        loadButton.setFont(new Font("微软雅黑", Font.BOLD, 12));

        controlPanel.add(yearPanel);
        controlPanel.add(quarterPanel);
        controlPanel.add(limitPanel); // 添加数量选择器
        controlPanel.add(chartTypePanel); // 加入图表类型选择
        controlPanel.add(categoryPanel); // 添加分类选择器到控制面板
        controlPanel.add(loadButton);

        // 2. 菜品数据表格
        String[] dishColumns = {"菜品编号", "菜品名称", "销售数量", "销售额(元)", "平均单价(元)", "销售天数"};
        DefaultTableModel dishTableModel = new DefaultTableModel(dishColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable dishTable = new JTable(dishTableModel);
        dishTable.setRowHeight(25);
        dishTable.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        dishTable.getTableHeader().setFont(new Font("微软雅黑", Font.BOLD, 12));
        dishTable.setFillsViewportHeight(true);
        dishTable.setName("dishTable");

        // 设置表格列宽
        dishTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        dishTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        dishTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        dishTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        dishTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        dishTable.getColumnModel().getColumn(5).setPreferredWidth(80);

        // 为表格添加滚动支持
        JScrollPane dishScrollPane = new JScrollPane(dishTable);
        dishScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        dishScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        dishScrollPane.setPreferredSize(new Dimension(950, 250)); // 增加高度
        dishScrollPane.setName("dishScrollPane");

        // 3. 菜品图表区域 - 明确创建并命名
        JPanel chartPanel = new JPanel();
        chartPanel.setBorder(BorderFactory.createTitledBorder("销售趋势"));
        chartPanel.setLayout(new GridLayout(1, 2, 10, 10));
        chartPanel.setPreferredSize(new Dimension(950, 600)); // 增加高度到600
        chartPanel.setMinimumSize(new Dimension(400, 400)); // 增加最小高度
        chartPanel.setName("dishChartPanel"); // 修复：使用明确的名称，防止找不到

        // 初始化图表区域
        initializeChartPanel(chartPanel);

        // 为图表区域添加滚动支持 - 修复：确保滚动功能正常
        JScrollPane chartScrollPane = new JScrollPane(chartPanel);
        chartScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chartScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        chartScrollPane.setName("chartScrollPane");
        // 设置滚动面板的最小尺寸，确保图表可以放大
        chartScrollPane.setMinimumSize(new Dimension(900, 500));

        // 4. 组装数据面板
        JPanel dataPanel = new JPanel(new BorderLayout(10, 10));
        dataPanel.add(dishScrollPane, BorderLayout.NORTH); // 表格放在上方

        // 将图表滚动面板添加到数据面板 - 使用JScrollPane确保可滚动
        dataPanel.add(chartScrollPane, BorderLayout.CENTER); // 图表区域放在中间，可扩展

        // 设置数据面板的最小和首选大小，以适应滚动
        dataPanel.setPreferredSize(new Dimension(980, 750)); // 增加高度
        dataPanel.setMinimumSize(new Dimension(900, 700));

        panel.add(controlPanel, BorderLayout.NORTH);

        // 将数据面板放入滚动面板，以支持整个内容区域的滚动
        JScrollPane mainScrollPane = new JScrollPane(dataPanel);
        mainScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mainScrollPane.setPreferredSize(new Dimension(1000, 800)); // 增加滚动面板的首选尺寸
        mainScrollPane.getViewport().setBackground(Color.WHITE);

        // 设置滚动面板的边框和样式
        mainScrollPane.setBorder(BorderFactory.createEmptyBorder());
        mainScrollPane.getVerticalScrollBar().setUnitIncrement(20);
        mainScrollPane.getHorizontalScrollBar().setUnitIncrement(20);

        panel.add(mainScrollPane, BorderLayout.CENTER);

        // 5. 添加事件处理
        loadButton.addActionListener(e -> {
            try {
                int year = Integer.parseInt((String) yearCombo.getSelectedItem());
                String quarter = (String) quarterCombo.getSelectedItem();
                String category = (String) categoryCombo.getSelectedItem();
                String limit = (String) limitCombo.getSelectedItem(); // 获取数量选择
                boolean isBarChart = barChartRadio.isSelected(); // 获取当前图表类型

                if (panelStatusLabel != null) {
                    panelStatusLabel.setText("正在加载" + (category.equals("全部") ? "" : category + "类") + "菜品销售数据...");
                }

                // 添加加载指示器
                JProgressBar progressBar = new JProgressBar();
                progressBar.setIndeterminate(true);
                JPanel progressPanel = new JPanel(new BorderLayout());
                progressPanel.add(new JLabel("正在加载数据..."), BorderLayout.CENTER);
                progressPanel.add(progressBar, BorderLayout.SOUTH);

                // 替换图表区域
                chartPanel.removeAll();
                chartPanel.add(progressPanel);
                chartPanel.revalidate();
                chartPanel.repaint();

                SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
                    @Override
                    protected List<Map<String, Object>> doInBackground() {
                        return controller.getQuarterlyDishSalesReport(year, quarter, category);
                    }

                    @Override
                    protected void done() {
                        try {
                            List<Map<String, Object>> reportData = get();
                            if (reportData.isEmpty()) {
                                JOptionPane.showMessageDialog(panel,
                                        "未找到" + year + "年" + quarter +
                                                (!"全部".equals(category) ? " " + category + "类" : "") +
                                                "的销售数据",
                                        "提示", JOptionPane.INFORMATION_MESSAGE);
                                if (panelStatusLabel != null) {
                                    panelStatusLabel.setText("未找到相关数据");
                                }
                                // 恢复图表区域
                                initializeChartPanel(chartPanel);
                                return;
                            }

                            // 更新表格数据
                            displayDishSalesData(reportData, dishTable, dishTableModel, chartPanel);

                            // 根据选择的数量，确定要显示的条目数
                            int maxItems = getMaxItemsFromLimit(limit, reportData.size());

                            // 更新图表，根据选择的图表类型
                            updateDishSalesChart(reportData, chartPanel, maxItems, isBarChart);

                            if (panelStatusLabel != null) {
                                String categoryText = category.equals("全部") ? "" : category + "类";
                                panelStatusLabel.setText(categoryText + "菜品销售数据加载完成 (" + reportData.size() + "条)");
                            }
                        } catch (Exception ex) {
                            if (panelStatusLabel != null) {
                                panelStatusLabel.setText("加载失败: " + ex.getMessage());
                            }
                            JOptionPane.showMessageDialog(panel, "加载菜品销售数据失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                            ex.printStackTrace();
                            // 恢复图表区域
                            initializeChartPanel(chartPanel);
                        }
                    }
                };
                worker.execute();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "无效的年份格式，请输入4位数字年份", "输入错误", JOptionPane.ERROR_MESSAGE);
                yearEditor.setForeground(Color.RED);
            }
        });

        // 6. 为图表类型单选按钮添加事件监听器
        ActionListener chartTypeListener = e -> {
            if (dishTableModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(panel, "请先加载数据", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            try {
                int year = Integer.parseInt((String) yearCombo.getSelectedItem());
                String quarter = (String) quarterCombo.getSelectedItem();
                String category = (String) categoryCombo.getSelectedItem();
                String limit = (String) limitCombo.getSelectedItem();
                boolean isBarChart = barChartRadio.isSelected();

                // 重新获取数据
                List<Map<String, Object>> reportData = controller.getQuarterlyDishSalesReport(year, quarter, category);

                if (reportData.isEmpty()) {
                    JOptionPane.showMessageDialog(panel, "没有找到可显示的数据", "提示", JOptionPane.INFORMATION_MESSAGE);
                    return;
                }

                // 根据选择的数量，确定要显示的条目数
                int maxItems = getMaxItemsFromLimit(limit, reportData.size());

                // 更新图表
                updateDishSalesChart(reportData, chartPanel, maxItems, isBarChart);

                if (panelStatusLabel != null) {
                    String chartTypeText = isBarChart ? "柱状图" : "扇形图";
                    panelStatusLabel.setText("已切换到" + chartTypeText + "显示");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(panel, "更新图表失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                if (panelStatusLabel != null) {
                    panelStatusLabel.setText("图表更新失败: " + ex.getMessage());
                }
            }
        };

        barChartRadio.addActionListener(chartTypeListener);
        pieChartRadio.addActionListener(chartTypeListener);

        // 7. 为数量选择器添加事件监听器
        limitCombo.addActionListener(e -> {
            if (dishTableModel.getRowCount() == 0) {
                return;
            }

            try {
                int year = Integer.parseInt((String) yearCombo.getSelectedItem());
                String quarter = (String) quarterCombo.getSelectedItem();
                String category = (String) categoryCombo.getSelectedItem();
                String limit = (String) limitCombo.getSelectedItem();
                boolean isBarChart = barChartRadio.isSelected();

                // 重新获取数据
                List<Map<String, Object>> reportData = controller.getQuarterlyDishSalesReport(year, quarter, category);

                if (reportData.isEmpty()) {
                    return;
                }

                // 根据选择的数量，确定要显示的条目数
                int maxItems = getMaxItemsFromLimit(limit, reportData.size());

                // 仅更新图表
                updateDishSalesChart(reportData, chartPanel, maxItems, isBarChart);

                if (panelStatusLabel != null) {
                    panelStatusLabel.setText("已更新显示数量为 " + limit);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // 8. 为分类选择器添加事件监听器
        categoryCombo.addActionListener(e -> {
            // 自动加载新分类的数据
            loadButton.doClick();
        });

        return panel;
    }

    /**
     * 根据显示限制字符串计算最大项目数（安全边界处理）
     *
     * @param limit      限制选项（"前10"/"前25"/"前50"/"全部"）
     * @param totalItems 数据集总项目数
     * @return 实际显示数量（不超过totalItems）
     */
    private int getMaxItemsFromLimit(String limit, int totalItems) {
        switch (limit) {
            case "前10":
                return Math.min(10, totalItems);
            case "前25":
                return Math.min(25, totalItems);
            case "前50":
                return Math.min(50, totalItems);
            default: // "全部"
                return totalItems;
        }
    }


    /**
     * 初始化图表区域为提示状态（无数据时显示引导文本）
     *
     * @param chartPanel 需要初始化的图表面板
     */
    private void initializeChartPanel(JPanel chartPanel) {
        chartPanel.removeAll();

        JLabel placeholderLabel = new JLabel("请选择年份和季度加载销售数据", SwingConstants.CENTER);
        placeholderLabel.setFont(new Font("微软雅黑", Font.ITALIC, 14));
        placeholderLabel.setForeground(Color.GRAY);

        chartPanel.add(placeholderLabel);
        chartPanel.revalidate();
        chartPanel.repaint();
    }


    /**
     * 動態更新菜品銷售圖表（支持柱狀圖/餅圖雙視圖）
     *
     * @param reportData 報表數據列表，每項包含：itemName, total_revenue, total_quantity
     * @param chartPanel 目標圖表面板（將被清空並填充新內容）
     * @param maxItems   顯示項目數量限制（0/負數=全部，>0=前N項，999+視為全部）
     * @param isBarChart true=柱狀圖（數值精確比較），false=餅圖（比例直觀展示）
     * @note 1. **智能數據處理**：
     * - 自動截斷長菜名（>8字用"..."）
     * - 餅圖模式下合併低占比項目（<2%或超出前10名）
     * - 柱狀圖嚴格Y軸≥0，餅圖移除百分比標籤
     * 2. **雙圖表布局**：
     * - 左側：銷售額排名/占比
     * - 右側：銷量排名/占比
     * - 標題動態顯示"前N項"或"全部"
     * 3. **性能保障**：
     * - 限制餅圖最多顯示10+1（其他）個扇區
     * - 自動跳過空數據集
     * 4. **異常恢復**：
     * - 圖表生成失敗時顯示友好錯誤提示
     * - 保留原面板結構不崩潰
     * 5. **典型場景**：
     * - 選擇季度報表後即時生成可視化
     * - 切換菜系類別時動態刷新
     * - 用戶調整顯示數量（下拉框）時更新
     *
     */
    private void updateDishSalesChart(List<Map<String, Object>> reportData, JPanel chartPanel, int maxItems, boolean isBarChart) {
        chartPanel.removeAll();
        chartPanel.setLayout(new GridLayout(1, 2, 10, 10));

        try {
            // 确保maxItems不会超过数据集大小
            int itemsToDisplay = Math.min(maxItems, reportData.size());
            String displayText = (maxItems == reportData.size() || maxItems >= 999) ? "全部" : "前" + maxItems;

            if (isBarChart) {
                // 柱状图逻辑
                // 准备数据集
                DefaultCategoryDataset salesDataset = new DefaultCategoryDataset();
                DefaultCategoryDataset quantityDataset = new DefaultCategoryDataset();

                for (int i = 0; i < itemsToDisplay; i++) {
                    Map<String, Object> item = reportData.get(i);
                    String itemName = (String) item.get("itemName");

                    // 缩短长名称
                    if (itemName.length() > 8) {
                        itemName = itemName.substring(0, 8) + "...";
                    }

                    double totalRevenue = (double) item.get("total_revenue");
                    int totalQuantity = (int) item.get("total_quantity");

                    // 添加到销售额数据集
                    salesDataset.addValue(totalRevenue, "销售额", itemName);

                    // 添加到销量数据集
                    quantityDataset.addValue(totalQuantity, "销量", itemName);
                }

                // 创建销售额图表
                JFreeChart salesChart = ChartFactory.createBarChart(
                        "销售额排名 (" + displayText + ")",
                        "菜品",
                        "销售额 (元)",
                        salesDataset,
                        PlotOrientation.VERTICAL,
                        false,
                        true,
                        false
                );

                // 创建销量图表
                JFreeChart quantityChart = ChartFactory.createBarChart(
                        "销量排名 (" + displayText + ")",
                        "菜品",
                        "销售数量",
                        quantityDataset,
                        PlotOrientation.VERTICAL,
                        false,
                        true,
                        false
                );
                // 自定义图表样式
                customizeChartStyle(salesChart, new Color(41, 128, 185)); // 蓝色
                customizeChartStyle(quantityChart, new Color(39, 174, 96)); // 绿色

                // 创建图表面板
                ChartPanel salesChartPanel = new ChartPanel(salesChart);
                salesChartPanel.setMouseWheelEnabled(true);

                ChartPanel quantityChartPanel = new ChartPanel(quantityChart);
                quantityChartPanel.setMouseWheelEnabled(true);

                // 添加到图表面板
                chartPanel.add(salesChartPanel);
                chartPanel.add(quantityChartPanel);
            } else {
                // 扇形图（饼图）逻辑
                if (reportData.isEmpty()) {
                    throw new RuntimeException("没有可用的销售数据");
                }

                // 计算总销售额和总销量
                double totalRevenue = 0.0;
                int totalQuantity = 0;

                // 只计算显示范围内的数据
                for (int i = 0; i < itemsToDisplay; i++) {
                    Map<String, Object> item = reportData.get(i);
                    totalRevenue += (double) item.get("total_revenue");
                    totalQuantity += (int) item.get("total_quantity");
                }

                // 创建数据集
                DefaultPieDataset salesDataset = new DefaultPieDataset();
                DefaultPieDataset quantityDataset = new DefaultPieDataset();

                // 仅显示有意义的切片 - 按销售额排序
                List<Map<String, Object>> sortedData = new ArrayList<>(reportData.subList(0, itemsToDisplay));
                sortedData.sort((a, b) -> Double.compare(
                        (double) b.get("total_revenue"),
                        (double) a.get("total_revenue")
                ));

                // 计算要显示的主要项目数量（最多10个，确保饼图不杂乱）
                int primaryItemsCount = Math.min(10, sortedData.size());
                double otherRevenue = 0.0;
                int otherQuantity = 0;
                int otherCount = 0;

                for (int i = 0; i < sortedData.size(); i++) {
                    Map<String, Object> item = sortedData.get(i);
                    String itemName = (String) item.get("itemName");

                    // 缩短长名称
                    if (itemName.length() > 12) {
                        itemName = itemName.substring(0, 12) + "...";
                    }

                    double revenue = (double) item.get("total_revenue");
                    int quantity = (int) item.get("total_quantity");

                    double revenuePercent = (revenue / totalRevenue) * 100;
                    double quantityPercent = totalQuantity > 0 ? ((double) quantity / totalQuantity) * 100 : 0;

                    // 只显示主要项目或占比大于2%的项目
                    if (i < primaryItemsCount - 1 || revenuePercent >= 2) {
                        // 添加到销售额数据集
                        salesDataset.setValue(itemName + " (" + String.format("%.1f%%", revenuePercent) + ")", revenue);

                        // 添加到销量数据集
                        quantityDataset.setValue(itemName + " (" + String.format("%.1f%%", quantityPercent) + ")", quantity);
                    } else {
                        otherRevenue += revenue;
                        otherQuantity += quantity;
                        otherCount++;
                    }
                }

                // 添加"其他"类别
                if (otherCount > 0) {
                    double otherRevenuePercent = (otherRevenue / totalRevenue) * 100;
                    double otherQuantityPercent = totalQuantity > 0 ? ((double) otherQuantity / totalQuantity) * 100 : 0;

                    if (otherRevenue > 0) {
                        salesDataset.setValue("其他 (" + otherCount + "项, " + String.format("%.1f%%", otherRevenuePercent) + ")", otherRevenue);
                    }

                    if (otherQuantity > 0) {
                        quantityDataset.setValue("其他 (" + otherCount + "项, " + String.format("%.1f%%", otherQuantityPercent) + ")", otherQuantity);
                    }
                }

                // 创建销售额饼图
                JFreeChart salesChart = ChartFactory.createPieChart(
                        "销售额占比 (" + displayText + ")",
                        salesDataset,
                        true,  // 显示图例
                        true,  // 生成工具提示
                        false  // 生成URLs
                );
                // 立即应用中文字体
                applyChineseFontToChart(salesChart);

                // 创建销量饼图
                JFreeChart quantityChart = ChartFactory.createPieChart(
                        "销量占比 (" + displayText + ")",
                        quantityDataset,
                        true,
                        true,
                        false
                );
                // 立即应用中文字体
                applyChineseFontToChart(quantityChart);
                // 自定义饼图样式
                customizePieChart(salesChart, "销售额");
                customizePieChart(quantityChart, "销量");

                // 创建图表面板
                ChartPanel salesChartPanel = new ChartPanel(salesChart);
                salesChartPanel.setMouseWheelEnabled(true);

                ChartPanel quantityChartPanel = new ChartPanel(quantityChart);
                quantityChartPanel.setMouseWheelEnabled(true);

                // 添加到图表面板
                chartPanel.add(salesChartPanel);
                chartPanel.add(quantityChartPanel);
            }

            chartPanel.revalidate();
            chartPanel.repaint();

        } catch (Exception e) {
            // 图表生成失败时显示错误
            JLabel errorLabel = new JLabel("图表生成失败: " + e.getMessage(), SwingConstants.CENTER);
            errorLabel.setForeground(Color.RED);
            errorLabel.setFont(new Font("微软雅黑", Font.BOLD, 12));
            chartPanel.add(errorLabel);

            chartPanel.revalidate();
            chartPanel.repaint();

            e.printStackTrace();
        }
    }


    /**
     * 定制饼图样式（移除百分比，仅显示"名称:数值"）
     *
     * @param type 图表类型（如"销售额"触发首项弹出）
     */
    private void customizePieChart(JFreeChart chart, String type) {
        PiePlot plot = (PiePlot) chart.getPlot();

        // 获取支持中文的字体
        Font chineseFontRegular = getChineseFont(12);
        Font chineseFontBold = getChineseFont(14);
        chineseFontBold = chineseFontBold.deriveFont(Font.BOLD); // 设置为粗体

        // 设置背景
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setShadowGenerator(null);

        // ====== 彻底修复：完全移除百分比 ======
        // 方法1：使用自定义标签生成器（最可靠）
        plot.setLabelGenerator(new PieSectionLabelGenerator() {
            @Override
            public String generateSectionLabel(PieDataset dataset, Comparable key) {
                // 只返回"名称: 数值"格式
                Number value = dataset.getValue(key);
                return key.toString() + ": " + value.intValue();
            }

            @Override
            public AttributedString generateAttributedSectionLabel(PieDataset dataset, Comparable key) {
                // 返回空的AttributedString，因为不需要特殊格式
                return null;
            }
        });

        // 方法2：作为备选（如果方法1不工作）
        // plot.setLabelGenerator(new StandardPieSectionLabelGenerator(
        //     "{0}: {1}",
        //     NumberFormat.getIntegerInstance(),
        //     new DecimalFormat("0%") // 使用一个不会实际显示的格式
        // ));
        // ====== 修复结束 ======

        // 设置标签字体
        plot.setLabelFont(chineseFontRegular);

        // 显示标签
        plot.setLabelLinksVisible(true);
        plot.setLabelBackgroundPaint(Color.WHITE);
        plot.setLabelOutlinePaint(Color.GRAY);
        plot.setLabelShadowPaint(new Color(0, 0, 0, 0)); // 透明阴影

        // 设置起始角度
        plot.setStartAngle(90);

        // 设置方向 - 顺时针
        plot.setDirection(Rotation.CLOCKWISE);

        // 设置标签链接样式
        plot.setLabelLinkStyle(PieLabelLinkStyle.STANDARD);
        plot.setLabelLinkPaint(Color.DARK_GRAY);
        plot.setLabelLinkStroke(new BasicStroke(1.0f));

        // 设置自动弹出主要部分 - 仅对销售额饼图应用
        if ("销售额".equals(type) && plot.getDataset().getItemCount() > 0) {
            // 只弹出第一个扇区（最大的部分）
            Comparable<?> firstKey = plot.getDataset().getKey(0);
            if (firstKey instanceof String) {
                plot.setExplodePercent((String) firstKey, 0.10);
            }
        }

        // 设置图例
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setItemFont(chineseFontRegular.deriveFont(10.0f));
            legend.setFrame(BlockBorder.NONE);
            legend.setPosition(RectangleEdge.BOTTOM);
        }

        // 设置标题字体
        TextTitle title = chart.getTitle();
        if (title != null) {
            title.setFont(chineseFontBold);
        }

        // 为每个扇区设置颜色 - 确保只使用字符串键
        int colorIndex = 0;
        Color[] colors = {
                new Color(228, 41, 50),    // 红色
                new Color(35, 154, 223),   // 蓝色
                new Color(50, 168, 82),    // 绿色
                new Color(142, 68, 173),   // 紫色
                new Color(243, 156, 18),   // 橙色
                new Color(127, 140, 141),  // 灰色
                new Color(44, 62, 80),     // 深灰
                new Color(211, 84, 0),     // 深橙
                new Color(30, 130, 76),    // 深绿
                new Color(218, 129, 225)   // 粉色
        };

        for (int i = 0; i < plot.getDataset().getItemCount(); i++) {
            Comparable<?> key = plot.getDataset().getKey(i);
            if (key instanceof String) {
                plot.setSectionPaint((String) key, colors[colorIndex % colors.length]);
                colorIndex++;
            }
        }

        // 设置图表边距，确保中文标签有足够显示空间
        chart.setPadding(new RectangleInsets(10, 10, 10, 10));

        // 设置绘图区域边距
        plot.setInsets(new RectangleInsets(10, 10, 10, 10));
    }

    /**
     * 定制条形图样式（Y轴严格≥0，自动计算上限）
     *
     * @param barColor 主条形颜色
     */
    private void customizeChartStyle(JFreeChart chart, Color barColor) {
        // 设置标题字体
        chart.getTitle().setFont(new Font("微软雅黑", Font.BOLD, 14));

        // 增加图表边距，为X轴标签提供更多空间
        chart.setPadding(new RectangleInsets(10, 10, 60, 10));

        // 获取图表区域
        CategoryPlot plot = chart.getCategoryPlot();

        // 设置背景
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(236, 240, 241));
        plot.setOutlinePaint(Color.LIGHT_GRAY);

        // 设置条形图渲染器
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, barColor);
        renderer.setItemMargin(0.1); // 条形之间的间距

        // 设置坐标轴字体
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelFont(new Font("微软雅黑", Font.PLAIN, 8));
        domainAxis.setLabelFont(new Font("微软雅黑", Font.BOLD, 11));
        domainAxis.setCategoryLabelPositions(
                CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 4.0)
        );
        domainAxis.setCategoryLabelPositionOffset(10);

        // ====== 关键修改：确保Y轴在任何情况下都不会显示负数 ======
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setLabelFont(new Font("微软雅黑", Font.BOLD, 12));

        // 1. 完全禁用自动范围 - 这是核心设置
        rangeAxis.setAutoRange(false);

        // 2. 设置Y轴下限为0 - 确保不会显示负数
        rangeAxis.setLowerBound(0.0);

        // 3. 计算当前数据集中的最大值
        double maxValue = 0;
        CategoryDataset dataset = plot.getDataset();
        if (dataset != null) {
            int seriesCount = dataset.getRowCount();
            int categoryCount = dataset.getColumnCount();
            for (int i = 0; i < seriesCount; i++) {
                for (int j = 0; j < categoryCount; j++) {
                    Number value = dataset.getValue(i, j);
                    if (value != null && value.doubleValue() > maxValue) {
                        maxValue = value.doubleValue();
                    }
                }
            }
        }

        // 4. 设置合理的上限 - 至少为1，确保即使没有数据也有合理范围
        double upperBound;
        if (maxValue == 0) {
            upperBound = 100.0; // 默认上限
        } else {
            // 添加20%的上边距，让图表顶部有空间
            upperBound = maxValue * 1.2;
            // 确保上限至少为1
            upperBound = Math.max(upperBound, 1.0);
        }
        rangeAxis.setUpperBound(upperBound);

        // 5. 设置刻度单位为整数
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        // 6. 移除Y轴下方边距，确保下限严格为0
        rangeAxis.setLowerMargin(0.0);

        // 7. 设置上边界距，让顶部有适当空间
        rangeAxis.setUpperMargin(0.1);

        // 8. 强制应用这些设置
        rangeAxis.configure();
        // ====== 结束关键修改 ======

        // 显示数据标签
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator());
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelFont(new Font("微软雅黑", Font.PLAIN, 9));
        renderer.setDefaultItemLabelPaint(Color.DARK_GRAY);

        // 为Plot区域也增加底部边距
        plot.setInsets(new RectangleInsets(5, 5, 20, 5));
    }

    /**
     * 从面板获取报表表格
     *
     * @note 通过滚动窗格名称"reportTableScrollPane"查找
     */
    private JTable getTableFromPanel(JPanel panel) {
        Component comp = findComponentByName(panel, "reportTableScrollPane");
        if (comp instanceof JScrollPane) {
            JScrollPane scrollPane = (JScrollPane) comp;
            JViewport viewport = scrollPane.getViewport();
            if (viewport != null && viewport.getView() instanceof JTable) {
                return (JTable) viewport.getView();
            }
        }
        return null;
    }

    /**
     * 从面板获取菜品表格
     *
     * @note 通过组件名称"dishTable"直接获取
     */
    private JTable getDishTableFromPanel(JPanel panel) {
        Component comp = findComponentByName(panel, "dishTable");
        return (JTable) comp;
    }


    /**
     * 显示菜品销售报表（表格+双图表）
     *
     * @note 仅展示前10个热门菜品，自动计算总计
     */
    private void displayDishSalesData(List<Map<String, Object>> reportData, JTable dishTable,
                                      DefaultTableModel tableModel, JPanel chartPanel) {
        tableModel.setRowCount(0); // 清空表格

        if (reportData == null || reportData.isEmpty()) {
            JOptionPane.showMessageDialog(this, "未找到相关菜品销售数据", "提示", JOptionPane.INFORMATION_MESSAGE);
            // 显示空白图表
            chartPanel.removeAll();
            JLabel noDataLabel = new JLabel("暂无数据可展示", SwingConstants.CENTER);
            noDataLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
            noDataLabel.setForeground(Color.GRAY);
            chartPanel.add(noDataLabel);
            chartPanel.revalidate();
            chartPanel.repaint();
            return;
        }

        double totalRevenue = 0.0;
        int totalQuantity = 0;

        // 准备图表数据
        DefaultCategoryDataset revenueDataset = new DefaultCategoryDataset();
        DefaultCategoryDataset quantityDataset = new DefaultCategoryDataset();

        // 填充表格
        for (Map<String, Object> data : reportData) {
            String itemCode = (String) data.get("itemCode");
            String itemName = (String) data.get("itemName");
            int quantity = ((Number) data.get("total_quantity")).intValue();
            double revenue = ((Number) data.get("total_revenue")).doubleValue();
            double avgPrice = revenue / quantity;
            int activeDays = ((Number) data.get("active_days")).intValue();

            Object[] row = {
                    itemCode,
                    itemName,
                    quantity,
                    String.format("%.2f", revenue),
                    String.format("%.2f", avgPrice),
                    activeDays
            };

            tableModel.addRow(row);

            // 累计总计
            totalRevenue += revenue;
            totalQuantity += quantity;

            // 为图表准备数据（只显示前10个菜品）
            if (tableModel.getRowCount() <= 10) {
                revenueDataset.addValue(revenue, "销售额", itemCode + " - " + itemName.substring(0, Math.min(8, itemName.length())));
                quantityDataset.addValue(quantity, "销售量", itemCode + " - " + itemName.substring(0, Math.min(8, itemName.length())));
            }
        }

        // 添加总计行
        Object[] totalRow = {
                "总计",
                "",
                totalQuantity,
                String.format("%.2f", totalRevenue),
                "",
                ""
        };
        tableModel.addRow(totalRow);

        // 创建图表
        createDishCharts(revenueDataset, quantityDataset, chartPanel);
    }

    /**
     * 生成菜品销售双图表（销售额/销售量排名）
     *
     * @note 适配中文并优化图表尺寸
     */
    private void createDishCharts(DefaultCategoryDataset revenueDataset,
                                  DefaultCategoryDataset quantityDataset,
                                  JPanel chartPanel) {
        chartPanel.removeAll();//可能是null的原因

        // 创建销售额图表
        JFreeChart revenueChart = ChartFactory.createBarChart(
                "热门菜品销售额排名",
                "菜品",
                "金额(元)",
                revenueDataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // 创建销售量图表
        JFreeChart quantityChart = ChartFactory.createBarChart(
                "热门菜品销售量排名",
                "菜品",
                "数量",
                quantityDataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // 修复中文乱码问题
        Font labelFont;
        Font titleFont;

        // 检查系统是否支持微软雅黑
        if (isFontAvailable("Microsoft YaHei")) {
            labelFont = new Font("Microsoft YaHei", Font.PLAIN, 12);
            titleFont = new Font("Microsoft YaHei", Font.BOLD, 14);
        } else {
            // 使用系统默认中文字体
            labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 14);
        }

        // 设置图表样式
        setChartStyle(revenueChart, labelFont, titleFont, "销售额");
        setChartStyle(quantityChart, labelFont, titleFont, "销售量");

        // 配置图表标签
        configureChartLabels(revenueChart, true);  // 货币格式
        configureChartLabels(quantityChart, false); // 普通数字格式

        // 设置图表大小
        ChartPanel revenuePanel = new ChartPanel(revenueChart);
        revenuePanel.setPreferredSize(new Dimension(450, 450)); // 增加高度
        revenuePanel.setMaximumSize(new Dimension(450, 600)); // 允许更大高度
        revenuePanel.setMouseWheelEnabled(true);

        ChartPanel quantityPanel = new ChartPanel(quantityChart);
        quantityPanel.setPreferredSize(new Dimension(450, 450)); // 增加高度
        quantityPanel.setMaximumSize(new Dimension(450, 600)); // 允许更大高度
        quantityPanel.setMouseWheelEnabled(true);

        chartPanel.add(revenuePanel);
        chartPanel.add(quantityPanel);
        chartPanel.revalidate();
        chartPanel.repaint();
    }

    /**
     * 导出菜品销售报表到Excel
     *
     * @note 自动格式化金额/数量列，失败时弹出错误
     */
    private void exportDishSalesToExcel(JTable table) {
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存菜品销售报表");
            fileChooser.setSelectedFile(new File("菜品销售报表_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".xlsx"));
            int userSelection = fileChooser.showSaveDialog(this);
            if (userSelection != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File fileToSave = fileChooser.getSelectedFile();
            String filePath = fileToSave.getAbsolutePath();
            if (!filePath.toLowerCase().endsWith(".xlsx")) {
                filePath += ".xlsx";
            }

            // 创建目录（如果不存在）
            File parentDir = fileToSave.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 使用Apache POI创建Excel
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                XSSFSheet sheet = workbook.createSheet("菜品销售报表");

                // 创建表头样式
                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                XSSFFont headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setColor(IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setBorderLeft(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);

                // 创建数据样式 - 金额列
                CellStyle currencyStyle = workbook.createCellStyle();
                currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
                currencyStyle.setBorderBottom(BorderStyle.THIN);
                currencyStyle.setBorderTop(BorderStyle.THIN);
                currencyStyle.setBorderLeft(BorderStyle.THIN);
                currencyStyle.setBorderRight(BorderStyle.THIN);

                // 创建数据样式 - 普通数字
                CellStyle numberStyle = workbook.createCellStyle();
                numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
                numberStyle.setBorderBottom(BorderStyle.THIN);
                numberStyle.setBorderTop(BorderStyle.THIN);
                numberStyle.setBorderLeft(BorderStyle.THIN);
                numberStyle.setBorderRight(BorderStyle.THIN);

                // 创建表头
                Row headerRow = sheet.createRow(0);
                TableModel model = table.getModel();
                for (int i = 0; i < model.getColumnCount(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(model.getColumnName(i));
                    cell.setCellStyle(headerStyle);
                }

                // 填充数据
                for (int i = 0; i < model.getRowCount(); i++) {
                    Row row = sheet.createRow(i + 1);
                    for (int j = 0; j < model.getColumnCount(); j++) {
                        Object value = model.getValueAt(i, j);
                        Cell cell = row.createCell(j);

                        // 设置边框
                        CellStyle borderStyle = workbook.createCellStyle();
                        borderStyle.setBorderBottom(BorderStyle.THIN);
                        borderStyle.setBorderTop(BorderStyle.THIN);
                        borderStyle.setBorderLeft(BorderStyle.THIN);
                        borderStyle.setBorderRight(BorderStyle.THIN);
                        cell.setCellStyle(borderStyle);

                        if (value == null || value.toString().trim().isEmpty()) {
                            cell.setCellValue("");
                            continue;
                        }

                        String cellValue = value.toString().trim();
                        cell.setCellValue(cellValue);

                        // 根据列类型设置格式
                        if (j == 3) { // 销售额列
                            try {
                                String cleanValue = cellValue.replaceAll("[^0-9.]", "");
                                if (!cleanValue.isEmpty()) {
                                    cell.setCellValue(Double.parseDouble(cleanValue));
                                    cell.setCellStyle(currencyStyle);
                                }
                            } catch (NumberFormatException e) {
                                // 保持字符串
                            }
                        } else if (j == 2 || j == 5) { // 销售数量和销售天数
                            try {
                                String cleanValue = cellValue.replaceAll("[^0-9]", "");
                                if (!cleanValue.isEmpty()) {
                                    cell.setCellValue(Integer.parseInt(cleanValue));
                                    cell.setCellStyle(numberStyle);
                                }
                            } catch (NumberFormatException e) {
                                // 保持字符串
                            }
                        } else if (j == 4) { // 平均单价
                            try {
                                String cleanValue = cellValue.replaceAll("[^0-9.]", "");
                                if (!cleanValue.isEmpty()) {
                                    cell.setCellValue(Double.parseDouble(cleanValue));
                                    cell.setCellStyle(currencyStyle);
                                }
                            } catch (NumberFormatException e) {
                                // 保持字符串
                            }
                        }
                    }
                }

                // 自动调整列宽
                for (int i = 0; i < model.getColumnCount(); i++) {
                    sheet.setColumnWidth(i, Math.min((int) (sheet.getColumnWidth(i) * 1.5), 5000));
                }

                // 保存文件
                try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                    workbook.write(fileOut);
                }

                JOptionPane.showMessageDialog(this, "报表已成功导出到:\n" + filePath, "导出成功", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "导出报表失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * 显示营业报表数据（表格+双图表）
     *
     * @note 自动计算总计并生成趋势图
     */
    private void displayReportData(List<Map<String, Object>> reportData, JTable reportTable,
                                   DefaultTableModel tableModel, JPanel chartPanel) {
        tableModel.setRowCount(0); // 清空表格

        if (reportData == null || reportData.isEmpty()) {
            JOptionPane.showMessageDialog(this, "未找到相关营业数据", "提示", JOptionPane.INFORMATION_MESSAGE);
            // 显示空白图表
            chartPanel.removeAll();
            JLabel noDataLabel = new JLabel("暂无数据可展示", SwingConstants.CENTER);
            noDataLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
            noDataLabel.setForeground(Color.GRAY);
            chartPanel.add(noDataLabel);
            chartPanel.revalidate();
            chartPanel.repaint();
            return;
        }

        double totalRevenue = 0.0;
        int totalCustomers = 0;
        int totalOrders = 0;

        // 准备图表数据
        DefaultCategoryDataset revenueDataset = new DefaultCategoryDataset();
        DefaultCategoryDataset customersDataset = new DefaultCategoryDataset();

        // 填充表格和计算总计
        for (Map<String, Object> data : reportData) {
            String date = (String) data.get("date");
            double revenue = (Double) data.get("revenue");
            int customers = (Integer) data.get("customers");
            int orderCount = (Integer) data.get("orderCount");
            double avgRevenuePerCustomer = customers > 0 ? revenue / customers : 0;

            Object[] row = {
                    date,
                    String.format("%.2f", revenue),
                    customers,
                    String.format("%.2f", avgRevenuePerCustomer),
                    orderCount
            };
            tableModel.addRow(row);

            // 累计总计
            totalRevenue += revenue;
            totalCustomers += customers;
            totalOrders += orderCount;

            // 为图表准备数据
            revenueDataset.addValue(revenue, "营业额", date);
            customersDataset.addValue(customers, "顾客数", date);
        }

        // 添加总计行
        if (reportData.size() > 1) {
            double avgRevenuePerCustomer = totalCustomers > 0 ? totalRevenue / totalCustomers : 0;
            Object[] totalRow = {
                    "总计",
                    String.format("%.2f", totalRevenue),
                    totalCustomers,
                    String.format("%.2f", avgRevenuePerCustomer),
                    totalOrders
            };
            tableModel.addRow(totalRow);
        }

        // 生成图表 - 修复：传递正确的数据集
        createCharts(revenueDataset, customersDataset, chartPanel);
    }

    /**
     * 创建双图表面板（营业额/顾客数量趋势）
     *
     * @note 自动处理中文字体和响应式布局
     */
    private void createCharts(DefaultCategoryDataset revenueDataset,
                              DefaultCategoryDataset customersDataset,
                              JPanel chartPanel) {
        chartPanel.removeAll();

        // 创建营业额图表
        JFreeChart revenueChart = ChartFactory.createBarChart(
                "每日营业额趋势",  // 更清晰的标题
                "日期",
                "金额(元)",
                revenueDataset,
                PlotOrientation.VERTICAL,
                true,  // 显示图例
                true,  // 生成工具提示
                false  // 生成URL
        );

        // 创建顾客数量图表
        JFreeChart customersChart = ChartFactory.createBarChart(
                "每日顾客数量统计",  // 更清晰的标题
                "日期",
                "人数",
                customersDataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        // 修复中文乱码问题 - 跨平台兼容方案
        Font labelFont;
        Font titleFont;

        // 检查系统是否支持微软雅黑
        if (isFontAvailable("Microsoft YaHei")) {
            labelFont = new Font("Microsoft YaHei", Font.PLAIN, 12);
            titleFont = new Font("Microsoft YaHei", Font.BOLD, 14);
        } else {
            // 使用系统默认中文字体
            labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 14);
        }

        // 设置营业额图表样式
        setChartStyle(revenueChart, labelFont, titleFont, "营业额");

        // 设置顾客数量图表样式
        setChartStyle(customersChart, labelFont, titleFont, "顾客数量");

        // 兼容最新JFreeChart版本的标签设置
        configureChartLabels(revenueChart, true);  // true表示货币格式
        configureChartLabels(customersChart, false); // false表示整数格式

        // 设置图表大小
        ChartPanel revenuePanel = new ChartPanel(revenueChart);
        revenuePanel.setPreferredSize(new Dimension(450, 300));
        revenuePanel.setMouseWheelEnabled(true); // 启用滚轮缩放

        ChartPanel customersPanel = new ChartPanel(customersChart);
        customersPanel.setPreferredSize(new Dimension(450, 300));
        customersPanel.setMouseWheelEnabled(true);

        chartPanel.add(revenuePanel);
        chartPanel.add(customersPanel);
        chartPanel.revalidate();
        chartPanel.repaint();
    }

    /**
     * 配置图表样式（颜色/字体/网格线）
     *
     * @param seriesName 决定主色调（"营业额"=蓝/"顾客数量"=绿）
     */
    private void setChartStyle(JFreeChart chart, Font labelFont, Font titleFont, String seriesName) {
        // 设置标题
        TextTitle title = chart.getTitle();
        if (title != null) {
            title.setFont(titleFont);
            title.setPaint(new Color(51, 51, 51)); // 深灰色标题
        }

        // 获取绘图区
        CategoryPlot plot = (CategoryPlot) chart.getPlot();

        // 设置背景
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(230, 230, 230)); // 浅灰色网格线
        plot.setOutlinePaint(new Color(200, 200, 200)); // 边框颜色

        // 设置X轴
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setLabelFont(labelFont);
        domainAxis.setTickLabelFont(labelFont);
        domainAxis.setTickLabelPaint(new Color(80, 80, 80));
        domainAxis.setAxisLinePaint(new Color(180, 180, 180));

        // 设置Y轴
        ValueAxis rangeAxis = plot.getRangeAxis();
        rangeAxis.setLabelFont(labelFont);
        rangeAxis.setTickLabelFont(labelFont);
        rangeAxis.setTickLabelPaint(new Color(80, 80, 80));
        rangeAxis.setAxisLinePaint(new Color(180, 180, 180));

        // 设置图例
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setItemFont(labelFont);
            legend.setItemPaint(new Color(60, 60, 60));
        }

        // 设置渲染器样式
        CategoryItemRenderer renderer = plot.getRenderer();

        // 设置系列颜色
        if (seriesName.equals("营业额")) {
            renderer.setSeriesPaint(0, new Color(41, 128, 185)); // 蓝色
        } else {
            renderer.setSeriesPaint(0, new Color(39, 174, 96)); // 绿色
        }

        // 设置边框
        if (renderer instanceof BarRenderer) {
            BarRenderer barRenderer = (BarRenderer) renderer;
            barRenderer.setSeriesOutlinePaint(0, new Color(30, 100, 150));
            barRenderer.setSeriesOutlineStroke(0, new BasicStroke(0.5f));
            barRenderer.setShadowVisible(false); // 禁用阴影，使图表更清晰
        }
    }

    /**
     * 检查字体是否可用
     */
    private boolean isFontAvailable(String fontName) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] availableFontNames = ge.getAvailableFontFamilyNames();
        for (String name : availableFontNames) {
            if (name.equals(fontName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 兼容最新JFreeChart版本的图表标签配置
     */
    private void configureChartLabels(JFreeChart chart, boolean isCurrency) {
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        CategoryItemRenderer renderer = plot.getRenderer();

        // 新版JFreeChart API - 使用set*方法而不是setBase*方法
        if (isCurrency) {
            // 货币格式
            renderer.setDefaultItemLabelGenerator(
                    new StandardCategoryItemLabelGenerator("{2}", NumberFormat.getCurrencyInstance())
            );
        } else {
            // 整数格式
            renderer.setDefaultItemLabelGenerator(
                    new StandardCategoryItemLabelGenerator("{2}", NumberFormat.getIntegerInstance())
            );
        }

        // 启用数据标签
        renderer.setDefaultItemLabelsVisible(true);

        // 设置标签位置
        ItemLabelPosition position = new ItemLabelPosition(
                ItemLabelAnchor.OUTSIDE12,
                TextAnchor.BOTTOM_CENTER
        );
        renderer.setDefaultPositiveItemLabelPosition(position);

        // 为条形图设置适当的内边距，确保标签可见
        if (renderer instanceof BarRenderer) {
            BarRenderer barRenderer = (BarRenderer) renderer;
            barRenderer.setMaximumBarWidth(0.1); // 控制条形宽度
            barRenderer.setItemMargin(0.2); // 条形之间的间距
        }
    }


    /**
     * 导出报表到Excel（自动识别金额/人数列格式）
     *
     * @note 失败时提供CSV备选方案
     */
    private void exportReportToExcel(JTable table) {
        try {
            // 修复Date类问题 - 明确使用java.util.Date
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存营业报表");
            fileChooser.setSelectedFile(new File("营业报表_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".xlsx"));

            int userSelection = fileChooser.showSaveDialog(this);
            if (userSelection != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File fileToSave = fileChooser.getSelectedFile();
            String filePath = fileToSave.getAbsolutePath();
            if (!filePath.toLowerCase().endsWith(".xlsx")) {
                filePath += ".xlsx";
            }

            // 创建目录（如果不存在）
            File parentDir = fileToSave.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 使用Apache POI创建Excel
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                XSSFSheet sheet = workbook.createSheet("营业报表");

                // 创建表头样式
                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                XSSFFont headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setColor(IndexedColors.WHITE.getIndex());
                headerStyle.setFont(headerFont);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setBorderLeft(BorderStyle.THIN);
                headerStyle.setBorderRight(BorderStyle.THIN);

                // 创建数据样式 - 金额列
                CellStyle currencyStyle = workbook.createCellStyle();
                currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
                currencyStyle.setBorderBottom(BorderStyle.THIN);
                currencyStyle.setBorderTop(BorderStyle.THIN);
                currencyStyle.setBorderLeft(BorderStyle.THIN);
                currencyStyle.setBorderRight(BorderStyle.THIN);

                // 创建数据样式 - 普通数字
                CellStyle numberStyle = workbook.createCellStyle();
                numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
                numberStyle.setBorderBottom(BorderStyle.THIN);
                numberStyle.setBorderTop(BorderStyle.THIN);
                numberStyle.setBorderLeft(BorderStyle.THIN);
                numberStyle.setBorderRight(BorderStyle.THIN);

                // 创建表头
                Row headerRow = sheet.createRow(0);
                TableModel model = table.getModel();
                for (int i = 0; i < model.getColumnCount(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(model.getColumnName(i));
                    cell.setCellStyle(headerStyle);
                }

                // 填充数据
                int revenueColumnIndex = -1;
                int avgRevenueColumnIndex = -1;
                int customerColumnIndex = -1;
                int orderCountColumnIndex = -1;

                // 自动检测列类型
                for (int i = 0; i < model.getColumnCount(); i++) {
                    String columnName = model.getColumnName(i);
                    if (columnName.contains("总营业额") || columnName.contains("金额")) {
                        revenueColumnIndex = i;
                    } else if (columnName.contains("平均客单价")) {
                        avgRevenueColumnIndex = i;
                    } else if (columnName.contains("顾客总数") || columnName.contains("人数")) {
                        customerColumnIndex = i;
                    } else if (columnName.contains("订单数量")) {
                        orderCountColumnIndex = i;
                    }
                }

                for (int i = 0; i < model.getRowCount(); i++) {
                    Row row = sheet.createRow(i + 1);
                    for (int j = 0; j < model.getColumnCount(); j++) {
                        Object value = model.getValueAt(i, j);
                        Cell cell = row.createCell(j);

                        // 设置边框
                        CellStyle borderStyle = workbook.createCellStyle();
                        borderStyle.setBorderBottom(BorderStyle.THIN);
                        borderStyle.setBorderTop(BorderStyle.THIN);
                        borderStyle.setBorderLeft(BorderStyle.THIN);
                        borderStyle.setBorderRight(BorderStyle.THIN);
                        cell.setCellStyle(borderStyle);

                        if (value == null || value.toString().trim().isEmpty()) {
                            cell.setCellValue("");
                            continue;
                        }

                        String cellValue = value.toString().trim();

                        // 特殊列处理 - 金额
                        if (j == revenueColumnIndex || j == avgRevenueColumnIndex) {
                            try {
                                // 移除货币符号和逗号
                                String cleanValue = cellValue.replaceAll("[^0-9.]", "");
                                if (!cleanValue.isEmpty()) {
                                    double numericValue = Double.parseDouble(cleanValue);
                                    cell.setCellValue(numericValue);
                                    cell.setCellStyle(currencyStyle);
                                } else {
                                    cell.setCellValue(cellValue);
                                }
                            } catch (NumberFormatException e) {
                                cell.setCellValue(cellValue);
                            }
                        }
                        // 特殊列处理 - 人数、订单数
                        else if (j == customerColumnIndex || j == orderCountColumnIndex) {
                            try {
                                String cleanValue = cellValue.replaceAll("[^0-9]", "");
                                if (!cleanValue.isEmpty()) {
                                    int numericValue = Integer.parseInt(cleanValue);
                                    cell.setCellValue(numericValue);
                                    cell.setCellStyle(numberStyle);
                                } else {
                                    cell.setCellValue(cellValue);
                                }
                            } catch (NumberFormatException e) {
                                cell.setCellValue(cellValue);
                            }
                        }
                        // 普通文本
                        else {
                            cell.setCellValue(cellValue);
                        }
                    }
                }

                // 自动调整列宽
                for (int i = 0; i < model.getColumnCount(); i++) {
                    sheet.setColumnWidth(i, Math.min((int) (sheet.getColumnWidth(i) * 1.5), 5000));
                }

                // 保存文件
                try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                    workbook.write(fileOut);
                }

                JOptionPane.showMessageDialog(this, "报表已成功导出到:\n" + filePath, "导出成功", JOptionPane.INFORMATION_MESSAGE);

                // 询问是否打开文件
                int openOption = JOptionPane.showConfirmDialog(this, "是否打开导出的文件?", "操作完成", JOptionPane.YES_NO_OPTION);
                if (openOption == JOptionPane.YES_OPTION) {
                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().open(new File(filePath));
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(this, "无法打开文件: " + ex.getMessage(), "提示", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(this, "当前系统不支持自动打开文件，请手动打开。", "提示", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "导出报表失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();

            // 提供备选方案
            int retryOption = JOptionPane.showConfirmDialog(this,
                    "导出失败，是否尝试导出为CSV格式？\n错误详情: " + e.getMessage(),
                    "导出失败",
                    JOptionPane.YES_NO_OPTION);

            if (retryOption == JOptionPane.YES_OPTION) {
                exportAsCSV(table);
            }
        }
    }

    /**
     * 导出报表到CSV（UTF-8编码）
     *
     * @note 自动转义特殊字符
     */
    private void exportAsCSV(JTable table) {
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存营业报表 (CSV)");
            fileChooser.setSelectedFile(new File("营业报表_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".csv"));

            int userSelection = fileChooser.showSaveDialog(this);
            if (userSelection != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File fileToSave = fileChooser.getSelectedFile();
            String filePath = fileToSave.getAbsolutePath();
            if (!filePath.toLowerCase().endsWith(".csv")) {
                filePath += ".csv";
            }

            StringBuilder csvContent = new StringBuilder();
            TableModel model = table.getModel();

            // 写入表头
            for (int i = 0; i < model.getColumnCount(); i++) {
                csvContent.append(escapeCSV(model.getColumnName(i)));
                if (i < model.getColumnCount() - 1) csvContent.append(",");
            }
            csvContent.append("\n");

            // 写入数据
            for (int i = 0; i < model.getRowCount(); i++) {
                for (int j = 0; j < model.getColumnCount(); j++) {
                    Object value = model.getValueAt(i, j);
                    csvContent.append(escapeCSV(value != null ? value.toString() : ""));
                    if (j < model.getColumnCount() - 1) csvContent.append(",");
                }
                csvContent.append("\n");
            }

            // 保存文件
            java.nio.file.Files.write(java.nio.file.Paths.get(filePath),
                    csvContent.toString().getBytes("UTF-8"));

            JOptionPane.showMessageDialog(this, "CSV格式报表已成功导出到:\n" + filePath, "导出成功", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "CSV导出失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    /**
     * 转义CSV特殊字符（逗号/引号/换行）
     *
     * @note 符合RFC4180标准
     */
    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 获取支持中文的字体（优先系统中文字体）
     *
     * @param size 字体大小
     */
    private Font getChineseFont(int size) {
        // 尝试使用系统支持的中文字体
        String[] chineseFonts = {"微软雅黑", "Microsoft YaHei", "宋体", "SimSun", "黑体", "SimHei", "KaiTi", "楷体"};

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Font font = null;

        for (String fontName : chineseFonts) {
            if (ge.getAvailableFontFamilyNames().length > 0) {
                font = new Font(fontName, Font.PLAIN, size);
                if (font.canDisplayUpTo("中文") == -1) {
                    return font;
                }
            }
        }

        // 如果找不到中文字体，使用默认字体并尝试显示中文
        return new Font("Dialog", Font.PLAIN, size);
    }

    /**
     * 为JFreeChart应用中文字体（解决乱码）
     *
     * @note 自动适配饼图/柱状图并调整标签间距
     */
    private void applyChineseFontToChart(JFreeChart chart) {
        Font chineseFont = getChineseFont(12);
        Font chineseFontBold = getChineseFont(14);
        chineseFontBold = chineseFontBold.deriveFont(Font.BOLD);

        // 设置标题
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(chineseFontBold);
        }

        // 设置图例
        LegendTitle legend = chart.getLegend();
        if (legend != null) {
            legend.setItemFont(chineseFont);
            legend.setItemPaint(Color.BLACK);
        }

        // 设置图表区域字体
        Plot plot = chart.getPlot();
        if (plot instanceof PiePlot) {
            PiePlot piePlot = (PiePlot) plot;
            piePlot.setLabelFont(chineseFont);
            // 注意：PiePlot没有setLegendLabelFont方法，图例字体已在上面设置
        } else if (plot instanceof CategoryPlot) {
            CategoryPlot categoryPlot = (CategoryPlot) plot;
            categoryPlot.getDomainAxis().setLabelFont(chineseFontBold);
            categoryPlot.getRangeAxis().setLabelFont(chineseFontBold);
            categoryPlot.getDomainAxis().setTickLabelFont(chineseFont);
            categoryPlot.getRangeAxis().setTickLabelFont(chineseFont);

            // 设置分类轴标签旋转，避免中文重叠
            if (categoryPlot.getDomainAxis() instanceof CategoryAxis) {
                CategoryAxis domainAxis = (CategoryAxis) categoryPlot.getDomainAxis();
                domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_45);
            }
        }

        // 设置额外边距，确保中文字符有足够显示空间
        chart.setPadding(new RectangleInsets(15, 20, 15, 20));
    }

    public void showTimeMessage(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }
}