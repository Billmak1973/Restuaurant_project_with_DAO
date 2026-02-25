package com.restaurant.controller;

import com.restaurant.dao.*;
import com.restaurant.dao.impl.*;
import com.restaurant.entity.CustomerGroup;
import com.restaurant.entity.MenuItem;
import com.restaurant.entity.OrderItem;
import com.restaurant.entity.Tables;
import com.restaurant.model.RestaurantModel;
import com.restaurant.service.ConnectionPool;
import com.restaurant.util.OperationResult;
import com.restaurant.view.OrderSystemGUI;
import com.restaurant.view.RestaurantView;

import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.*;

public class RestaurantController implements RestaurantModel.ModelChangeListener {
    public RestaurantModel model;
    private RestaurantView view;
    private OrderSystemGUI frame;
    private final OrderDAO orderDAO;
    private final OrderItemDAO orderItemDAO;
    private final MenuItemDAO menuItemDAO;
    private final TablesDAO tablesDAO;
    private final CustomerGroupDAO customerGroupDAO;
    private final QueueDAO queueDAO;

    public RestaurantController(RestaurantModel model, RestaurantView view, OrderSystemGUI frame) {
        // 关键：使用默认 DAO 实现（最小改动方案）
        this(model, view, frame, new OrderDAOImpl(), new OrderItemDAOImpl(), new MenuItemDAOImpl(), new TablesDAOImpl(), new CustomerGroupDAOImpl(), new QueueDAOImpl());
    }

    // 主构造函数（支持依赖注入）
    public RestaurantController(RestaurantModel model, RestaurantView view, OrderSystemGUI frame,
                                OrderDAO orderDAO, OrderItemDAO orderItemDAO, MenuItemDAO menuItemDAO, TablesDAO tablesDAO, CustomerGroupDAO customerGroupDAO, QueueDAO queueDAO) {
        this.model = model;
        this.view = view;
        this.frame = frame;
        this.orderDAO = orderDAO;        // 初始化字段
        this.orderItemDAO = orderItemDAO; //  初始化字段
        this.menuItemDAO = menuItemDAO;  // 正确初始化
        this.tablesDAO = tablesDAO;
        this.customerGroupDAO = customerGroupDAO;
        this.queueDAO = queueDAO;
        view.setController(this);
        model.addModelChangeListener(this);
        initializeView();
    }

    /**
     * 模型变更监听器
     */
    @Override
    public void onTableChanged(Tables table) {
        SwingUtilities.invokeLater(() -> {
            view.updateSingleTable(table); // 只更新這個餐桌
        });
    }

    @Override
    public void onQueueChanged() {
        SwingUtilities.invokeLater(() -> {
            updateQueueDisplay(); // 只更新隊列
        });
    }

    @Override
    public void onStructuralChange() {
        SwingUtilities.invokeLater(this::updateView); // 全局刷新
    }

    /**
     * 初始化视图和事件监听
     */
    private void initializeView() {
        // 显示餐桌
        view.setTables(model.getTables());
        updateQueueDisplay();
        view.updateTableStatusDisplay(model.getTables()); // 订单状态文本刷新
        // 设置添加顾客组按钮的监听器（使用方法引用）
        view.setAddGroupListener(this::handleAddGroup);
        view.setSplitTableListener(this::handleSplitTable);
        view.setRecombineTableListener(this::handleRecombineTable);
        view.setCheckoutListener(this::handleCheckoutAction);
        view.setOrderListener(e -> handleOpenOrderSystem());
        view.setChangeTableListener(e -> handleChangeTable());
        view.setClearAllListener(this::handleClearAll);
        view.setQueueManagementListener(e -> view.showQueueManagementDialog());
        view.setSelectTableListener(e -> view.showSelectTableDialog());
        view.setCloseDayListener(this::handleCloseDay);
        view.setReportListener(e ->handleShowBusinessReport());

    }

    public void updateView() {
        view.setTables(model.getTables());
        view.updateTablesDisplay(model.getTables());
        view.updateTableStatusDisplay(model.getTables()); // 订单状态文本刷新
        updateQueueDisplay(); // 更新队列显示
    }

    /**
     * 更新队列显示
     */
    public void updateQueueDisplay() {
        SwingUtilities.invokeLater(() -> {
            view.updateQueueDisplay(
                    model.getQueue2Seat(),
                    model.getQueue4Seat(),
                    model.getQueue6Seat()
            );
        });
    }

    /**
     * 处理添加顾客组
     */
    private void handleAddGroup(ActionEvent e) {
        String sizeStr = view.getGroupSizeInput();
        if (sizeStr == null || sizeStr.trim().isEmpty()) {
            JOptionPane.showMessageDialog(view, "请输入顾客组人数", "输入错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            int groupSize = Integer.parseInt(sizeStr.trim());
            if (groupSize <= 0) {
                JOptionPane.showMessageDialog(view, "顾客组人数必须大于0", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (groupSize > 8) {
                JOptionPane.showMessageDialog(view, "顾客组人数不能超过8人", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 添加顾客组
            CustomerGroup group = model.addCustomerGroup(groupSize);
            if (group != null) {
                // 更新视图
                updateView();

                String message;
                if (group.isAssigned()) {
                    message = "顾客组 #" + group.getCallNumber() + " (" + group.getSize() + "人) 已就座";
                } else {
                    message = "顾客组 #" + group.getCallNumber() + " (" + group.getSize() + "人) 已加入等待队列";
                }
                view.appendToLog(message);
                view.clearGroupSizeInput();
            } else {
                JOptionPane.showMessageDialog(view, "添加顾客组失败，请重试", "操作失败", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(view, "请输入有效的数字", "输入错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 处理拆分餐桌事件
     */
    private void handleSplitTable(ActionEvent e) {
        String tableId = JOptionPane.showInputDialog(view,
                "请输入要拆分的餐桌编号（只能拆分2人或4人桌）:",
                "拆分餐桌",
                JOptionPane.QUESTION_MESSAGE);

        if (tableId == null || tableId.trim().isEmpty()) {
            return;
        }

        try {
            boolean success = model.splitTable(tableId.trim());
            if (success) {
                JOptionPane.showMessageDialog(view,
                        "餐桌 #" + tableId + " 拆分成功！",
                        "操作成功",
                        JOptionPane.INFORMATION_MESSAGE);
                updateView();
                view.appendToLog("餐桌 #" + tableId + " 已拆分为两个子桌");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(view,
                    "拆分失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    /**
     * 处理合并餐桌事件
     */
    private void handleRecombineTable(ActionEvent e) {
        String mainTableId = JOptionPane.showInputDialog(view,
                "请输入要恢复的主桌编号:",
                "合并餐桌",
                JOptionPane.QUESTION_MESSAGE);

        if (mainTableId == null || mainTableId.trim().isEmpty()) {
            return;
        }

        try {
            boolean success = model.recombineTables(mainTableId.trim());
            if (success) {
                JOptionPane.showMessageDialog(view,
                        "餐桌 #" + mainTableId + " 合并成功！",
                        "操作成功",
                        JOptionPane.INFORMATION_MESSAGE);
                updateView();
                view.appendToLog("餐桌 #" + mainTableId + " 已成功合并");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(view,
                    "合并失败: " + ex.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }


    private void handleCheckoutAction(ActionEvent e) {
        // 显示结账对话框
        String tableNumber = view.showCheckoutDialog();
        if (tableNumber == null || tableNumber.isEmpty()) {
            return; // 用户取消了操作
        }

        // 1. 验证餐桌编号格式
        if (!model.isValidTableNumberFormat(tableNumber)) {
            view.showError("餐桌号格式无效！\n主桌应为纯数字（如7）\n子桌后缀只能是a或b（如7a或7b）");
            return;
        }

        // 2. 从模型中查找餐桌
        Tables targetTable = model.getTableById(tableNumber);
        if (targetTable == null) {
            view.showError("未找到餐桌: " + tableNumber);
            return;
        }

        // 3. 检查餐桌状态
        if (targetTable.getStatus() != Tables.TableStatus.OCCUPIED) {
            String statusText = targetTable.getStatus().toString();
            view.showError("餐桌 " + tableNumber + " 当前处于【" + statusText + "】状态，无法结账");
            return;
        }

        // 4. 检查是否为合并桌中的主桌
        if (!model.isMainOrderTable(tableNumber)) {
            Tables table = model.getTableById(tableNumber);
            String partnerId = table.getMergedWith();
            view.showError("该合并桌只能通过编号较小的餐桌（" + partnerId + "）进行操作。\n请切换至餐桌 " + partnerId + " 进行结账操作。");
            return;
        }

        //  5. 【关键修复】使用 Model 内存状态检查是否已结账（零数据库查询）
        if (model.isOrderCheckedOut(tableNumber)) {
            view.showError("餐桌 " + tableNumber + " 的订单已结账，无法再次结账");
            return;
        }

        //  6. 检查是否有活跃订单（使用 Model 内存状态）
        if (!model.hasOrder(tableNumber)) {
            view.showError("餐桌 " + tableNumber + " 没有订单，无法结账");
            return;
        }

        // 所有验证通过，显示结账界面
        view.showCheckoutInterface(tableNumber);
    }

    public void handleCheckoutSubmit(String tableNumber, double paymentAmount) {
        Map<String, Object> result = model.processCheckout(tableNumber, paymentAmount);

        if ((Boolean) result.get("success")) {
            Tables table = model.getTableById(tableNumber);
            if (table != null) {
                table.setOrderStatus(Tables.OrderStatus.CHECKED_OUT); // ← 内存更新
            }

            double changeAmount = (Double) result.get("changeAmount");
            double totalAmount = (Double) result.get("totalAmount");
            final Object revenueDateObj = result.get("revenueDate");

            SwingUtilities.invokeLater(() -> {
                String baseMessage = "结账成功!";
                if (changeAmount > 0) {
                    baseMessage += "\n找零金额: " + String.format("%.2f", changeAmount) + "元";
                }

                //  时区安全的跨日判断（使用字符串格式化）
                if (revenueDateObj instanceof java.sql.Date) {
                    java.sql.Date revenueDate = (java.sql.Date) revenueDateObj;

                    // 使用 SimpleDateFormat 格式化为 "yyyy-MM-dd" 字符串
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    String revenueDateStr = sdf.format(revenueDate);

                    // 获取当前系统日期（东八区）
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(new java.util.Date());
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    String todayStr = sdf.format(cal.getTime());

                    //  仅比较日期字符串（忽略时区问题）
                    if (!revenueDateStr.equals(todayStr)) {
                        baseMessage += "\n\n 跨日结账提示:\n该订单创建于 " + revenueDateStr +
                                "，营业额已计入该日期的统计中。";
                    }
                }

                JOptionPane.showMessageDialog(view, baseMessage, "结账成功", JOptionPane.INFORMATION_MESSAGE);
                updateView();
                view.updateLogDisplay("餐桌 " + tableNumber + " 结账成功，金额: " +
                        String.format("%.2f", totalAmount) + "元");
            });
        } else {
            String message = (String) result.get("message");
            SwingUtilities.invokeLater(() -> {
                view.showError("结账失败: " + message);
            });
        }
    }

    private void handleChangeTable() {
        // 调用新方法显示合并后的弹窗
        String[] inputs = view.showChangeTableDialog();
        if (inputs == null) {
            return; // 用户取消了操作
        }

        String fromInput = inputs[0]; // 换桌的餐桌ID
        String toInput = inputs[1]; // 目标空闲餐桌ID

        //调用模型层的方法执行换桌逻辑
        boolean success = model.changeTable(fromInput, toInput);
        if (success) {
            view.updateLogDisplay("已将餐桌 #" + fromInput + " 的顾客组转移到餐桌 #" + toInput);
            view.updateTablesDisplay(model.getTables());
        }
    }

    private void handleClearAll(ActionEvent e) {
        int confirm = JOptionPane.showConfirmDialog(view,
                "确定要清空所有餐桌吗？\n注意：所有占用中的餐桌将被释放，排队顾客将被清空。",
                "确认清空", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            // ✅ 使用 Model 层的统一清空方法
            boolean success = model.clearAllTables();

            if (success) {
                // 刷新界面
                updateView();
            } else {
                view.showError("清空餐桌失败，请检查系统状态");
            }
        } catch (Exception ex) {
            view.showError("清空餐桌时发生错误: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public boolean hasWaitingCustomers() {
        return model.hasWaitingCustomers();
    }


    public void handleQueueManagementAction(int callNumber, int customerCount,
                                            boolean isAdd, boolean isEdit, boolean isDelete) {
        try {
            if (isAdd) {
                // 直接调用 Model 现有方法（已包含完整业务逻辑）
                if (customerCount <= 0 || customerCount > 9) {
                    view.showError("客户数量必须在1-9之间！");
                    return;
                }

                CustomerGroup newGroup = model.addCustomerGroup(customerCount);
                if (newGroup != null) {
                    view.updateLogDisplay("成功添加新顾客组 #" + newGroup.getCallNumber() +
                            " (" + customerCount + "人) 到队列");
                    model.checkAndAssignWaitingCustomers(); // 检查可分配餐桌
                } else {
                    view.showError("添加顾客组失败");
                    return;
                }
            } else {
                //  通过 Model 层统一查找（封装数据访问细节）
                CustomerGroup targetGroup = model.findCustomerGroupByCallNumber(callNumber);
                if (targetGroup == null) {
                    view.showError("未找到指定排队号码的顾客组！");
                    return;
                }

                if (isDelete) {
                    //  事务性删除交给 Model 层
                    model.removeCustomerGroupFromQueue(targetGroup);
                    view.updateLogDisplay("已删除排队号为 #" + callNumber + " 的顾客组。");
                    model.checkAndAssignWaitingCustomers(); // 释放餐桌后检查等待队列
                }

                if (isEdit) {
                    // 人数变更逻辑封装在 Model 层
                    if (customerCount <= 0 || customerCount > 9) {
                        view.showError("客户数量必须在1-9之间！");
                        return;
                    }

                    int originalSize = targetGroup.getSize();
                    model.updateCustomerGroupSize(targetGroup, customerCount);
                    view.updateLogDisplay("已修改排队号为 #" + callNumber + " 的顾客组人数从 " +
                            originalSize + " 人变为 " + customerCount + " 人。");
                    model.checkAndAssignWaitingCustomers(); // 人数变更后重新评估分配
                }
            }

            //  统一刷新界面（避免重复代码）
            updateView(); // 复用现有方法，内部调用 view.updateTablesDisplay() 和 view.updateQueueDisplay()

        } catch (SQLException e) {
            view.showError("数据库操作失败: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            view.showError("操作失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void handleManualTableAssignment(
            String tableIdInput,
            int peopleCount,
            boolean isFromQueue,
            int callNumber,
            boolean isMerge,
            boolean isTwoSeat,
            boolean isFourSeat,
            boolean isSixSeat,
            boolean isAddGuests,
            boolean isShare,
            String secondTableIdInput) {

        if (tableIdInput == null || tableIdInput.trim().isEmpty()) {
            view.showError("餐桌编号不能为空");
            return;
        }

        if (isMerge && (secondTableIdInput == null || secondTableIdInput.trim().isEmpty())) {
            view.showError("合并操作需要指定第二张餐桌编号");
            return;
        }

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            OperationResult<Boolean> result = model.tryAssignCustomerToTable(
                    conn, tableIdInput, peopleCount, isTwoSeat, isFourSeat,
                    isMerge, isShare, isAddGuests, isSixSeat,
                    secondTableIdInput, isFromQueue, callNumber
            );

            if (result.isSuccess()) {
                conn.commit();
                model.syncMemoryAfterAddGuests(tableIdInput);  // ← 新增方法（见步骤2）
                view.showInfo(buildSuccessMessage(isFromQueue, callNumber, peopleCount, isMerge, isAddGuests, isShare));
                updateView();
            } else {
                conn.rollback();
                switch (result.getErrorType()) {
                    case ERROR -> view.showError(result.getErrorMessage());
                    case WARNING -> view.showWarning(result.getErrorMessage());
                    case INFO -> view.showInfo(result.getErrorMessage());
                }
            }

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) { /* ignore */ }
            }
            view.showError("数据库事务失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 简洁版消息构建（无图标）
    private String buildSuccessMessage(boolean isFromQueue, int callNumber, int peopleCount,
                                       boolean isMerge, boolean isAddGuests, boolean isShare) {
        StringBuilder msg = new StringBuilder();
        if (isFromQueue) {
            msg.append("排队号 #").append(callNumber).append(" 已成功就座");
        } else {
            msg.append("新顾客组（").append(peopleCount).append("人）已成功就座");
        }

        if (isMerge) msg.append("（合并桌子）");
        else if (isAddGuests) msg.append("（添加客人）");
        else if (isShare) msg.append("（共享餐桌）");

        return msg.append("。").toString();
    }



    private void handleCloseDay(ActionEvent e) {
        if (model.isOpenForBusiness()) {
            // ===== 🔹 纯内存查询未结账餐桌（零SQL）=====
            List<String> unpaidTables = model.getTablesWithUnpaidOrdersInMemory();

            // ===== 🔹 未结账订单提醒（不阻止打烊）=====
            if (!unpaidTables.isEmpty()) {
                StringBuilder warningMsg = new StringBuilder();
                warningMsg.append("⚠️ 以下餐桌有未结账订单：\n\n");

                int displayCount = Math.min(unpaidTables.size(), 15);
                for (int i = 0; i < displayCount; i++) {
                    warningMsg.append("  • 餐桌 #").append(unpaidTables.get(i)).append("\n");
                }
                if (unpaidTables.size() > 15) {
                    warningMsg.append("\n  ... 还有 ").append(unpaidTables.size() - 15)
                            .append(" 个餐桌未显示");
                }
                warningMsg.append("\n\n未结账订单将保留至次日，不影响打烊操作。\n");
                warningMsg.append("是否确认结束营业？");

                Object[] options = {"✅ 确认打烊", "❌ 取消"};
                int choice = JOptionPane.showOptionDialog(
                        view,
                        warningMsg.toString(),
                        "未结账订单提醒",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[1]
                );

                if (choice != JOptionPane.YES_OPTION) {
                    view.updateLogDisplay("✗ 打烊操作已取消");
                    return;
                }
            }

            // ===== 🔹 常规打烊确认 =====
            int confirm = JOptionPane.showConfirmDialog(
                    view,
                    "确定要结束营业吗？\n此操作将停止接待新顾客！",
                    "确认结束营业",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (confirm == JOptionPane.YES_OPTION) {
                // ===== 🔹 单一调用：Model 内部处理事务 =====
                try {
                    model.closeForDayWithPersistence();

                    // ===== 🔹 仅负责 UI 更新 =====
                    view.updateLogDisplay("✓ 餐厅已打烊，停止接待新顾客。");
                    if (!unpaidTables.isEmpty()) {
                        view.updateLogDisplay("⚠️ 提醒：未结账餐桌: " + String.join(", ", unpaidTables));
                    }
                    view.clearGroupSizeInput();
                    updateView();
                    view.setCloseDayButtonText("开始营业");
                    view.updateBusinessStatusDisplay(false);

                } catch (RuntimeException ex) {
                    // ===== 🔹 统一错误处理 =====
                    view.updateLogDisplay("❌ 打烊失败: " + ex.getMessage());
                    JOptionPane.showMessageDialog(view,
                            "保存营业状态失败: " + ex.getCause().getMessage(),
                            "数据库错误",
                            JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                }
            }

        } else {
            // =====  开始营业流程（对称设计）=====
            try {
                model.openForBusinessWithPersistence();  //  单一调用

                view.updateLogDisplay("✓ 餐厅重新开始营业");
                updateView();
                view.setCloseDayButtonText("结束营业");
                view.updateBusinessStatusDisplay(true);

            } catch (RuntimeException ex) {
                view.updateLogDisplay("❌ 开始营业失败: " + ex.getMessage());
                JOptionPane.showMessageDialog(view,
                        "保存营业状态失败: " + ex.getCause().getMessage(),
                        "数据库错误",
                        JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    /**
     * 显示营业报表对话框
     */
    private void handleShowBusinessReport() {
        view.showBusinessReportDialog();
    }

    /**
     * 获取单日营业报表
     * @param date 日期（格式：YYYY-MM-DD）
     * @return 报表数据列表
     */
    public List<Map<String, Object>> getDailyBusinessReport(String date) {
        return model.getDailyBusinessReport(date);
    }

    /**
     * 获取日期范围营业报表
     * @param startDate 起始日期（YYYY-MM-DD）
     * @param endDate 结束日期（YYYY-MM-DD）
     * @return 报表数据列表
     */
    public List<Map<String, Object>> getDateRangeBusinessReport(String startDate, String endDate) {
        return model.getDateRangeBusinessReport(startDate, endDate);
    }

    /**
     * 获取季度菜品销售报表
     * @param year 年份
     * @param quarter 季度（Q1-Q4）
     * @param category 菜品类别（可选）
     * @return 销售数据列表，异常时抛出运行时异常
     */
    public List<Map<String, Object>> getQuarterlyDishSalesReport(int year, String quarter, String category) {
        try {
            return model.getQuarterlyDishSalesReport(year, quarter, category);
        } catch (Exception e) {
            System.err.println("控制器获取季度菜品销售报表失败: " + e.getMessage());
            throw new RuntimeException("获取报表数据失败: " + e.getMessage(), e);
        }
    }
    /**
     * 获取菜品销售数据中有记录的年份列表
     *
     * @return 按降序排列的年份字符串列表（例如["2026", "2025"]）
     *         无数据或异常时返回包含当前年份的列表
     */
    public List<String> getAvailableYearsForDishSales() {
        return model.getAvailableYearsForDishSales();
    }

    private void handleOpenOrderSystem() {
        new OrderSystemGUI(this, model).setVisible(true);
    }


    public String getOrderStatusDisplay(String tableNumber) {
        return model.getOrderStatusDisplay(tableNumber);
    }

    // 订单状态变更（点菜/上菜/结账）- 仅刷新订单相关区域
    public void refreshOrderStatusOnly() {
        SwingUtilities.invokeLater(() -> {
            view.updateTableStatusDisplay(model.getTables()); // 仅文本面板刷新（含订单状态）
            if (frame != null) {
                frame.refreshAllPanels(); // 同步刷新点餐系统面板
            }
        });
    }

    /**
     * 处理订单确认核心业务逻辑（Controller层）
     * <p>
     * 职责分离：
     * - 接收View层传递的临时订单数据
     * - 智能判断：新建订单 或 合并到现有订单
     * - 事务管理：确保订单头+明细+状态更新原子性
     * - 异步执行：通过SwingWorker避免UI冻结
     * <p>
     * 关键设计：
     * 1. 事务边界：conn.commit()成功后才更新内存状态（防止回滚不一致）
     * 2. 智能合并：自动识别"首次下单"与"追加点菜"场景
     * 3. 回调机制：onSuccess在EDT线程执行，确保UI刷新安全
     * 4. 零SQL原则：所有数据库操作委托DAO，Controller仅协调事务
     *
     * @param tableNumber 餐桌编号（如"7"或"7a"），必须已验证有效性
     * @param orderItems  订单项列表，由View层从临时订单转换而来
     * @param onSuccess   事务提交成功后的UI刷新回调（在EDT线程执行）
     */

    public void handleConfirmOrder(String tableNumber, List<OrderItem> orderItems, boolean isReorderAfterCheckout, Runnable onSuccess) {
        if (orderItems == null || orderItems.isEmpty()) {
            JOptionPane.showMessageDialog(null, "訂單不能為空");
            return;
        }

        if (tableNumber == null || tableNumber.trim().isEmpty() || "未选择".equals(tableNumber)) {
            JOptionPane.showMessageDialog(null, "請先選擇餐桌");
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                Tables table = model.getTableById(tableNumber);
                int tableId = table.getTableId();

                Map<String, Integer> newItems = new HashMap<>();
                for (OrderItem item : orderItems) {
                    newItems.merge(item.getItemCode(), item.getQuantity(), Integer::sum);
                }

                try (Connection conn = ConnectionPool.getConnection()) {
                    conn.setAutoCommit(false);

                    // 处理已结账后重新点单
                    if (isReorderAfterCheckout) {
                        Integer checkedOutOrderId = orderDAO.findCheckedOutOrderIdByTableId(conn, tableId);
                        if (checkedOutOrderId != null) {
                            orderDAO.updateOrderStatusAndAmount(
                                    conn,
                                    checkedOutOrderId,
                                    Tables.OrderStatus.ORDERED_UNFINISHED,
                                    0.0
                            );
                            orderItemDAO.deleteOrderItemsByOrderId(conn, checkedOutOrderId);
                        }
                    }

                    Integer orderId = orderDAO.findActiveOrderIdByTableId(conn, tableId);

                    if (orderId == null) {
                        double total = orderItems.stream()
                                .mapToDouble(i -> i.getQuantity() * i.getPriceAtOrder())
                                .sum();
                        orderId = orderDAO.createOrder(conn, tableId, total);
                        for (OrderItem item : orderItems) {
                            item.setOrderId(orderId);
                        }
                        orderItemDAO.addOrderItems(conn, orderId, orderItems);
                    } else {
                        orderItemDAO.mergeOrderItems(
                                conn,
                                orderId,
                                newItems,
                                code -> {
                                    try {
                                        return menuItemDAO.findItemIdByCode(conn, code);
                                    } catch (SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                },
                                code -> {
                                    MenuItem item = null;
                                    try {
                                        item = menuItemDAO.findById(code);
                                    } catch (SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                    if (item == null) throw new RuntimeException("未找到菜品：" + code);
                                    return item.getPrice();
                                }
                        );
                    }

                    table.setOrderStatus(Tables.OrderStatus.ORDERED_UNFINISHED);
                    conn.commit();
                }

                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    refreshOrderStatusOnly();
                    if (onSuccess != null) {
                        SwingUtilities.invokeLater(onSuccess);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(
                            null,
                            "下單失敗：" + e.getMessage(),
                            "錯誤",
                            JOptionPane.ERROR_MESSAGE
                    );
                    e.printStackTrace();
                }
            }
        };

        worker.execute();
    }

    /**
     * 標記指定菜品為已上桌（Controller 層事務管理）
     *
     * @param tableNumber 餐桌編號（如 "7" 或 "7a"）
     * @param itemId      數據庫菜品ID（int 類型，由 View 層從內存緩存獲取）
     * @param quantity    標記上桌的數量
     * @throws SQLException             數據庫操作失敗
     * @throws IllegalArgumentException 業務驗證失敗
     */
    public void handleMarkItemsAsServed(String tableNumber, int itemId, int quantity)
            throws SQLException {

        // 1. 基礎驗證
        if (tableNumber == null || tableNumber.trim().isEmpty() || "未选择".equals(tableNumber.trim())) {
            throw new IllegalArgumentException("餐桌号不能为空");
        }
        if (itemId <= 0) {
            throw new IllegalArgumentException("无效的菜品ID");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("数量必须大于0");
        }

        // 2. 獲取餐桌
        Tables table = model.getTableById(tableNumber);
        if (table == null) {
            throw new IllegalStateException("餐桌不存在: " + tableNumber);
        }
        int tableId = table.getTableId();
        if (tableId <= 0) {
            throw new IllegalStateException("无效餐桌ID: " + tableId);
        }

        // 3. 事務管理
        Connection conn = null;
        boolean allServedAfterUpdate = false; // 用于内存状态更新的标志

        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 4. 獲取活躍訂單
            Integer orderId = orderDAO.findActiveOrderIdByTableId(conn, tableId);
            if (orderId == null) {
                throw new IllegalStateException("餐桌 " + tableNumber + " 沒有活躍訂單");
            }

            // 5. 執行上桌操作（DAO 层 - 订单明细持久化到数据库）
            orderItemDAO.incrementServedQuantity(conn, orderId, itemId, quantity);

            // 6. 关键：在事务内检查是否全部上桌（避免额外查库）
            allServedAfterUpdate = !orderItemDAO.hasUnservedItems(conn, orderId);

            conn.commit(); // 事务提交（订单明细已100%持久化到数据库）

            // 7.  事务成功后更新内存状态（仅状态标识，不存订单明细！）
            if (allServedAfterUpdate) {
                table.setOrderStatus(Tables.OrderStatus.ORDERED_FINISHED);
            }
            // 否则保持 ORDERED_UNFINISHED（无需显式设置，因为已是该状态）

            System.out.println(" 部分上桌成功 - 餐桌: " + tableNumber +
                    ", 菜品ID: " + itemId +
                    ", 數量: " + quantity +
                    (allServedAfterUpdate ? " | 订单状态更新为: 已完成" : ""));

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("回滚失败: " + rollbackEx.getMessage());
                }
            }

            throw new SQLException("標記上桌失敗: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    System.err.println("關閉連接失敗: " + closeEx.getMessage());
                }
            }
        }
    }

    /**
     * 一鍵標記餐桌所有菜品為已上桌（Controller 層事務管理）
     *
     * @param tableNumber 餐桌編號（如 "7" 或 "7a"）
     * @throws SQLException             數據庫操作失敗
     * @throws IllegalArgumentException 業務驗證失敗
     */
    public void handleMarkAllItemsAsServed(String tableNumber) throws SQLException {
        // 1. 基础验证
        if (tableNumber == null || tableNumber.trim().isEmpty() || "未选择".equals(tableNumber.trim())) {
            throw new IllegalArgumentException("餐桌号不能为空");
        }

        // 2. 获取餐桌（Model 层）
        Tables table = model.getTableById(tableNumber);
        if (table == null) {
            throw new IllegalStateException("餐桌不存在: " + tableNumber);
        }
        int tableId = table.getTableId();
        if (tableId <= 0) {
            throw new IllegalStateException("无效餐桌ID: " + tableId);
        }

        // 3. 事务管理（DAO 层操作，Controller 零 SQL）
        Connection conn = null;
        boolean allServedAfterUpdate = false; // 用于内存状态更新

        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 4. 获取活跃订单
            Integer orderId = orderDAO.findActiveOrderIdByTableId(conn, tableId);
            if (orderId == null) {
                throw new IllegalStateException("餐桌 " + tableNumber + " 沒有活躍訂單");
            }

            // 5. 检查是否有待上桌菜品
            if (!orderItemDAO.hasUnservedItems(conn, orderId)) {
                throw new IllegalStateException("餐桌 " + tableNumber + " 沒有待上桌的菜品");
            }

            // 6. 执行批量上桌（DAO 层）
            int updatedCount = orderItemDAO.markAllItemsAsServed(conn, orderId);
            if (updatedCount <= 0) {
                throw new IllegalStateException("未找到可更新的菜品明細");
            }

            // 7.关键：在事务内检查是否全部上桌（避免额外查库）
            allServedAfterUpdate = !orderItemDAO.hasUnservedItems(conn, orderId);

            conn.commit();

            // 8.  事务成功后更新内存状态（仅状态，不存明细！）
            if (allServedAfterUpdate) {
                table.setOrderStatus(Tables.OrderStatus.ORDERED_FINISHED);
            } else {
                // 理论上不会发生（因为是"全部上桌"），但防御性编程
                table.setOrderStatus(Tables.OrderStatus.ORDERED_UNFINISHED);
            }

            System.out.println("全部上桌成功 - 餐桌: " + tableNumber + ", 更新菜品數: " + updatedCount);

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("回滚失败: " + rollbackEx.getMessage());
                }
            }
            // 事务失败，不更新内存状态（保持旧状态）
            throw new SQLException("標記全部菜品失敗: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    System.err.println("關閉連接失敗: " + closeEx.getMessage());
                }
            }
        }
    }

    /**
     * 撤销菜品（Controller 仅负责事务协调，所有验证在 View 层完成）
     */
    public void handleCancelOrderItem(String tableNumber, String itemCode, int cancelQuantity,
                                      String cancellationReason) throws SQLException {
        // 1. 获取餐桌（仅做基础空值检查，详细验证在 View 层）
        Tables table = model.getTableById(tableNumber);
        if (table == null) {
            throw new IllegalStateException("餐桌不存在: " + tableNumber);
        }
        int tableId = table.getTableId();

        // 2. 事务管理
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 3. 获取活跃订单
            Integer orderId = orderDAO.findActiveOrderIdByTableId(conn, tableId);
            if (orderId == null) {
                throw new IllegalStateException("餐桌 " + tableNumber + " 没有活跃订单");
            }

            // 4. 获取菜品ID（通过 DAO，Controller 不写 SQL）
            Integer itemId = menuItemDAO.findItemIdByCode(conn, itemCode);
            if (itemId == null) {
                throw new IllegalStateException("菜品 " + itemCode + " 不存在");
            }

            // 5. 执行撤销
            orderItemDAO.cancelOrderItem(conn, orderId, itemId, cancelQuantity, cancellationReason);

            // 6. 重新计算订单总金额
            orderItemDAO.recalculateOrderTotal(conn, orderId);

            // 7. 检查订单是否为空 → 删除订单
            if (!orderItemDAO.hasRemainingItems(conn, orderId)) {
                boolean deleted = orderDAO.deleteOrder(conn, orderId);
                if (!deleted) {
                    throw new SQLException("删除空订单失败");
                }
            }

            conn.commit();
            System.out.println(" 撤销成功 - 餐桌: " + tableNumber + ", 菜品: " + itemCode + ", 数量: " + cancelQuantity);

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("回滚失败: " + rollbackEx.getMessage());
                }
            }
            throw new SQLException("撤销菜品失败: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    System.err.println("关闭连接失败: " + closeEx.getMessage());
                }
            }
        }
    }


    /**
     * 取消重新点餐（同步执行）
     *
     * @param tableNumber 餐桌号
     * @throws Exception 业务失败或系统错误时抛出异常，消息即为用户提示
     */
    public void handleCancelReorder(String tableNumber) throws Exception {
        Tables table = model.getTableById(tableNumber);
        if (table == null) {
            throw new Exception("餐桌 " + tableNumber + " 不存在");
        }
        int tableId = table.getTableId();

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. 检查活跃订单
                Integer orderId = orderDAO.findActiveOrderIdByTableId(conn, tableId);
                if (orderId == null) {
                    conn.rollback();
                    throw new Exception("餐桌 " + tableNumber + " 没有活跃订单，无需取消重新点餐");
                }

                // 2. 检查重新点单场景
                if (!orderDAO.isOrderPreviouslyCheckedOut(conn, tableId)) {
                    conn.rollback();
                    throw new Exception("餐桌 " + tableNumber + " 的订单是全新订单，不属于重新点单场景");
                }

                // 3. 检查已上菜菜品
                if (orderItemDAO.hasServedItems(conn, orderId)) {
                    conn.rollback();
                    throw new Exception("餐桌 " + tableNumber + " 有已上桌菜品，不能取消重新点餐");
                }

                // 4. 执行恢复操作
                orderDAO.updateOrderStatusAndAmount(
                        conn,
                        orderId,
                        Tables.OrderStatus.CHECKED_OUT,
                        0.0
                );

                // 防御性：commit 前验证 autoCommit
                if (conn.getAutoCommit()) {
                    conn.setAutoCommit(false);
                }
                conn.commit();
                table.setOrderStatus(Tables.OrderStatus.CHECKED_OUT);

            } catch (Exception ex) {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
                throw ex;
            }
        }
    }

    /**
     * 检查餐桌是否有活跃订单（纯内存查询）
     *
     * @return true=有订单（ORDERED_UNFINISHED/ORDERED_FINISHED），false=无订单（NO_ORDER）
     */
    public boolean hasAnyOrderForTable(String tableId) {
        return model.hasOrder(tableId);
    }

    /**
     * 检查餐桌是否有未上桌菜品（纯内存查询）
     *
     * @note 通过 orderStatus 间接判断：ORDERED_UNFINISHED = 有未上桌菜品
     */
    public boolean hasUnservedItems(String tableId) {
        Tables table = model.getTableById(tableId);
        return table != null &&
                table.getOrderStatus() == Tables.OrderStatus.ORDERED_UNFINISHED;
    }

    /**
     * 检查餐桌订单是否已结账（纯内存查询）
     *
     * @return true=已结账（CHECKED_OUT），false=未结账
     */
    public boolean isTableCheckedOut(String tableNumber) {
        return model.isOrderCheckedOut(tableNumber);
    }

    /**
     * 获取订单详情（结账界面需要明细数据，此处允许查库一次）
     *
     * @note 仅结账界面调用，非高频操作，查库可接受
     */
    public Map<String, Object> getOrderDetails(String tableNumber) {
        return model.getOrderDetails(tableNumber);
    }


}