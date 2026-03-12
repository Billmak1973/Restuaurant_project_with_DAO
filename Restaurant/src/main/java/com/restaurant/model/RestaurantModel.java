package com.restaurant.model;


import com.restaurant.dao.*;
import com.restaurant.dao.impl.*;
import com.restaurant.entity.CustomerGroup;
import com.restaurant.entity.Tables;
import com.restaurant.service.ConnectionPool;
import com.restaurant.service.MenuCategoryService;
import com.restaurant.util.OperationResult;

import javax.swing.*;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.stream.Stream;


public class RestaurantModel {

    public interface ModelChangeListener {
        void onTableChanged(Tables table);  // 新增方法

        void onQueueChanged();              // 新增方法

        void onStructuralChange();              // 👈 新增：用于拆分/合并等结构变化
    }

    private CustomerGroupDAO customerGroupDAO;
    private TablesDAO tablesDAO;
    private final OrderDAO orderDAO;
    private final OrderItemDAO orderItemDAO;
    private final QueueDAO queueDAO;
    private BusinessStatusDAO businessStatusDAO;
    public Map<String, Tables> tableMap = new ConcurrentHashMap<>();
    private List<Tables> tables;
    private int nextCallNumber = 1;
    private boolean isOpenForBusiness = true;
    private Queue<CustomerGroup> queue2Seat = new LinkedList<>(); // 2人桌队列
    private Queue<CustomerGroup> queue4Seat = new LinkedList<>(); // 4人桌队列
    private Queue<CustomerGroup> queue6Seat = new LinkedList<>(); // 6人桌队列
    private final Object queueLock = new Object(); // 队列同步锁
    private List<ModelChangeListener> listeners = new ArrayList<>();
    private final Map<Integer, CustomerGroup> customerGroupMap = new ConcurrentHashMap<>();


    public RestaurantModel() {
        tables = new ArrayList<>();
        tableMap = new ConcurrentHashMap<>();

        // 初始化队列
        queue2Seat = new LinkedList<>();
        queue4Seat = new LinkedList<>();
        queue6Seat = new LinkedList<>();

        // 初始化DAO
        customerGroupDAO = new CustomerGroupDAOImpl();
        tablesDAO = new TablesDAOImpl();
        orderDAO = new OrderDAOImpl();
        orderItemDAO = new OrderItemDAOImpl();
        this.queueDAO = new QueueDAOImpl();
        this.businessStatusDAO = new BusinessStatusDAOImpl();


        // 1. 确保数据库结构存在
        ConnectionPool.initializeDatabaseSchema();

        // 2. 尝试从数据库加载现有数据
        if (!loadDataFromDatabase()) {
            // 3. 如果数据库中没有数据，初始化默认数据
            initializeDefaultData();
        }

        // 4. 确保今天的业务状态记录存在
        try (Connection conn = ConnectionPool.getConnection()) {
            businessStatusDAO.ensureTodayStatusExists(conn, LocalDate.now());
        } catch (SQLException e) {
            System.err.println("确保业务状态记录失败: " + e.getMessage());
            e.printStackTrace();
        }

        //  加载营业状态到内存
        // 加载营业状态到内存（直接调用 DAO）
        try (Connection conn = ConnectionPool.getConnection()) {
            Boolean isOpen = businessStatusDAO.loadIsOpenStatus(conn, LocalDate.now());
            if (isOpen != null) {
                this.isOpenForBusiness = isOpen;
            }
        } catch (SQLException e) {
            System.err.println(" 加载营业状态失败，使用默认值: " + e.getMessage());
            this.isOpenForBusiness = true;
            this.nextCallNumber = 1;
        }

        loadQueuesFromDatabase();
        MenuCategoryService.getInstance().initialize();
        initializeOrderStatusCache();

    }

    public void addModelChangeListener(ModelChangeListener listener) {
        listeners.add(listener);
    }

    public void removeModelChangeListener(ModelChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * 初始化时加载队列 - 确保被调用
     *
     * @return
     */
    private boolean loadDataFromDatabase() {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            if (tablesDAO.hasExistingTableData(conn)) {
                loadTablesFromDatabase(conn);
                System.out.println("成功从数据库加载 " + tables.size() + " 张餐桌");

                // 修复6: 关键！加载队列数据
                loadQueuesFromDatabase();

                nextCallNumber = businessStatusDAO.getNextCallNumber(conn, LocalDate.now());
                return true;
            }
        } catch (SQLException e) {
            System.err.println(" 检查数据库状态时出错: " + e.getMessage());
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
        return false;
    }


    /**
     * 从数据库加载餐桌数据
     */
    private void loadTablesFromDatabase(Connection conn) throws SQLException {
        tables.clear();
        tableMap.clear();

        // ✅ 核心重构：委托 DAO 层执行查询，Model 层零 SQL
        List<Tables> loadedTables = tablesDAO.findAllTables(conn);

        for (Tables table : loadedTables) {
            // 处理关联的顾客组（保持原有业务逻辑）
            Integer currentGroupId = table.getCurrentGroupId();
            if (currentGroupId != null) {
                try {
                    CustomerGroup group = customerGroupDAO.findById(currentGroupId);
                    if (group != null) {
                        table.setCurrentGroup(group);
                    }
                } catch (Exception e) {
                    System.err.println("加载关联顾客组失败 (餐桌 #" + table.getDisplayId() + "): " + e.getMessage());
                }
            }

            tables.add(table);
            tableMap.put(table.getDisplayId(), table);
        }

        System.out.println("✅ 成功加载 " + tables.size() + " 张餐桌，包含主桌/子桌关系");
    }


    /**
     * 初始化默认数据（餐桌、菜单分类等）
     */
    private void initializeDefaultData() {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 1. 初始化餐桌
            initializeDefaultTables(conn);

            // 2. 初始化菜单分类
            initializeMenuCategories(conn);

            // 3. 创建今天的业务状态记录
            businessStatusDAO.insertTodayStatus(conn, LocalDate.now());
            System.out.println("已创建今天的业务状态记录");

            // 4. 提交事务
            conn.commit();
            System.out.println("成功初始化默认数据");
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("回滚失败: " + ex.getMessage());
                }
            }
            System.err.println("初始化默认数据失败: " + e.getMessage());
            e.printStackTrace();

            // 即使失败，也创建内存中的默认餐桌，确保应用可以启动
            createInMemoryDefaultTables();
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

    /**
     * 在内存中创建默认餐桌（当数据库操作失败时的备用方案）
     */
    private void createInMemoryDefaultTables() {
        tables.clear();
        // 1-6号：2人桌
        for (int i = 1; i <= 6; i++) {
            tables.add(new Tables(i, 2, String.valueOf(i))); // 修正參數順序
        }
        // 7-12号：4人桌
        for (int i = 7; i <= 12; i++) {
            tables.add(new Tables(i, 4, String.valueOf(i))); // 修正參數順序
        }
        // 13-15号：6人桌
        for (int i = 13; i <= 15; i++) {
            tables.add(new Tables(i, 6, String.valueOf(i))); // 修正參數順序
        }
        System.out.println("已创建内存中的默认餐桌（数据库操作失败）");
    }


    private void initializeDefaultTables(Connection conn) throws SQLException {
        // ✅ 核心重构：委托DAO层执行插入操作
        tablesDAO.initializeDefaultTables(conn);

        // 重新加载餐桌到内存（后续应重构为使用 tablesDAO.findAllTables(conn)）
        loadTablesFromDatabase(conn);
    }

    /**
     * 初始化默认菜单分类
     */
    private void initializeMenuCategories(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) as count FROM menu_categories";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
             ResultSet rs = checkStmt.executeQuery()) {

            if (rs.next() && rs.getInt("count") == 0) {
                String insertSql = "INSERT INTO menu_categories (name, prefix) VALUES (?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, "特色食物");
                    insertStmt.setString(2, "A");
                    insertStmt.addBatch();

                    insertStmt.setString(1, "饮料");
                    insertStmt.setString(2, "B");
                    insertStmt.addBatch();

                    insertStmt.setString(1, "小炒");
                    insertStmt.setString(2, "C");
                    insertStmt.addBatch();

                    insertStmt.setString(1, "套餐");
                    insertStmt.setString(2, "D");
                    insertStmt.addBatch();

                    insertStmt.executeBatch();
                    System.out.println("成功初始化四个菜单分类");
                }
            }
        }
    }


    public void initializeOrderStatusCache() {
        try (Connection conn = ConnectionPool.getConnection()) {
            // 修复：使用聚合函数包裹所有非 GROUP BY 列
            String sql = """
                    SELECT t.display_id,
                           CASE
                             WHEN MAX(CASE WHEN o.status = 'CHECKED_OUT' THEN 1 ELSE 0 END) = 1 
                                  THEN 'CHECKED_OUT'
                             WHEN MAX(CASE WHEN oi.served_quantity < oi.quantity THEN 1 ELSE 0 END) = 1 
                                  THEN 'ORDERED_UNFINISHED'
                             WHEN MAX(oi.order_item_id) IS NOT NULL 
                                  THEN 'ORDERED_FINISHED'
                             ELSE 'NO_ORDER'
                           END AS order_status
                    FROM restaurant_tables t
                    LEFT JOIN table_orders o ON t.table_id = o.table_id
                    LEFT JOIN order_items oi ON o.order_id = oi.order_id
                    WHERE t.status = 'OCCUPIED'
                    GROUP BY t.display_id
                    """;

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String displayId = rs.getString("display_id");
                    String statusStr = rs.getString("order_status");
                    Tables table = getTableById(displayId);
                    if (table != null) {
                        Tables.OrderStatus status = switch (statusStr) {
                            case "CHECKED_OUT" -> Tables.OrderStatus.CHECKED_OUT;
                            case "ORDERED_UNFINISHED" -> Tables.OrderStatus.ORDERED_UNFINISHED;
                            case "ORDERED_FINISHED" -> Tables.OrderStatus.ORDERED_FINISHED;
                            default -> Tables.OrderStatus.NO_ORDER;
                        };
                        table.setOrderStatus(status);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("初始化订单状态缓存失败: " + e.getMessage());
            // 失败时保持默认状态 NO_ORDER，不影响系统运行
        }
    }

    /**
     * 添加顾客组
     *
     * @param groupSize 顾客组人数
     * @return 创建的顾客组，或null（如果无法添加）
     */

    public CustomerGroup addCustomerGroup(int groupSize) {
        if (!isOpenForBusiness) {
            System.out.println("餐廳未營業，無法添加顧客組");
            return null;
        }

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 1. 獲取下一個排隊號碼
            int nextCallNumber = businessStatusDAO.getNextCallNumber(conn, LocalDate.now());

            // 2. 創建新顧客組
            CustomerGroup group = new CustomerGroup(nextCallNumber, groupSize);
            group.setStartTime(LocalDateTime.now());
            group.setAssigned(false);

            // 3. 保存到數據庫（此時 group_id 已生成）
            group = customerGroupDAO.save(group);
            // 直接注册到 customerGroupMap（ConcurrentHashMap 线程安全，无需同步）
            customerGroupMap.put(group.getGroup_id(), group);

            boolean assigned = false;

            // 4a. 嘗試分配單張餐桌
            Tables assignedTable = tryAssignTableToGroup(conn, group);
            if (assignedTable != null) {
                processTableAssignment(conn, group, assignedTable);
                assigned = true;
            }
            // 4b. 3–4 人：合併 2 人桌
            else if (groupSize >= 3 && groupSize <= 4) {
                assigned = tryMergeTablesByCapacity(group, 2);
            }
            // 4c. 5–8 人：合併 4 人桌
            else if (groupSize >= 5 && groupSize <= 8) {
                assigned = tryMergeTablesByCapacity(group, 4);
            }
            // 4d. 1–2 人：自動分裂
            else if (groupSize <= 2) {
                assigned = tryAutoSplitForGroup(conn, group);
            }


            // 6. 未分配成功 → 入队
            if (!assigned) {
                enqueueGroupTransactional(conn, group);
                synchronized (queueLock) {
                    if (groupSize <= 2) {
                        group.setPosition(queue2Seat.size() + 1);
                        queue2Seat.add(group);
                    } else if (groupSize <= 4) {
                        group.setPosition(queue4Seat.size() + 1);
                        queue4Seat.add(group);
                    } else {
                        group.setPosition(queue6Seat.size() + 1);
                        queue6Seat.add(group);
                    }
                }
                System.out.println("顧客組 #" + group.getCallNumber()
                        + " 已加入隊列，大小: " + groupSize);
            }

            // 7. 更新下一個叫號
            businessStatusDAO.incrementNextCallNumber(conn, LocalDate.now());

            conn.commit();
            return group;

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("回滾事務失敗: " + ex.getMessage());
                }
            }
            System.err.println("添加顧客組失敗: " + e.getMessage());
            e.printStackTrace();
            return null;

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

    private Tables tryAssignTableToGroup(Connection conn, CustomerGroup group) throws SQLException {
        int groupSize = group.getSize();

        // 1. 首先尝试找完全匹配的餐桌
        List<Tables> availableTables = tablesDAO.findAvailableTables(groupSize, "MAIN");
        for (Tables table : availableTables) {
            //  6人桌规则：3人及以下不能使用6人桌
            if (table.getCapacity() == 6 && groupSize < 4) {
                continue;
            }
            return table;
        }

        // 2. 找容量稍大的餐桌
        availableTables = tablesDAO.findAvailableTables(groupSize + 1, "MAIN");
        for (Tables table : availableTables) {
            if (table.getCapacity() == 6 && groupSize < 4) {
                continue;
            }
            return table;
        }

        // 3. 尝试所有可用餐桌（最后手段）
        availableTables = tablesDAO.findAvailableTables(groupSize, null);
        for (Tables table : availableTables) {
            if (table.getCapacity() == 6 && groupSize < 4) {
                continue;
            }
            return table;
        }

        return null; // 无可用餐桌 → 将触发自动分裂（1-2人组）或入队
    }


    public List<Tables> getTables() {
        // 从 tableMap 中获取所有餐桌
        List<Tables> allTables = new ArrayList<>(tableMap.values());
        // 分离主桌和子桌
        List<Tables> mainTables = new ArrayList<>();
        List<Tables> subTables = new ArrayList<>();
        for (Tables table : allTables) {
            // 判断是否为子桌：有子桌后缀或有关联的主桌ID
            if (table.getSubTableSuffix() != null && !table.getSubTableSuffix().isEmpty()) {
                subTables.add(table);
            } else {
                mainTables.add(table);
            }
        }
        // 对主桌按 baseId 升序排序
        mainTables.sort(Comparator.comparingInt(Tables::getBaseId));
        // 对子桌按主桌 baseId + 后缀排序
        subTables.sort((t1, t2) -> {
            // 获取主桌 baseId
            int mainBaseId1 = t1.getMainTableId() != null ?
                    getTableById(String.valueOf(t1.getMainTableId())).getBaseId() : t1.getBaseId();
            int mainBaseId2 = t2.getMainTableId() != null ?
                    getTableById(String.valueOf(t2.getMainTableId())).getBaseId() : t2.getBaseId();
            // 按主桌 baseId 升序
            int baseIdComparison = Integer.compare(mainBaseId1, mainBaseId2);
            if (baseIdComparison != 0) {
                return baseIdComparison;
            }
            // 主桌 baseId 相同，按后缀升序（a < b）
            String suffix1 = t1.getSubTableSuffix() != null ? t1.getSubTableSuffix() : "";
            String suffix2 = t2.getSubTableSuffix() != null ? t2.getSubTableSuffix() : "";
            return suffix1.compareTo(suffix2);
        });
        // 合并最终顺序：主桌在前，子桌在后
        List<Tables> orderedTables = new ArrayList<>(mainTables);
        orderedTables.addAll(subTables);
        return orderedTables;
    }


    private Tables findMergedPartnerTable(Tables currentTable) {
        if (currentTable == null) return null;

        // 情况1: 当前餐桌有明确的 merged_with 指向
        if (currentTable.getMergedWith() != null && !currentTable.getMergedWith().isEmpty()) {
            Tables partner = getTableById(currentTable.getMergedWith());
            if (partner != null && partner.getStatus() == Tables.TableStatus.OCCUPIED) {
                return partner;
            }
        }

        // 情况2: 双向查找 - 找到 merged_with 指向当前餐桌的伙伴
        for (Tables table : tables) {
            if (table != currentTable &&
                    table.getStatus() == Tables.TableStatus.OCCUPIED &&
                    table.getMergedWith() != null &&
                    table.getMergedWith().equals(currentTable.getDisplayId())) {
                return table;
            }
        }

        return null;
    }

    public boolean finishMeal(String displayId) {
        Tables table = getTableById(displayId);
        if (table == null || table.getStatus() != Tables.TableStatus.OCCUPIED) {
            System.err.println("餐桌 #" + displayId + " 不处于占用状态，无法完成离店");
            return false;
        }

        Tables.OrderStatus orderStatus = table.getOrderStatus();
        if (orderStatus != Tables.OrderStatus.NO_ORDER &&
                orderStatus != Tables.OrderStatus.CHECKED_OUT) {

            String statusText = (orderStatus == Tables.OrderStatus.ORDERED_UNFINISHED)
                    ? "有未完成订单"
                    : "有已完成但未结账订单";

            System.err.println(" 餐桌 #" + displayId + " " + statusText + "，请先结账再离店");
            return false;
        }


        // === 关键修复：替换原有检测逻辑，使用双向查找 ===
        Tables partnerTable = findMergedPartnerTable(table);  // ← 替换这里！
        boolean isMerged = (partnerTable != null);

        // 获取顾客组ID（优先从当前餐桌，失败则尝试伙伴餐桌）
        Integer groupId = table.getCurrentGroupId();
        if (groupId == null && partnerTable != null) {
            groupId = partnerTable.getCurrentGroupId();
        }
        if (groupId == null) {
            System.err.println(" 餐桌 #" + displayId + " 无关联顾客组，无法完成离店");
            return false;
        }

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            orderDAO.deleteTableOrdersByTableId(conn, table.getTableId());
            // === 步骤1: 更新主餐桌状态 ===
            boolean tableUpdated = tablesDAO.updateTableStatusForDeparture(
                    table.getTableId(), "SETTING_UP", null, 0,
                    table.getTableType().name());
            if (!tableUpdated) throw new SQLException("更新主餐桌状态失败");

            // === 步骤2: 更新伙伴餐桌状态（如果存在）===
            if (partnerTable != null) {
                boolean partnerUpdated = tablesDAO.updateTableStatusForDeparture(
                        partnerTable.getTableId(), "SETTING_UP", null, 0,
                        table.getTableType().name());
                if (!partnerUpdated) throw new SQLException("更新伙伴餐桌状态失败");
            }

            // === 步骤3: 删除顾客组（只删除一次）===
            boolean groupDeleted = customerGroupDAO.delete(conn, groupId);
            if (!groupDeleted) throw new SQLException("删除顾客组失败");

            conn.commit();  // 事务提交后再更新内存

            // ===  步骤4: 事务成功后再更新内存状态（防止回滚不一致）===
            updateTableMemoryStateForDeparture(table);
            if (partnerTable != null) {
                updateTableMemoryStateForDeparture(partnerTable);
            }

            // === 通知UI更新 ===
            notifyTableChanged(table);
            if (partnerTable != null) {
                notifyTableChanged(partnerTable);
                checkAndAssignWaitingCustomers(); // 触发队列分配
            }

            System.out.println(" 顾客组 #" + groupId + " 离店成功，餐桌 #" +
                    displayId + (isMerged ? " 和 #" + partnerTable.getDisplayId() : "") +
                    " 已更新为准备中状态");
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            System.err.println(" finishMeal 操作失败: " + e.getMessage());
            e.printStackTrace();
            return false;
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



    /**
     * 更新餐桌内存状态（离店专用）
     */

    private void updateTableMemoryStateForDeparture(Tables table) {
        // 1. 清除顾客组关联
        table.setCurrentGroup(null);
        table.setCurrentGroupId(null);
        // 2. 设置结束时间
        table.setEndTime(LocalDateTime.now());
        // 3. 重置状态
        table.setStatus(Tables.TableStatus.SETTING_UP);
        table.setActualSeats(0);
        // 4. 清除合并关系
        table.setMergedWith(null);

        if (table.getTableType() == Tables.TableType.MERGED) {
            table.setTableType(Tables.TableType.MAIN); // 仅合并餐桌恢复为主桌
        }
        // SUBTABLE 和 MAIN 保持原类型不变

        table.setOrderStatus(Tables.OrderStatus.NO_ORDER);

        // 5. 同步更新 tables 列表中的引用
        for (int i = 0; i < tables.size(); i++) {
            Tables listTable = tables.get(i);
            if (listTable.getDisplayId().equals(table.getDisplayId())) {
                if (listTable != table) {
                    listTable.setStatus(table.getStatus());
                    listTable.setMergedWith(table.getMergedWith());
                    // 仅当原类型是 MERGED 时才更新 tableType
                    if (table.getTableType() == Tables.TableType.MAIN &&
                            listTable.getTableType() == Tables.TableType.MERGED) {
                        listTable.setTableType(Tables.TableType.MAIN);
                    }
                    listTable.setCurrentGroup(null);
                    listTable.setCurrentGroupId(null);
                    listTable.setEndTime(table.getEndTime());
                    listTable.setActualSeats(0);
                    listTable.setOrderStatus(Tables.OrderStatus.NO_ORDER);
                }
                break;
            }
        }
    }

    public void notifyTableChanged(Tables table) {
        for (ModelChangeListener listener : listeners) {
            listener.onTableChanged(table);
        }
    }

    public void notifyStructuralChange() {
        for (ModelChangeListener listener : listeners) {
            listener.onStructuralChange();
        }
    }

    public void cleanTable(String displayId) throws SQLException {
        // 1. 验证并清理餐桌
        Tables table = getTableById(displayId);
        if (table == null) throw new IllegalArgumentException("餐桌 #" + displayId + " 不存在");
        if (table.getStatus() != Tables.TableStatus.SETTING_UP)
            throw new IllegalArgumentException("餐桌 #" + displayId + " 不处于可清理状态");

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            table.setStatus(Tables.TableStatus.VACANT);
            table.setCurrentGroup(null);
            table.setCurrentGroupId(null);
            table.setStartTime(null);
            table.setEndTime(null);
            table.setActualSeats(0);
            table.setOrderStatus(Tables.OrderStatus.NO_ORDER);
            tablesDAO.update(table);
            conn.commit();
        }

        notifyTableChanged(table); // 局部刷新当前餐桌

        // 2. 检测子桌合并条件
        String suffix = table.getSubTableSuffix();
        if (suffix == null || suffix.isEmpty()) {
            checkAndAssignWaitingCustomers();
            return;
        }

        String mainId = displayId.substring(0, displayId.length() - 1);
        String siblingId = mainId + ("a".equals(suffix) ? "b" : "a");
        Tables sibling = getTableById(siblingId);
        boolean canMerge = (sibling != null && sibling.getStatus() == Tables.TableStatus.VACANT);

        // 3. 弹窗询问并执行合并（合并成功后触发全局刷新）
        if (canMerge) {
            SwingUtilities.invokeLater(() -> {
                int confirm = JOptionPane.showConfirmDialog(null,
                        "两张子桌 (" + displayId + " 和 " + siblingId + ") 现在都空闲，是否合并为主桌？",
                        "合并确认", JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    new Thread(() -> {
                        try {
                            if (recombineTables(mainId)) {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(null,
                                            " 已成功合并子桌 " + displayId + " 和 " + siblingId +
                                                    " 为主桌 #" + mainId, "合并成功", JOptionPane.INFORMATION_MESSAGE);


                                    notifyStructuralChange(); // ← 新增方法

                                    checkAndAssignWaitingCustomers();
                                });
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(null,
                                            "合并失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE));
                        }
                    }).start();
                } else {
                    checkAndAssignWaitingCustomers();
                }
            });
        } else {
            checkAndAssignWaitingCustomers();
        }
    }

    public Tables getTableById(int tableId) {
        for (Tables table : tables) {
            if (table.getTableId() == tableId) {
                return table;
            }
        }
        return null;
    }

    public int getNextCallNumber() {
        return nextCallNumber;
    }

    public boolean isOpenForBusiness() {
        return isOpenForBusiness;
    }

    public void setOpenForBusiness(boolean open) {
        isOpenForBusiness = open;
    }

    public Tables getTableById(String displayId) {
        return tableMap.get(displayId);
    }

    // 为UI提供队列数据
    public Queue<CustomerGroup> getQueue2Seat() {
        synchronized (queueLock) {
            return new LinkedList<>(queue2Seat);
        }
    }

    public Queue<CustomerGroup> getQueue4Seat() {
        synchronized (queueLock) {
            return new LinkedList<>(queue4Seat);
        }
    }

    public Queue<CustomerGroup> getQueue6Seat() {
        synchronized (queueLock) {
            return new LinkedList<>(queue6Seat);
        }
    }

    private void enqueueGroupTransactional(Connection conn, CustomerGroup group) throws SQLException {
        String queueType = resolveQueueType(group.getSize());
        int position = queueDAO.getNextQueuePosition(conn, queueType);

        queueDAO.insertQueue(conn, queueType, group.getGroup_id(), position);
        queueDAO.updateQueuePositions(conn, queueType); // 同一个 conn
    }

    private String resolveQueueType(int groupSize) {
        if (groupSize <= 2) {
            return "2_SEAT";
        } else if (groupSize <= 4) {
            return "4_SEAT";
        } else if (groupSize <= 9) {
            return "6_SEAT";
        } else {
            throw new IllegalArgumentException("不支持的顾客组人数: " + groupSize);
        }
    }

    /**
     * 处理餐桌分配
     */

    private void processTableAssignment(Connection conn, CustomerGroup group, Tables table) throws SQLException {
        synchronized (table) {
            try {
                // 1. 更新数据库
                boolean tableUpdated = tablesDAO.updateTableStatus(
                        table.getTableId(),
                        "OCCUPIED",
                        group.getGroup_id(),
                        group.getSize()
                );
                if (!tableUpdated) {
                    throw new SQLException("更新餐桌状态失败");
                }

                boolean groupUpdated = customerGroupDAO.updateAssignmentStatus(
                        group.getGroup_id(),
                        table.getTableId(),
                        true,
                        false
                );
                if (!groupUpdated) {
                    throw new SQLException("更新顾客组状态失败");
                }
                businessStatusDAO.incrementDailyTotalCustomers(conn, group.getSize(), LocalDate.now());
                // 2. 更新内存状态 - 使用新方法确保一致性
                Tables memoryTable = tableMap.get(table.getDisplayId());
                if (memoryTable == null) {
                    throw new IllegalStateException("tableMap 中不存在 displayId=" + table.getDisplayId());
                }

                synchronized (memoryTable) {
                    memoryTable.setStatus(Tables.TableStatus.OCCUPIED);
                    // 关键修复：使用新方法同时设置组对象和ID
                    memoryTable.assignCustomerGroup(group);
                    memoryTable.setActualSeats(group.getSize());
                }

                // 3. 更新顾客组状态
                group.setAssigned(true);
                group.setTableId(memoryTable.getTableId());

                // 4. 从队列中移除
                removeFromAnyQueue(conn, group.getGroup_id());
                // 5. 验证一致性
                if (!memoryTable.isConsistent()) {
                    throw new IllegalStateException("餐桌 #" + memoryTable.getDisplayId() +
                            " 分配后状态不一致: ID=" + memoryTable.getCurrentGroupId() +
                            ", GroupID=" + (group != null ? group.getGroup_id() : "null"));
                }

                System.out.println("✅ 顾客组 #" + group.getCallNumber() +
                        " 已分配到餐桌 #" + memoryTable.getDisplayId() +
                        " (内存组ID: " + memoryTable.getCurrentGroupId() + ")");

            } catch (SQLException e) {
                // 出错时重置状态
                table.setStatus(Tables.TableStatus.VACANT);
                table.setCurrentGroup(null);
                table.setCurrentGroupId(null);
                throw e;
            }
        }
    }

    /**
     * 從任何隊列中移除指定的顧客組（同時更新數據庫和內存）
     */

    private void removeFromAnyQueue(Connection conn, int groupId) throws SQLException {
        synchronized (queueLock) {
            // 1. 查詢顧客組所在的隊列類型
            String queueType = queueDAO.findQueueTypeByGroupId(conn, groupId);

            // 2. 如果找到隊列類型，使用現有方法移除
            if (queueType != null) {
                // 從數據庫獲取完整的顧客組對象
                CustomerGroup group = customerGroupDAO.findById(groupId);
                if (group != null) {
                    removeFromQueue(conn, group, queueType);
                    System.out.println("從 " + queueType + " 隊列中移除顧客組 #" + groupId);
                }
            }
        }
    }

    /**
     * 从数据库加载队列数据
     */
    private void loadQueuesFromDatabase() {
        synchronized (queueLock) {
            queue2Seat.clear();
            queue4Seat.clear();
            queue6Seat.clear();
        }

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();

            // 加载2人桌队列
            loadSingleQueueFromDatabase(conn, "2_SEAT", queue2Seat);
            // 加载4人桌队列
            loadSingleQueueFromDatabase(conn, "4_SEAT", queue4Seat);
            // 加载6人桌队列
            loadSingleQueueFromDatabase(conn, "6_SEAT", queue6Seat);

            System.out.println("成功加载队列数据: 2人桌(" + queue2Seat.size() + "), 4人桌(" + queue4Seat.size() + "), 6人桌(" + queue6Seat.size() + ")");
        } catch (SQLException e) {
            System.err.println("加载队列数据失败: " + e.getMessage());
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

    /**
     * 加载单个队列
     */
    // RestaurantModel.java - 重构后（一行代码委托 DAO）
    private void loadSingleQueueFromDatabase(Connection conn, String queueType, Queue<CustomerGroup> queue) throws SQLException {
        // ✅ 核心修复：委托 DAO 执行查询，Model 零 SQL
        List<CustomerGroup> groups = queueDAO.loadQueueByType(conn, queueType);

        // 仅负责业务逻辑：添加到内存队列
        queue.addAll(groups);

        System.out.println(" 加载 " + queueType + " 队列: " + groups.size() + " 个顾客组");
    }

    public void checkAndAssignWaitingCustomers() {
        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);

            boolean assigned = false;

            // 检查2人桌队列
            assigned |= assignFromQueueIfPossible(conn, queue2Seat, "2_SEAT");
            // 检查4人桌队列
            assigned |= assignFromQueueIfPossible(conn, queue4Seat, "4_SEAT");
            // 检查6人桌队列
            assigned |= assignFromQueueIfPossible(conn, queue6Seat, "6_SEAT");

            if (assigned) {
                conn.commit();

            } else {
                conn.rollback();
            }
        } catch (SQLException e) {
            System.err.println("分配等待顾客失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean assignFromQueueIfPossible(Connection conn, Queue<CustomerGroup> queue, String queueType) throws SQLException {
        synchronized (queueLock) {
            if (queue.isEmpty()) {
                return false;
            }
            CustomerGroup firstGroup = queue.peek();
            if (firstGroup == null) {
                return false;
            }

            // 1. 尝试分配单张餐桌
            Tables assignedTable = tryAssignTableToGroup(conn, firstGroup);
            boolean assigned = false;

            if (assignedTable != null) {
                // ✅ 执行实际分配操作
                processTableAssignment(conn, firstGroup, assignedTable);
                assigned = true;
            }
            // 2. 单桌分配失败 → 尝试合并
            else {
                int groupSize = firstGroup.getSize();
                // 3-4人：尝试合并2人桌
                if (groupSize >= 3 && groupSize <= 4) {
                    assigned = tryMergeTablesByCapacity(firstGroup, 2);
                }
                // 5-8人：尝试合并4人桌
                else if (groupSize >= 5 && groupSize <= 8) {
                    assigned = tryMergeTablesByCapacity(firstGroup, 4);
                }
            }

            // 3. 如果成功分配，从队列中移除
            if (assigned) {
                //queue.poll();
                // 更新队列位置
                int position = 1;
                for (CustomerGroup g : queue) {
                    g.setPosition(position++);
                }
            }

            return assigned; // ✅ 修复：正确返回分配结果
        }
    }

    /**
     * 从指定类型的队列中移除顾客组（业务层：协调 DAO + 内存）
     */
    private void removeFromQueue(Connection conn, CustomerGroup group, String queueType) throws SQLException {
        synchronized (queueLock) {
            //  步骤1: 委托 DAO 执行纯数据库操作（含 WHERE queue_type 限定）
            queueDAO.removeFromQueue(conn, group.getGroup_id(), queueType);

            //  步骤2: 仅更新内存状态（无 SQL）
            Queue<CustomerGroup> targetQueue = getQueueByType(queueType);
            targetQueue.removeIf(g -> g.getGroup_id() == group.getGroup_id());

            //  步骤3: 仅更新内存位置（与数据库保持一致）
            int position = 1;
            for (CustomerGroup g : targetQueue) {
                g.setPosition(position++);
            }

            //  通知监听器
            for (ModelChangeListener listener : listeners) {
                listener.onQueueChanged();
            }
        }
    }

    /**
     * 根据队列类型获取对应的内存队列
     */
    private Queue<CustomerGroup> getQueueByType(String queueType) {
        return switch (queueType) {
            case "2_SEAT" -> queue2Seat;
            case "4_SEAT" -> queue4Seat;
            default -> queue6Seat; // "6_SEAT"
        };
    }


    /**
     * 拆分餐桌（2人桌拆分成两个1人桌，4人桌拆分成两个2人桌）
     */

    public boolean splitTable(String displayId) throws SQLException {
        // ✅ 修复1: 从 tableMap 获取真实引用（而非 DAO 查询）
        Tables mainTable = tableMap.get(displayId);  // ← 关键：使用内存真实引用
        if (mainTable == null) {
            throw new IllegalArgumentException("餐桌 #" + displayId + " 不存在");
        }

        // 業務規則驗證（保持不变）
        if (mainTable.getSubTableSuffix() != null) {
            throw new IllegalStateException("子桌不能被拆分");
        }
        if (mainTable.getCapacity() != 2 && mainTable.getCapacity() != 4) {
            throw new IllegalStateException("只能拆分2人或4人桌，當前餐桌容量為" + mainTable.getCapacity() + "人");
        }
        if (mainTable.getStatus() != Tables.TableStatus.VACANT) {
            throw new IllegalStateException("只能拆分空閒狀態的餐桌，當前狀態: " + mainTable.getStatus().getDisplayName());
        }
        if (mainTable.isSplit()) {
            throw new IllegalStateException("餐桌 #" + displayId + " 已經處於拆分狀態");
        }

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. 更新主桌狀態（仅数据库操作）
                boolean statusUpdated = tablesDAO.updateSplitStatus(mainTable.getTableId(), true);
                if (!statusUpdated) {
                    throw new SQLException("更新主桌拆分狀態失敗");
                }

                // ⚠️ 删除以下两行！事务提交前不应修改内存
                // mainTable.setSplit(true);
                // mainTable.setStatus(Tables.TableStatus.SPLITTING);

                // 2. 創建兩個子桌
                Tables subTableA = createSubTable(mainTable, "a");
                Tables subTableB = createSubTable(mainTable, "b");

                // 3. 保存子桌到數據庫
                subTableA = tablesDAO.save(subTableA);
                subTableB = tablesDAO.save(subTableB);

                conn.commit();  //  事务提交成功后再更新内存

                // 4.  修复2: 事务成功后统一更新内存
                updateMemoryTablesAfterSplit(mainTable, subTableA, subTableB);

                System.out.println(" 餐桌 #" + displayId + " 拆分成功");
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private Tables createSubTable(Tables mainTable, String suffix) {
        int subCapacity = mainTable.getCapacity() / 2;
        Tables subTable = new Tables(mainTable.getBaseId(), subCapacity, mainTable.getDisplayId() + suffix);

        //  关键：设置物理容量（避免后续查询时为null）
        subTable.setPhysicalCapacity(subCapacity);
        subTable.setTableType(Tables.TableType.SUBTABLE);
        subTable.setMainTableId(mainTable.getTableId());
        subTable.setSubTableSuffix(suffix);
        subTable.setStatus(Tables.TableStatus.VACANT);
        subTable.setSplit(false);

        // 新增：显式设置 mergedWith 为 null（防御性编程）
        subTable.setMergedWith(null);

        return subTable;
    }

    /**
     * 拆分后更新内存状态（仅更新数据结构，不触发局部刷新）
     *
     * @note 拆分是结构性变化（新增组件），必须通过全量刷新创建UI
     */
    private void updateMemoryTablesAfterSplit(Tables mainTable, Tables subTableA, Tables subTableB) {
        // 1. 更新主桌内存状态
        Tables memoryMainTable = tableMap.get(mainTable.getDisplayId());
        if (memoryMainTable != null) {
            memoryMainTable.setSplit(true);
            memoryMainTable.setStatus(Tables.TableStatus.SPLITTING);
            memoryMainTable.setCurrentGroup(null);
            memoryMainTable.setCurrentGroupId(null);

            // 同步 tables 列表中的引用
            for (int i = 0; i < tables.size(); i++) {
                Tables listTable = tables.get(i);
                if (listTable.getDisplayId().equals(memoryMainTable.getDisplayId())) {
                    if (listTable != memoryMainTable) {
                        listTable.setSplit(true);
                        listTable.setStatus(Tables.TableStatus.SPLITTING);
                    }
                    break;
                }
            }
        }

        // 2. 添加子桌到内存数据结构
        tables.add(subTableA);
        tables.add(subTableB);
        tableMap.put(subTableA.getDisplayId(), subTableA);
        tableMap.put(subTableB.getDisplayId(), subTableB);

        // 3.  核心修复：不触发 onTableChanged！
        //    拆分是结构性变化（新增组件），局部刷新无法处理
        //    仅触发 onQueueChanged 间接触发全量刷新
        SwingUtilities.invokeLater(() -> {
            for (ModelChangeListener listener : listeners) {
                listener.onQueueChanged();
            }
        });

        System.out.println("拆分完成，触发全量刷新以创建子桌UI组件");
    }

    /**
     * 合并餐桌（将拆分的子桌合并回主桌）
     */
    public boolean recombineTables(String mainTableDisplayId) throws SQLException {
        // 1. 找到主桌
        Tables mainTable = tablesDAO.findByDisplayId(mainTableDisplayId);
        if (mainTable == null || !mainTable.isSplit()) {
            throw new IllegalArgumentException("主桌 #" + mainTableDisplayId + " 不存在或未被拆分");
        }

        // 2. 獲取所有子桌
        List<Tables> subTables = tablesDAO.findSubTablesByMainId(mainTable.getTableId());
        if (subTables.size() < 2) {
            throw new IllegalStateException("主桌應至少有兩個子桌");
        }

        // 3. 驗證子桌狀態
        for (Tables subTable : subTables) {
            if (subTable.getStatus() != Tables.TableStatus.VACANT) {
                throw new IllegalStateException("子桌 #" + subTable.getDisplayId() +
                        " 必須處於空閒狀態才能合併，當前狀態: " + subTable.getStatus().getDisplayName());
            }
        }

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 4. 刪除子桌（直接执行，无需清理顾客组）
                List<Integer> subTableIds = subTables.stream()
                        .map(Tables::getTableId)
                        .collect(Collectors.toList());
                tablesDAO.deleteSubTables(subTableIds);

                // 5. 恢復主桌狀態
                mainTable.setStatus(Tables.TableStatus.VACANT);
                mainTable.setSplit(false);
                tablesDAO.update(mainTable);

                // 6. 更新內存狀態
                updateMemoryTablesAfterRecombine(mainTable, subTableIds);

                conn.commit();
                System.out.println("✅ 餐桌 #" + mainTableDisplayId + " 合併成功");
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * 合併後更新內存中的餐桌狀態
     */
    private void updateMemoryTablesAfterRecombine(Tables mainTable, List<Integer> subTableIds) {
        // 1. 從內存中移除子桌
        subTableIds.forEach(id -> {
            Tables subTable = tables.stream()
                    .filter(t -> t.getTableId() == id)
                    .findFirst()
                    .orElse(null);

            if (subTable != null) {
                tables.remove(subTable);
                tableMap.remove(subTable.getDisplayId());
            }
        });

        // 2. 確保主桌在內存中已更新
        Tables memoryMainTable = tableMap.get(mainTable.getDisplayId());
        if (memoryMainTable != null) {
            memoryMainTable.setSplit(false);
            memoryMainTable.setStatus(Tables.TableStatus.VACANT);
        }

        // 3. 通知監聽器更新UI
        for (ModelChangeListener listener : listeners) {
            listener.onTableChanged(mainTable);
            // 通知隊列已更改
            listener.onQueueChanged();
        }
    }

    /**
     * 為大顧客組嘗試合併餐桌
     *
     * @param group            顧客組
     * @param requiredCapacity 每張餐桌所需容量（2人或4人）
     * @return 是否成功分配
     */
    private boolean tryMergeTablesByCapacity(CustomerGroup group, int requiredCapacity) {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 1. 查找相鄰的可用餐桌對
            List<List<Tables>> adjacentPairs = tablesDAO.findAdjacentAvailableTables(requiredCapacity, 3);

            if (adjacentPairs.isEmpty()) {
                return false;
            }

            // 2. 選擇第一對相鄰餐桌
            List<Tables> selectedPair = adjacentPairs.get(0);
            Tables mainTable = selectedPair.get(0);
            Tables partnerTable = selectedPair.get(1);

            // 3. 確保mainTable是編號較小的餐桌（主餐桌）
            if (mainTable.getBaseId() > partnerTable.getBaseId()) {
                Tables temp = mainTable;
                mainTable = partnerTable;
                partnerTable = temp;
            }

            // 4. 計算每張餐桌的實際座位數
            int groupSize = group.getSize();
            int seatsForMain = Math.min(groupSize, mainTable.getPhysicalCapacity());
            int seatsForPartner = groupSize - seatsForMain;

            // 5. 更新餐桌合併狀態（數據庫層）
            boolean tablesUpdated = tablesDAO.updateMergeStatus(
                    mainTable.getTableId(),
                    partnerTable.getTableId(),
                    partnerTable.getDisplayId(),
                    mainTable.getDisplayId(),
                    group.getGroup_id(),
                    seatsForMain,
                    seatsForPartner
            );

            if (!tablesUpdated) {
                throw new SQLException("更新餐桌合併狀態失敗");
            }

            // 6. 更新顧客組分配狀態 - 關鍵：只關聯到主餐桌
            boolean groupUpdated = customerGroupDAO.updateAssignmentStatus(
                    group.getGroup_id(),
                    mainTable.getTableId(), // 只設置主餐桌ID
                    true,
                    false
            );

            if (!groupUpdated) {
                throw new SQLException("更新顧客組狀態失敗");
            }


            businessStatusDAO.incrementDailyTotalCustomers(conn, group.getSize(), LocalDate.now());
            // 7. 更新內存狀態
            updateMemoryForMergedTables(mainTable, partnerTable, group, seatsForMain, seatsForPartner);

            conn.commit();
            System.out.println("成功為顧客組 #" + group.getCallNumber() +
                    " 合併餐桌 #" + mainTable.getDisplayId() +
                    " 和 #" + partnerTable.getDisplayId());
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            System.err.println("合併餐桌失敗: " + e.getMessage());
            return false;
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

    /**
     * 更新內存中的合併餐桌狀態
     */
    public void updateMemoryForMergedTables(Tables mainTable, Tables partnerTable,
                                            CustomerGroup group, int seatsForMain, int seatsForPartner) {
        // 1. 獲取內存中的餐桌對象
        Tables memoryMainTable = tableMap.get(mainTable.getDisplayId());
        Tables memoryPartnerTable = tableMap.get(partnerTable.getDisplayId());

        if (memoryMainTable == null || memoryPartnerTable == null) {
            throw new IllegalStateException("內存中找不到餐桌對象");
        }

        synchronized (memoryMainTable) {
            synchronized (memoryPartnerTable) {
                // 2. 設置餐桌狀態
                memoryMainTable.setStatus(Tables.TableStatus.OCCUPIED);
                memoryPartnerTable.setStatus(Tables.TableStatus.OCCUPIED);

                // 3. 設置餐桌類型為合併
                memoryMainTable.setTableType(Tables.TableType.MERGED);
                memoryPartnerTable.setTableType(Tables.TableType.MERGED);

                // 4. 設置合併關係
                memoryMainTable.setMergedWith(memoryPartnerTable.getDisplayId());
                memoryPartnerTable.setMergedWith(memoryMainTable.getDisplayId());

                // 5. 設置實際座位數
                memoryMainTable.setActualSeats(seatsForMain);
                memoryPartnerTable.setActualSeats(seatsForPartner);

                // 6. 設置當前顧客組
                memoryMainTable.assignCustomerGroup(group);
                memoryPartnerTable.assignCustomerGroup(group);

                // 7. 設置開始時間
                LocalDateTime now = LocalDateTime.now();
                memoryMainTable.setStartTime(now);
                memoryPartnerTable.setStartTime(now);
            }
        }

        // 8. 更新顧客組狀態 - 只設置主餐桌ID
        group.setAssigned(true);
        group.setTableId(mainTable.getTableId());

        // 9. 從所有隊列中移除
        removeFromAllQueues(group);
    }

    /**
     * 获取合并餐桌的伙伴（双向查找）
     */
    public Tables getMergedPartnerTable(String displayId) {
        Tables current = getTableById(displayId);
        if (current == null) return null;

        // 情况1: 当前是合并子桌 → 找主桌
        if (current.getTableType() == Tables.TableType.MERGED &&
                current.getMergedWith() != null && !current.getMergedWith().isEmpty()) {
            return getTableById(current.getMergedWith());
        }

        // 情况2: 当前是主桌 → 找合并到它的子桌
        for (Tables t : tables) {  // tables 是 List<Tables>
            if (t.getTableType() == Tables.TableType.MERGED &&
                    t.getMergedWith() != null &&
                    t.getMergedWith().equals(displayId) &&
                    t.getStatus() == Tables.TableStatus.OCCUPIED) {
                return t;
            }
        }
        return null;
    }

    /**
     * 從所有隊列中移除指定的顧客組
     */
    private void removeFromAllQueues(CustomerGroup group) {
        synchronized (queueLock) {
            // 從所有可能的隊列中移除該顧客組
            queue2Seat.removeIf(g -> g.getGroup_id() == group.getGroup_id());
            queue4Seat.removeIf(g -> g.getGroup_id() == group.getGroup_id());
            queue6Seat.removeIf(g -> g.getGroup_id() == group.getGroup_id());

            // 同時從數據庫隊列表中移除
            try (Connection conn = ConnectionPool.getConnection()) {
                removeFromAnyQueue(conn, group.getGroup_id());
            } catch (SQLException e) {
                System.err.println("從隊列中移除顧客組失敗: " + e.getMessage());
            }
        }
    }

    /**
     * 检查是否有等待队列中的顾客
     *
     * @return true=有顾客在排队，false=队列为空
     */
    public boolean hasWaitingCustomers() {
        return !queue2Seat.isEmpty() || !queue4Seat.isEmpty() || !queue6Seat.isEmpty();
    }

    /**
     * 查找可自动分裂的占用中餐桌（纯内存查询，零SQL）
     *
     * @param newGroupSize 新顾客组人数（1-2人）
     * @return 符合条件的餐桌，无则返回null
     */
    private Tables findSplittableTableForGroup(int newGroupSize) {
        if (newGroupSize < 1 || newGroupSize > 2) {
            return null;
        }

        List<Tables> sortedTables = new ArrayList<>(tableMap.values());
        sortedTables.sort(Comparator.comparingInt(Tables::getBaseId));

        for (Tables table : sortedTables) {
            // 基础状态过滤
            if (table.getStatus() != Tables.TableStatus.OCCUPIED ||
                    table.isSplit() ||
                    table.getTableType() == Tables.TableType.MERGED ||
                    table.getSubTableSuffix() != null) {
                continue;
            }

            CustomerGroup existingGroup = table.getCurrentGroup();
            if (existingGroup == null) continue;
            int existingSize = existingGroup.getSize();

            // 仅支持2人桌和4人桌分裂
            int physicalCapacity = table.getCapacity(); // 假设capacity表示物理容量
            if (physicalCapacity != 2 && physicalCapacity != 4) continue;

            int subTableCapacity = physicalCapacity / 2;

            // 验证规则
            if (existingSize > subTableCapacity) continue;
            if (newGroupSize > subTableCapacity) continue;
            if (existingSize + newGroupSize > physicalCapacity) continue;
            if (physicalCapacity == 4 && existingSize == 3) continue;
            if (physicalCapacity == 2 && existingSize == 1 && newGroupSize > 1) continue;

            System.out.println(" 找到可分裂餐桌: #" + table.getDisplayId() +
                    " (当前" + existingSize + "人, 新增" + newGroupSize + "人)");
            return table;
        }
        return null;
    }


    /**
     * 事务提交前更新内存状态（防止回滚不一致）
     */
    /**
     * 事务提交前更新内存状态（防止回滚不一致）
     */
    private void updateMemoryAfterAutoSplit(
            Tables mainTable,
            int[] subTableIds,
            CustomerGroup existingGroup,
            CustomerGroup newGroup,
            LocalDateTime originalStartTime) {  // ← 新增参数

        // 1. 更新主桌内存状态
        Tables memoryMainTable = tableMap.get(mainTable.getDisplayId());
        if (memoryMainTable != null) {
            memoryMainTable.setSplit(true);
            memoryMainTable.setStatus(Tables.TableStatus.SPLITTING);
            memoryMainTable.setCurrentGroup(null);
            memoryMainTable.setCurrentGroupId(null);
            memoryMainTable.setStartTime(null);
        }

        // 2. 创建子桌A（原顾客组）- ✅ 关键修复点
        Tables subA = new Tables(
                mainTable.getBaseId(),
                mainTable.getPhysicalCapacity() / 2,
                mainTable.getDisplayId() + "a"
        );
        subA.setTableId(subTableIds[0]);
        subA.setTableType(Tables.TableType.SUBTABLE);
        subA.setSubTableSuffix("a");
        subA.setMainTableId(mainTable.getTableId());
        subA.setStatus(Tables.TableStatus.OCCUPIED);
        subA.assignCustomerGroup(existingGroup);
        existingGroup.setTableId(subA.getTableId());
        existingGroup.setAssigned(true);

        // ⚠️ 关键修复：使用传入的 originalStartTime 而不是 mainTable.getStartTime()
        subA.setStartTime(originalStartTime != null ? originalStartTime : LocalDateTime.now());
        subA.setActualSeats(existingGroup.getSize());
        subA.setPhysicalCapacity(mainTable.getPhysicalCapacity() / 2);
        subA.setOrderStatus(mainTable.getOrderStatus());

        // 3. 创建子桌B（新顾客组）
        Tables subB = new Tables(
                mainTable.getBaseId(),
                mainTable.getPhysicalCapacity() / 2,
                mainTable.getDisplayId() + "b"
        );
        subB.setTableId(subTableIds[1]);
        subB.setTableType(Tables.TableType.SUBTABLE);
        subB.setSubTableSuffix("b");
        subB.setMainTableId(mainTable.getTableId());
        subB.setStatus(Tables.TableStatus.OCCUPIED);
        subB.assignCustomerGroup(newGroup);
        newGroup.setTableId(subB.getTableId());
        newGroup.setAssigned(true);
        subB.setStartTime(LocalDateTime.now());
        subB.setActualSeats(newGroup.getSize());
        subB.setPhysicalCapacity(mainTable.getPhysicalCapacity() / 2);
        subB.setOrderStatus(Tables.OrderStatus.NO_ORDER);

        // 4. 加入全局映射
        tables.add(subA);
        tables.add(subB);
        tableMap.put(subA.getDisplayId(), subA);
        tableMap.put(subB.getDisplayId(), subB);
    }


    /**
     * 尝试自动分裂占用中的餐桌以容纳小型顾客组
     *
     * @param conn     当前事务连接
     * @param newGroup 新顾客组（1-2人）
     * @return true=分裂成功并分配，false=无法分裂
     */
    private boolean tryAutoSplitForGroup(Connection conn, CustomerGroup newGroup) throws SQLException {
        int newGroupSize = newGroup.getSize();

        // 步骤1: 内存查找符合条件的占用中餐桌
        Tables targetTable = findSplittableTableForGroup(newGroupSize);
        if (targetTable == null) {
            System.out.println("未找到可分裂的餐桌（顾客组大小: " + newGroupSize + "）");
            return false;
        }

        CustomerGroup existingGroup = targetTable.getCurrentGroup();
        if (existingGroup == null) {
            System.out.println("目标餐桌无关联顾客组: " + targetTable.getDisplayId());
            return false;
        }
        int existingSize = existingGroup.getSize();

        // 步骤2: 业务规则验证
        int physicalCapacity = targetTable.getCapacity(); // 假设capacity表示物理容量
        int subTableCapacity = physicalCapacity / 2;

        // 规则1: 分裂后每个子桌人数不超过子桌容量
        if (existingSize > subTableCapacity) {
            System.err.println("餐桌 #" + targetTable.getDisplayId() +
                    " 已有 " + existingSize + " 人，超过子桌容量 " + subTableCapacity + "，无法分裂");
            return false;
        }
        if (newGroupSize > subTableCapacity) {
            System.err.println("新顾客组 " + newGroupSize + " 人，超过子桌容量 " + subTableCapacity + "，无法分裂");
            return false;
        }

        // 规则2: 总人数不超过原餐桌物理容量
        if (existingSize + newGroupSize > physicalCapacity) {
            System.err.println("总人数 " + (existingSize + newGroupSize) +
                    " 超过餐桌物理容量 " + physicalCapacity + "，无法分裂");
            return false;
        }

        // 规则3: 4人桌已有3人时禁止分裂
        if (physicalCapacity == 4 && existingSize == 3) {
            System.err.println("4人桌已有3人，禁止分裂（避免不平衡）");
            return false;
        }

        // 关键修复：顾客组已在addCustomerGroup中保存，直接使用group_id
        int newGroupId = newGroup.getGroup_id();
        if (newGroupId <= 0) {
            throw new SQLException("顾客组ID无效: " + newGroupId + "，可能未在事务开始时正确保存");
        }

        LocalDateTime originalStartTime = targetTable.getStartTime();  // ← 新增

        // 步骤3: 通过DAO执行原子分裂操作
        int[] subTableIds = tablesDAO.splitOccupiedTable(
                conn,
                targetTable.getTableId(),
                existingGroup.getGroup_id(),
                newGroupId,  //  使用已有的group_id（不再重复保存）
                subTableCapacity
        );

        if (subTableIds == null || subTableIds[0] <= 0 || subTableIds[1] <= 0) {
            throw new SQLException("分裂操作返回无效子桌ID");
        }

        // 步骤4: 迁移原订单到子桌A
        if (subTableIds[0] > 0) {
            orderDAO.migrateOrdersToTable(
                    conn,
                    targetTable.getTableId(),
                    subTableIds[0]
            );
        }

        // 步骤5: 事务提交前更新内存状态
       // updateMemoryAfterAutoSplit(targetTable, subTableIds, existingGroup, newGroup);
        updateMemoryAfterAutoSplit(targetTable, subTableIds, existingGroup, newGroup, originalStartTime);

        // 步骤6: 从队列移除并标记为已分配
        removeFromAllQueues(newGroup);
        newGroup.setAssigned(true);

        System.out.println(" 餐桌 #" + targetTable.getDisplayId() +
                " 自动分裂成功: " + existingSize + "人 + " + newGroupSize + "人 -> " +
                "子桌A(" + existingSize + "人) + 子桌B(" + newGroupSize + "人)");

        return true;
    }

    /**
     * 将顾客组从一张餐桌转移到另一张空闲餐桌（MVC + DAO 实现）
     * <p>
     * 业务规则：
     * 1. 源餐桌必须是 OCCUPIED 状态且非 MERGED 类型（合并餐桌需通过主桌操作）
     * 2. 目标餐桌必须是 VACANT 状态且容量足够
     * 3. 6人桌规则：3人及以下顾客组不能转移到6人桌
     * 4. 换桌前必须无活跃订单（仅允许 NO_ORDER 或 CHECKED_OUT 状态）
     * 5. 合并餐桌（MERGED）不允许直接换桌，需先取消合并
     *
     * @param fromDisplayId 源餐桌显示ID（如 "7" 或 "7a"）
     * @param toDisplayId   目标餐桌显示ID（如 "8" 或 "8b"）
     * @return 换桌成功返回 true，失败返回 false 并显示错误提示
     */
    public boolean changeTable(String fromDisplayId, String toDisplayId) {
        // 1. 基础验证：非空检查
        if (fromDisplayId == null || fromDisplayId.trim().isEmpty() ||
                toDisplayId == null || toDisplayId.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "餐桌编号不能为空", "输入错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        fromDisplayId = fromDisplayId.trim();
        toDisplayId = toDisplayId.trim();

        // 2. 从内存映射获取餐桌对象（避免重复查询）
        Tables fromTable = tableMap.get(fromDisplayId);
        Tables toTable = tableMap.get(toDisplayId);

        if (fromTable == null) {
            JOptionPane.showMessageDialog(null, "未找到源餐桌: " + fromDisplayId, "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (toTable == null) {
            JOptionPane.showMessageDialog(null, "未找到目标餐桌: " + toDisplayId, "错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        // 3. 业务规则验证
        // 规则1: 源餐桌必须是占用状态
        if (fromTable.getStatus() != Tables.TableStatus.OCCUPIED) {
            JOptionPane.showMessageDialog(null,
                    "源餐桌 #" + fromDisplayId + " 当前状态为【" + fromTable.getStatus().getDisplayName() + "】，无法换桌",
                    "无效操作", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // 规则2: 源餐桌不能是合并餐桌（避免数据不一致）
        if (fromTable.getTableType() == Tables.TableType.MERGED) {
            JOptionPane.showMessageDialog(null,
                    "合并餐桌不能直接换桌！\n请先通过主餐桌（编号较小的餐桌）操作，或先取消合并关系。",
                    "操作受限", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // 规则3: 目标餐桌必须是空闲状态
        if (toTable.getStatus() != Tables.TableStatus.VACANT) {
            JOptionPane.showMessageDialog(null,
                    "目标餐桌 #" + toDisplayId + " 当前状态为【" + toTable.getStatus().getDisplayName() + "】，不是空闲状态",
                    "无效操作", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // 规则4: 目标餐桌不能是合并餐桌
        if (toTable.getTableType() == Tables.TableType.MERGED) {
            JOptionPane.showMessageDialog(null,
                    "不能将顾客组转移到合并餐桌！\n请先取消合并关系或选择普通餐桌。",
                    "操作受限", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // 规则5: 检查订单状态（关键业务规则）
// ===== 修复：严格订单状态验证（仅允许未下单状态换桌）=====
        Tables.OrderStatus orderStatus = fromTable.getOrderStatus();
        if (orderStatus != Tables.OrderStatus.NO_ORDER) {
            String statusText = switch (orderStatus) {
                case ORDERED_UNFINISHED -> "有未完成订单";
                case ORDERED_FINISHED -> "有已完成但未结账订单";
                case CHECKED_OUT -> "订单已结账";
                default -> "有活跃订单";
            };

            JOptionPane.showMessageDialog(null,
                    "餐桌 #" + fromDisplayId + " " + statusText + "，无法换桌。\n" +
                            "结账后请直接执行离店操作，不可换桌。",
                    "操作受限", JOptionPane.WARNING_MESSAGE);
            return false;
        }
// ===== 验证通过：仅当 orderStatus == NO_ORDER 时允许换桌 =====

        // 规则6: 获取顾客组并验证
        CustomerGroup group = fromTable.getCurrentGroup();
        if (group == null || !group.isAssigned()) {
            JOptionPane.showMessageDialog(null,
                    "源餐桌 #" + fromDisplayId + " 无有效顾客组关联",
                    "数据错误", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        int groupSize = group.getSize();
        int targetCapacity = toTable.getCapacity();

        // 规则7: 6人桌特殊规则（3人及以下不能进6人桌）
        if (targetCapacity == 6 && groupSize < 4) {
            JOptionPane.showMessageDialog(null,
                    "只有4人及以上顾客组才能使用6人桌！\n当前顾客组人数: " + groupSize + "人",
                    "规则限制", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // 规则8: 容量验证
        if (groupSize > targetCapacity) {
            JOptionPane.showMessageDialog(null,
                    "顾客组人数(" + groupSize + "人) 超过目标餐桌容量(" + targetCapacity + "人)",
                    "容量不足", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // 4. 事务处理（核心：DAO层操作 + 内存状态同步）
        Connection conn = null;
        boolean success = false;

        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 4.1 更新源餐桌状态 → SETTING_UP（离店流程）
            boolean fromUpdated = tablesDAO.updateTableStatusForDeparture(
                    fromTable.getTableId(),
                    "SETTING_UP",
                    null,  // 清空顾客组ID
                    0,      // 实际座位数归零
                    fromTable.getTableType().name()
            );

            if (!fromUpdated) {
                throw new SQLException("更新源餐桌状态失败");
            }

            // 4.2 更新目标餐桌状态 → OCCUPIED
            boolean toUpdated = tablesDAO.updateTableStatus(
                    toTable.getTableId(),
                    "OCCUPIED",
                    group.getGroup_id(),  // 关联顾客组
                    groupSize             // 实际座位数
            );

            if (!toUpdated) {
                throw new SQLException("更新目标餐桌状态失败");
            }

            // 4.3 更新顾客组关联（关键：同步 table_id）
            boolean groupUpdated = customerGroupDAO.updateAssignmentStatus(
                    group.getGroup_id(),
                    toTable.getTableId(),  // 新餐桌ID
                    true,                  // 已分配
                    group.hasShownWaitMessage()
            );

            if (!groupUpdated) {
                throw new SQLException("更新顾客组餐桌关联失败");
            }

            // 4.5 提交事务
            conn.commit();
            success = true;

            // 5. 事务成功后更新内存状态（防止回滚不一致）
            updateMemoryAfterTableChange(fromTable, toTable, group, groupSize);

            // 6. 通知UI更新
            notifyTableChanged(fromTable);
            notifyTableChanged(toTable);

            System.out.println("✅ 换桌成功: #" + fromDisplayId + " → #" + toDisplayId +
                    " (顾客组 #" + group.getCallNumber() + ", " + groupSize + "人)");
            return true;

        } catch (SQLException e) {
            // 7. 事务回滚
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) { /* ignore */ }
            }

            String errorMsg = "换桌操作失败: " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();

            JOptionPane.showMessageDialog(null,
                    "换桌失败: " + e.getLocalizedMessage(),
                    "数据库错误", JOptionPane.ERROR_MESSAGE);
            return false;

        } finally {
            // 8. 释放连接
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) { /* ignore */ }
            }

            // 9. 事务失败时重置内存状态（仅当未成功时）
            if (!success) {
                // 无需特殊处理，内存状态未在事务前修改
            }
        }
    }

    /**
     * 事务提交后更新内存状态（确保与数据库一致）
     */
    private void updateMemoryAfterTableChange(Tables fromTable, Tables toTable,
                                              CustomerGroup group, int groupSize) {
        // 1. 更新源餐桌内存状态
        Tables memoryFrom = tableMap.get(fromTable.getDisplayId());
        if (memoryFrom != null) {
            memoryFrom.setStatus(Tables.TableStatus.SETTING_UP);
            memoryFrom.setCurrentGroup(null);
            memoryFrom.setCurrentGroupId(null);
            memoryFrom.setEndTime(LocalDateTime.now());
            memoryFrom.setActualSeats(0);
            memoryFrom.setOrderStatus(Tables.OrderStatus.NO_ORDER); // 重置订单状态

            // 同步 tables 列表
            for (Tables t : tables) {
                if (t.getDisplayId().equals(memoryFrom.getDisplayId())) {
                    t.setStatus(memoryFrom.getStatus());
                    t.setCurrentGroup(null);
                    t.setCurrentGroupId(null);
                    t.setEndTime(memoryFrom.getEndTime());
                    t.setActualSeats(0);
                    t.setOrderStatus(Tables.OrderStatus.NO_ORDER);
                    break;
                }
            }
        }

        // 2. 更新目标餐桌内存状态
        Tables memoryTo = tableMap.get(toTable.getDisplayId());
        if (memoryTo != null) {
            memoryTo.setStatus(Tables.TableStatus.OCCUPIED);
            memoryTo.assignCustomerGroup(group); // 同时设置组对象和ID
            memoryTo.setStartTime(LocalDateTime.now());
            memoryTo.setActualSeats(groupSize);
            // 订单状态继承源餐桌（理论上应为NO_ORDER或CHECKED_OUT）
            memoryTo.setOrderStatus(fromTable.getOrderStatus());

            // 同步 tables 列表
            for (Tables t : tables) {
                if (t.getDisplayId().equals(memoryTo.getDisplayId())) {
                    t.setStatus(memoryTo.getStatus());
                    t.assignCustomerGroup(group);
                    t.setStartTime(memoryTo.getStartTime());
                    t.setActualSeats(groupSize);
                    t.setOrderStatus(memoryTo.getOrderStatus());
                    break;
                }
            }
        }

        // 3. 更新顾客组状态
        group.setTableId(toTable.getTableId());
        group.setAssigned(true);
    }


    /**
     * 清空所有可清理的餐桌（4步静默流程）
     *
     * @return true=成功清理至少一张餐桌，false=没有可清理的餐桌
     * @throws SQLException 数据库操作异常
     */
    public boolean clearAllTables() throws SQLException {
        // === 第1步：检查是否有可清理的桌子 ===
        if (!hasCleanableTables()) {
            JOptionPane.showMessageDialog(null, "目前没有可清理的桌子", "提示", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        boolean cleanedAny = false;
        boolean subTablesCleaned = false;
        boolean mergedTablesCleaned = false;
        boolean mainTablesCleaned = false;

        // === 第2步：清理子桌（静默：无子桌可清理时不提示）===
        try {
            subTablesCleaned = cleanupSubTables();
            cleanedAny = cleanedAny || subTablesCleaned;
        } catch (SQLException e) {
            System.err.println("子桌清理失败: " + e.getMessage());
        }

        // === 第3步：清理合并桌（静默：无合并桌可清理时不提示）===
        try {
            mergedTablesCleaned = cleanupMergedTables();
            cleanedAny = cleanedAny || mergedTablesCleaned;
        } catch (SQLException e) {
            System.err.println("合并桌清理失败: " + e.getMessage());
        }

        // === 第4步：清理主桌（静默：无主桌可清理时不提示）===
        try {
            mainTablesCleaned = cleanupMainTables();
            cleanedAny = cleanedAny || mainTablesCleaned;
        } catch (SQLException e) {
            System.err.println("主桌清理失败: " + e.getMessage());
        }

        // === 最终汇总提示 ===
        if (cleanedAny) {
            // 构建清理摘要
            StringBuilder summary = new StringBuilder("清理完成:\n");
            if (subTablesCleaned) summary.append("✓ 子桌已清理\n");
            if (mergedTablesCleaned) summary.append("✓ 合并桌已清理\n");
            if (mainTablesCleaned) summary.append("✓ 主桌已清理");

            JOptionPane.showMessageDialog(null,
                    summary.toString().trim(),
                    "清理完成",
                    JOptionPane.INFORMATION_MESSAGE);

            // 仅当有排队顾客时才询问自动入座
            if (hasWaitingCustomers()) {
                int response = JOptionPane.showConfirmDialog(
                        null,
                        "检测到有顾客在排队，是否自动安排入座？",
                        "自动入座",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                if (response == JOptionPane.YES_OPTION) {
                    checkAndAssignWaitingCustomers();
                    JOptionPane.showMessageDialog(null, "已自动安排等待顾客入座", "提示", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } else {
            // 理论上不会发生（因第1步已检查），但防御性处理
            JOptionPane.showMessageDialog(null, "没有可清理的桌子", "提示", JOptionPane.INFORMATION_MESSAGE);
        }

        return cleanedAny;
    }


    /**
     * 判断是否存在可清理的餐桌
     * 核心修正：订单状态仅在 OCCUPIED 状态下参与判断
     */
    private boolean hasCleanableTables() {
        for (Tables table : getTables()) {
            Tables.TableStatus tableStatus = table.getStatus();
            Tables.OrderStatus orderStatus = table.getOrderStatus();
            Tables.TableType tableType = table.getTableType();

            // 不可清理条件 = (VACANT+Main) OR (OCCUPIED + 三种订单状态之一)
            boolean isUncleanable =
                    tableStatus == Tables.TableStatus.VACANT && tableType == Tables.TableType.MAIN ||
                            (tableStatus == Tables.TableStatus.OCCUPIED &&
                                    (orderStatus == Tables.OrderStatus.NO_ORDER ||
                                            orderStatus == Tables.OrderStatus.ORDERED_FINISHED ||
                                            orderStatus == Tables.OrderStatus.ORDERED_UNFINISHED));

            // 只要有一张桌子不满足不可清理条件 → 存在可清理桌子
            if (!isUncleanable) {
                return true;
            }
        }
        return false; // 所有桌子都不可清理
    }


    private boolean cleanupSubTables() throws SQLException {

        List<Tables> subTablesToDelete = collectSubTablesForDeletion();
        Map<Integer, List<Tables>> groupedByMainTable = subTablesToDelete.stream()
                .collect(Collectors.groupingBy(Tables::getMainTableId));

        //  修复：无子桌可清理时直接返回 false
        if (subTablesToDelete.isEmpty()) {
            return false;  // 静默跳过，不执行任何数据库操作
        }

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // === 关键修复：先清理顾客组和订单 ===
            for (Tables subTable : subTablesToDelete) {
                Integer groupId = subTable.getCurrentGroupId();
                if (groupId != null) {
                    customerGroupDAO.delete(conn, groupId);
                }
                orderDAO.deleteTableOrdersByTableId(conn, subTable.getTableId());
            }

            // 删除子桌记录
            List<Integer> tableIdsToDelete = subTablesToDelete.stream()
                    .map(Tables::getTableId)
                    .collect(Collectors.toList());
            tablesDAO.deleteSubTables(conn, tableIdsToDelete);

            // === 修复：使用 DAO 更新主桌状态 ===
            for (Map.Entry<Integer, List<Tables>> entry : groupedByMainTable.entrySet()) {
                Integer mainTableId = entry.getKey();
                List<Tables> remainingSubTables = tablesDAO.findSubTablesByMainId(conn, mainTableId);
                if (remainingSubTables.isEmpty()) {
                    // 从内存获取主桌对象
                    Tables mainTable = tables.stream()
                            .filter(t -> t.getTableId() == mainTableId)
                            .findFirst()
                            .orElse(null);

                    if (mainTable != null && mainTable.isSplit()) {
                        // 更新内存状态
                        mainTable.setStatus(Tables.TableStatus.VACANT);
                        mainTable.setSplit(false);
                        mainTable.setCurrentGroupId(null);
                        mainTable.setStartTime(null);
                        mainTable.setEndTime(null);
                        mainTable.setActualSeats(0);

                        // 使用 DAO 更新数据库
                        tablesDAO.update(mainTable);
                        System.out.println("✅ 主桌 #" + mainTable.getDisplayId() + " 已恢复为空闲状态");
                    }
                }
            }

            conn.commit();
            updateMemoryAfterSubTableDeletion(subTablesToDelete, groupedByMainTable);
            notifyStructuralChange();
            return true;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) { /* ignore */ }
            }
            throw new SQLException("子桌清理失败: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) { /* ignore */ }
            }
        }
    }

    /**
     * 判断单个子桌是否满足清理条件（三个条件之一）
     *
     * @note SETTING_UP 状态无条件可清理（客人已离店，不检查订单状态）
     */
    private boolean isSubTableDeletable(Tables subTable) {
        Tables.OrderStatus orderStatus = subTable.getOrderStatus();
        Tables.TableStatus tableStatus = subTable.getStatus();

        return orderStatus == Tables.OrderStatus.CHECKED_OUT ||          // 条件1: 已结账（无视status）
                tableStatus == Tables.TableStatus.SETTING_UP ||           // 条件2: 准备中（无视orderStatus，客人已走）
                (orderStatus == Tables.OrderStatus.NO_ORDER &&            // 条件3: 未下单+空闲
                        tableStatus == Tables.TableStatus.VACANT);
    }

    /**
     * 收集可清理的子桌（关键修复：按主桌分组验证）
     * 业务规则：同一主桌的所有子桌必须同时满足清理条件，才能执行清理
     * 允许子桌状态不同（如A=CHECKED_OUT, B=SETTING_UP），但每个子桌必须各自满足任一条件
     */
    private List<Tables> collectSubTablesForDeletion() {
        // 1. 按主桌ID分组所有子桌
        Map<Integer, List<Tables>> subTablesByMainTable = new HashMap<>();
        for (Tables table : getTables()) {
            if (table.getTableType() != Tables.TableType.SUBTABLE ||
                    table.getMainTableId() == null) {
                continue;
            }
            subTablesByMainTable
                    .computeIfAbsent(table.getMainTableId(), k -> new ArrayList<>())
                    .add(table);
        }

        // 2. 按主桌分组验证：所有子桌必须同时满足清理条件
        List<Tables> candidates = new ArrayList<>();
        for (Map.Entry<Integer, List<Tables>> entry : subTablesByMainTable.entrySet()) {
            Integer mainTableId = entry.getKey();
            List<Tables> subTables = entry.getValue();

            // ✅ 关键修复：检查该主桌的【所有】子桌是否都可清理
            boolean allDeletable = subTables.stream()
                    .allMatch(this::isSubTableDeletable);

            if (allDeletable) {
                // 仅当所有子桌都满足条件时，才将它们加入候选列表
                candidates.addAll(subTables);
                System.out.println("✅ 主桌 #" + mainTableId + " 的 " + subTables.size() +
                        " 个子桌全部满足清理条件: " +
                        subTables.stream()
                                .map(t -> t.getDisplayId() + "(" + t.getStatus() + "/" + t.getOrderStatus() + ")")
                                .collect(Collectors.joining(", ")));
            } else {
                // 任一子桌不满足条件 → 整组跳过（不清理任何子桌）
                System.out.println(" 主桌 #" + mainTableId + " 有子桌不满足清理条件，跳过整组: " +
                        subTables.stream()
                                .map(t -> t.getDisplayId() + "(" +
                                        (isSubTableDeletable(t) ? "✓" : "✗") +
                                        " " + t.getStatus() + "/" + t.getOrderStatus() + ")")
                                .collect(Collectors.joining(", ")));
            }
        }

        return candidates;
    }

    /**
     * 事务提交后更新内存状态
     */
    private void updateMemoryAfterSubTableDeletion(
            List<Tables> deletedSubTables,
            Map<Integer, List<Tables>> groupedByMainTable) {

        // 1. 从内存中移除子桌
        for (Tables subTable : deletedSubTables) {
            tables.removeIf(t -> t.getTableId() == subTable.getTableId());
            tableMap.remove(subTable.getDisplayId());
            System.out.println("✅ 内存清理: 子桌 #" + subTable.getDisplayId());
        }

        // 2. 恢复主桌内存状态
        for (Map.Entry<Integer, List<Tables>> entry : groupedByMainTable.entrySet()) {
            Integer mainTableId = entry.getKey();
            Tables mainTable = tables.stream()
                    .filter(t -> t.getTableId() == mainTableId)
                    .findFirst()
                    .orElse(null);

            if (mainTable != null && mainTable.isSplit()) {
                // 检查是否还有存活子桌（防御性检查）
                boolean hasRemaining = tables.stream()
                        .anyMatch(t -> t.getMainTableId() != null &&
                                t.getMainTableId().equals(mainTableId) &&
                                t.getTableType() == Tables.TableType.SUBTABLE);

                if (!hasRemaining) {
                    mainTable.setSplit(false);
                    mainTable.setStatus(Tables.TableStatus.VACANT);
                    mainTable.setCurrentGroup(null);
                    mainTable.setCurrentGroupId(null);
                    mainTable.setStartTime(null);
                    mainTable.setEndTime(null);
                    mainTable.setActualSeats(0);
                    mainTable.setOrderStatus(Tables.OrderStatus.NO_ORDER);
                    System.out.println("✅ 内存恢复: 主桌 #" + mainTable.getDisplayId() + " 已恢复为空闲状态");
                }
            }
        }
    }


    private boolean cleanupMergedTables() throws SQLException {
        List<Tables> mergedTables = getTables().stream()
                .filter(t -> t.getTableType() == Tables.TableType.MERGED)
                .collect(Collectors.toList());

        if (mergedTables.isEmpty()) {
            return false;
        }

        Set<String> processedPairs = new HashSet<>();
        boolean cleanedAny = false;

        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            for (Tables table : mergedTables) {
                String partnerId = table.getMergedWith();
                if (partnerId == null || partnerId.isEmpty()) continue;

                String pairKey = Stream.of(table.getDisplayId(), partnerId)
                        .sorted()
                        .collect(Collectors.joining("|"));
                if (processedPairs.contains(pairKey)) continue;
                processedPairs.add(pairKey);

                Tables partner = getTableById(partnerId);
                if (partner == null || partner.getTableType() != Tables.TableType.MERGED) {
                    continue;
                }

                // 只检查代表桌（较小）是否已结账
                Tables representative = table.getDisplayId().compareTo(partner.getDisplayId()) < 0
                        ? table : partner;
                if (representative.getOrderStatus() != Tables.OrderStatus.CHECKED_OUT) {
                    continue;
                }

                // 删除顾客组和订单（仅一次）
                if (representative.getCurrentGroupId() != null) {
                    customerGroupDAO.delete(conn, representative.getCurrentGroupId());
                }
                orderDAO.deleteTableOrdersByTableId(conn, representative.getTableId());

                tablesDAO.updateMergedPairToVacant(table.getTableId(), partner.getTableId(), conn);

                // 更新内存状态（两张都更新）
                for (Tables t : new Tables[]{table, partner}) {
                    t.setMergedWith(null);
                    t.setTableType(Tables.TableType.MAIN);
                    t.setStatus(Tables.TableStatus.VACANT); // 直接空闲
                    t.setCurrentGroupId(null);
                    t.setStartTime(null);
                    t.setEndTime(null);
                    t.setActualSeats(0);
                    t.setOrderStatus(Tables.OrderStatus.NO_ORDER);
                }

                cleanedAny = true;
                System.out.println("✅ 同时清理合并桌: " + table.getDisplayId() + " ↔ " + partner.getDisplayId());
            }

            conn.commit();
            if (cleanedAny) {
                notifyStructuralChange();
            }
            return cleanedAny;

        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }


    /**
     * 清理主桌
     * 业务规则：
     * - OCCUPIED + CHECKED_OUT → 删除顾客组+订单 → 变为 VACANT
     * - SETTING_UP → 直接变为 VACANT（顾客组/订单已在离店时清理）
     */
    private boolean cleanupMainTables() throws SQLException {
        // 收集可清理的主桌
        List<Tables> mainTablesToClean = getTables().stream()
                .filter(t -> t.getTableType() == Tables.TableType.MAIN &&
                        (t.getStatus() == Tables.TableStatus.SETTING_UP ||
                                (t.getStatus() == Tables.TableStatus.OCCUPIED &&
                                        t.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT)))
                .collect(Collectors.toList());

        if (mainTablesToClean.isEmpty()) {
            return false;
        }

        Connection conn = null;
        boolean cleanedAny = false;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            for (Tables table : mainTablesToClean) {
                // OCCUPIED + CHECKED_OUT：需要清理顾客组和订单
                if (table.getStatus() == Tables.TableStatus.OCCUPIED &&
                        table.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT) {

                    Integer groupId = table.getCurrentGroupId();
                    if (groupId != null) {
                        customerGroupDAO.delete(conn, groupId);
                    }
                    orderDAO.deleteTableOrdersByTableId(conn, table.getTableId());
                }

                // 重置主桌状态为 VACANT
                table.setStatus(Tables.TableStatus.VACANT);
                table.setCurrentGroupId(null);
                table.setStartTime(null);
                table.setEndTime(null);
                table.setActualSeats(0);
                table.setOrderStatus(Tables.OrderStatus.NO_ORDER);

                tablesDAO.update(table); // 使用 DAO 更新
                notifyTableChanged(table); //  修正：传入 table 参数
                cleanedAny = true;
                System.out.println(" 清理主桌: " + table.getDisplayId());
            }

            conn.commit();
            return cleanedAny;

        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }


    /**
     * 根据排队号查找顾客组（Model 层职责：封装数据访问逻辑）
     */
    public CustomerGroup findCustomerGroupByCallNumber(int callNumber) {
        // 优先检查已入座顾客（可能正在用餐）
        for (Tables table : tables) {
            if (table.getStatus() == Tables.TableStatus.OCCUPIED) {
                CustomerGroup group = table.getCurrentGroup();
                if (group != null && group.getCallNumber() == callNumber) {
                    return group;
                }
            }
        }

        // 检查等待队列
        for (CustomerGroup group : queue2Seat) {
            if (group.getCallNumber() == callNumber) return group;
        }
        for (CustomerGroup group : queue4Seat) {
            if (group.getCallNumber() == callNumber) return group;
        }
        for (CustomerGroup group : queue6Seat) {
            if (group.getCallNumber() == callNumber) return group;
        }
        return null;
    }

    /**
     * 从队列中移除顾客组并删除实体（仅用于用户主动放弃等待的场景）
     *
     * @param group 要删除的顾客组（必须处于等待队列中）
     * @throws SQLException             数据库操作失败
     * @throws IllegalArgumentException 顾客组不在队列中或已入座
     */
    public void removeCustomerGroupFromQueue(CustomerGroup group) throws SQLException {
        // 1. 验证顾客组状态：必须在等待队列中且未分配餐桌
        if (group.isAssigned()) {
            throw new IllegalArgumentException("顾客组 #" + group.getCallNumber() +
                    " 已分配餐桌，不能从队列中删除");
        }

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 2. 检查数据库中是否存在队列关联
                String queueType = queueDAO.findQueueTypeByGroupId(conn, group.getGroup_id());
                if (queueType == null) {
                    throw new IllegalArgumentException("顾客组 #" + group.getCallNumber() +
                            " 不在任何等待队列中，无法删除");
                }

                // 3. 从数据库队列移除
                queueDAO.removeFromQueue(conn, group.getGroup_id(), queueType);
                queueDAO.updateQueuePositions(conn, queueType); // 重排位置

                // 4. 修正：使用实际存在的 delete 方法（硬删除）
                boolean deleted = customerGroupDAO.delete(conn, group.getGroup_id());
                if (!deleted) {
                    throw new SQLException("删除顾客组实体失败: group_id=" + group.getGroup_id());
                }

                conn.commit();

                // 5. 事务成功后更新内存状态（防止回滚导致不一致）
                synchronized (this) {
                    // 从内存队列移除
                    Queue<CustomerGroup> queue = getQueueByType(queueType);
                    if (queue != null) {
                        queue.removeIf(g -> g.getGroup_id() == group.getGroup_id());
                        // 重置位置编号
                        int pos = 1;
                        for (CustomerGroup g : queue) {
                            g.setPosition(pos++);
                        }
                    }
                    // 从内存映射中移除
                    customerGroupMap.remove(group.getGroup_id());
                }

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * 事务性更新顾客组人数（Model 层职责：处理复杂业务规则）
     *
     * @param group   顾客组
     * @param newSize 新人数（1-9人）
     * @throws SQLException             数据库操作失败
     * @throws IllegalArgumentException 人数无效或顾客组已入座
     */
    public void updateCustomerGroupSize(CustomerGroup group, int newSize) throws SQLException {
        if (newSize <= 0 || newSize > 9) {
            throw new IllegalArgumentException("客户数量必须在1-9之间");
        }

        // 验证：已分配餐桌的顾客组不能修改人数
        if (group.isAssigned()) {
            throw new IllegalArgumentException(
                    "顾客组 #" + group.getCallNumber() + " 已分配餐桌，不能修改人数");
        }

        String currentQueueType = null; // 提升变量作用域到方法级
        String newQueueType = resolveQueueType(newSize);

        try (Connection conn = ConnectionPool.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. 获取当前队列类型
                currentQueueType = queueDAO.findQueueTypeByGroupId(conn, group.getGroup_id());
                if (currentQueueType == null) {
                    throw new IllegalArgumentException(
                            "顾客组 #" + group.getCallNumber() + " 不在等待队列中，无法编辑");
                }

                // 2. 从当前队列移除
                queueDAO.removeFromQueue(conn, group.getGroup_id(), currentQueueType);

                // 3. ✅ 修复2：使用实际存在的 update 方法（先更新内存对象，再持久化）
                group.setSize(newSize); // 更新内存对象
                boolean updated = customerGroupDAO.update(group); // 持久化到数据库
                if (!updated) {
                    throw new SQLException("更新顾客组人数失败: group_id=" + group.getGroup_id());
                }

                // 4. 重新入队（根据新大小）
                if (newQueueType != null) {
                    int position = queueDAO.getNextQueuePosition(conn, newQueueType);
                    queueDAO.insertQueue(conn, newQueueType, group.getGroup_id(), position);
                    queueDAO.updateQueuePositions(conn, newQueueType);
                }

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }

        // 5. 事务成功后更新内存状态（防止回滚不一致）
        synchronized (this) {
            // 从旧队列移除
            if (currentQueueType != null) {
                Queue<CustomerGroup> oldQueue = getQueueByType(currentQueueType);
                if (oldQueue != null) {
                    oldQueue.removeIf(g -> g.getGroup_id() == group.getGroup_id());
                }
            }

            // 加入新队列
            if (newQueueType != null) {
                Queue<CustomerGroup> newQueue = getQueueByType(newQueueType);
                if (newQueue != null) {
                    newQueue.add(group);
                    int pos = 1;
                    for (CustomerGroup g : newQueue) {
                        g.setPosition(pos++);
                    }
                }
            }

            // 触发UI更新
            for (ModelChangeListener listener : listeners) {
                listener.onQueueChanged();
            }
        }

        // 6. 检查是否有可用餐桌（人数变更可能触发新分配）
        checkAndAssignWaitingCustomers();
    }

    /**
     * 分界綫
     */

    public OperationResult<Boolean> tryAssignCustomerToTable(
            Connection conn,
            String tableId,
            int peopleCount,
            boolean isTwoSeat,
            boolean isFourSeat,
            boolean isMerge,
            boolean isShare,
            boolean isAddGuests,
            boolean isSixSeat,
            String secondTableId,
            boolean isFromQueue,
            int callNumber) {

        try {
            // ===== 1. 队列模式：直接分配到空桌（您的原始逻辑，完全保留）=====
            if (isFromQueue) {
                CustomerGroup group = customerGroupDAO.findByCallNumber(conn, callNumber);
                if (group == null || group.isAssigned()) {
                    return OperationResult.error(
                            "排队号 #" + callNumber + " 不存在或已入座"
                    );
                }
                peopleCount = group.getSize(); // 使用队列中真实人数
                return assignQueuedGroupToTable(conn, tableId, group, isTwoSeat, isFourSeat, isSixSeat, isShare, isMerge, secondTableId);
            }

            // ===== 2. 查询目标餐桌 =====
            Tables table = getTableById(tableId.trim());
            if (table == null) {
                return OperationResult.error("餐桌 #" + tableId + " 不存在");
            }

            // ===== 3. 共享餐桌场景（仅新顾客模式）=====
            if (isShare) {
                if (peopleCount <= 0) {
                    return OperationResult.error("共享餐桌需要指定新顾客组人数");
                }
                // ✅ 仅新顾客支持共享，直接调用（2参数版本）

                return handleShareTable(conn, table, peopleCount);
            }

            // ===== 4. 添加客人场景 =====
            if (isAddGuests) {
                return handleAddToExistingGroup(conn, table, peopleCount);
            }

            // ===== 5. 餐桌类型校验 =====
            if ((isTwoSeat && table.getPhysicalCapacity() != 2) ||
                    (isFourSeat && table.getPhysicalCapacity() != 4) ||
                    (isSixSeat && table.getPhysicalCapacity() != 6)) {
                return OperationResult.error(
                        "餐桌 #" + tableId + " 不是所选类型的餐桌（实际容量: " +
                                table.getPhysicalCapacity() + "）"
                );
            }

            // ===== 6. 6人桌规则 =====
            if (isSixSeat && peopleCount < 4) {
                return OperationResult.warning(
                        "3人或以下的客户不能使用6人桌，请选择2人桌或4人桌"
                );
            }

            // ===== 7. 合并桌子场景 =====
            if (isMerge) {
                if (secondTableId == null || secondTableId.trim().isEmpty()) {
                    return OperationResult.error("合并操作需要指定第二张餐桌编号");
                }
                Tables partnerTable = tablesDAO.findByDisplayId(conn, secondTableId.trim());
                if (partnerTable == null) {
                    return OperationResult.error("第二张餐桌 #" + secondTableId + " 不存在");
                }

                // ✅ 关键修复：实际调用 assignMergedTables 执行合并分配
                try {
                    boolean success = assignMergedTables(conn, table, partnerTable, peopleCount);
                    if (success) {
                        return OperationResult.success(true);
                    } else {
                        return OperationResult.error("合并餐桌分配失败");
                    }
                } catch (SQLException e) {
                    return OperationResult.error("合并餐桌时发生错误: " + e.getMessage());
                }
            }
            return OperationResult.success(true);

        } catch (SQLException e) {
            e.printStackTrace();
            return OperationResult.error("数据库操作失败");
        }
    }

    private OperationResult<Boolean> handleAddToExistingGroup(
            Connection conn, Tables table, int additionalPeople) throws SQLException {

        // ===== 场景1: 空桌 → 创建新顾客组 =====
        if (table.getStatus() == Tables.TableStatus.VACANT) {
            if (table.getPhysicalCapacity() == 6 && additionalPeople < 4) {
                return OperationResult.error("3人或以下的客户不能使用6人桌");
            }

            int callNumber = businessStatusDAO.getNextCallNumber(conn, LocalDate.now());
            CustomerGroup newGroup = new CustomerGroup(callNumber, additionalPeople);
            newGroup.setAssigned(true);
            newGroup.setStartTime(LocalDateTime.now());
            newGroup.setTableId(table.getTableId());

            //  保存到数据库（生成 group_id）
            customerGroupDAO.save(conn, newGroup);

            // 关键修复：检查 int 类型的 group_id（不能与 null 比较）
            if (newGroup.getGroup_id() <= 0) {
                return OperationResult.error("顾客组ID生成失败，请重试");
            }
            customerGroupMap.put(newGroup.getGroup_id(), newGroup);

            // 更新餐桌
            table.setCurrentGroupId(newGroup.getGroup_id());
            table.setStatus(Tables.TableStatus.OCCUPIED);
            table.setActualSeats(additionalPeople);
            table.setStartTime(LocalDateTime.now());

            tablesDAO.update(conn, table);
            businessStatusDAO.incrementNextCallNumber(conn, LocalDate.now());
            businessStatusDAO.incrementDailyTotalCustomers(conn, additionalPeople, LocalDate.now());
            return OperationResult.success(true);
        }

        // ===== 场景2: 已占桌 → 追加人数 =====
        if (table.getStatus() != Tables.TableStatus.OCCUPIED) {
            return OperationResult.error("餐桌 #" + table.getDisplayId() + " 状态异常");
        }

        Integer groupId = table.getCurrentGroupId();
        if (groupId == null || groupId <= 0) {
            return OperationResult.error("餐桌 #" + table.getDisplayId() + " 无有效顾客组ID");
        }

        //  从 customerGroupMap 获取（无需 findCustomerGroupById）
        CustomerGroup group = customerGroupMap.get(groupId);
        if (group == null) {
            // 内存中找不到 → 从数据库加载并同步到内存
            group = customerGroupDAO.findById(conn, groupId);
            if (group == null) {
                return OperationResult.error("顾客组数据异常（ID=" + groupId + " 不存在）");
            }
            customerGroupMap.put(groupId, group);
        }

        int currentSize = group.getSize();
        int remainingSeats = table.getPhysicalCapacity() - currentSize;

        if (additionalPeople > remainingSeats) {
            return OperationResult.error(
                    "追加人数 (" + additionalPeople + ") 超过剩余座位 (" + remainingSeats + ")"
            );
        }

        if (table.getPhysicalCapacity() == 6 && (currentSize + additionalPeople) < 4) {
            return OperationResult.error("3人或以下的客户不能使用6人桌");
        }

        // 更新内存
        group.setSize(currentSize + additionalPeople);
        table.setActualSeats(currentSize + additionalPeople);

        // 持久化
        customerGroupDAO.update(conn, group);
        tablesDAO.update(conn, table);

        // 合并桌处理
        if (table.getTableType() == Tables.TableType.MERGED && table.getMergedWith() != null) {
            Tables partner = tablesDAO.findByDisplayId(conn, table.getMergedWith());
            if (partner != null) {
                Tables master = table.getBaseId() <= partner.getBaseId() ? table : partner;
                master.setActualSeats(currentSize + additionalPeople);
                tablesDAO.update(conn, master);
            }
        }


        return OperationResult.success(true);
    }


    public void syncMemoryAfterAddGuests(String displayId) {
        Tables memoryTable = tableMap.get(displayId);
        if (memoryTable == null) return;

        try (Connection conn = ConnectionPool.getConnection()) {
            // 1. 从数据库重新加载餐桌
            Tables dbTable = tablesDAO.findByDisplayId(conn, displayId);
            if (dbTable == null) return;

            // 2. 更新餐桌基础属性（保持对象引用不变）
            memoryTable.setActualSeats(dbTable.getActualSeats());
            memoryTable.setStatus(dbTable.getStatus());
            memoryTable.setCurrentGroupId(dbTable.getCurrentGroupId()); // 仅设置ID
            memoryTable.setStartTime(dbTable.getStartTime());

            //  关键修复：同步设置 currentGroup 对象引用（解决警告的核心）
            Integer groupId = dbTable.getCurrentGroupId();
            if (groupId != null && groupId > 0) {
                // 优先从内存获取（避免重复查库）
                CustomerGroup group = customerGroupMap.get(groupId);

                // 内存中不存在 → 从数据库加载并同步到内存
                if (group == null) {
                    group = customerGroupDAO.findById(conn, groupId);
                    if (group != null) {
                        customerGroupMap.put(groupId, group); // 确保内存有此对象
                    }
                }

                // 关键：设置 currentGroup 引用（消除"有ID无对象"警告）
                memoryTable.setCurrentGroup(group);
            } else {
                // 清理无效引用
                memoryTable.setCurrentGroup(null);
            }

            // 3. 合并桌特殊处理（同步伙伴桌）
            if (dbTable.getTableType() == Tables.TableType.MERGED &&
                    dbTable.getMergedWith() != null) {
                Tables partner = tableMap.get(dbTable.getMergedWith());
                if (partner != null) {
                    Tables dbPartner = tablesDAO.findByDisplayId(conn, dbTable.getMergedWith());
                    if (dbPartner != null) {
                        partner.setActualSeats(dbPartner.getActualSeats());
                        partner.setStatus(dbPartner.getStatus());
                        partner.setCurrentGroupId(dbPartner.getCurrentGroupId());
                        partner.setStartTime(dbPartner.getStartTime());

                        //  同样需要设置伙伴桌的 currentGroup
                        Integer partnerGroupId = dbPartner.getCurrentGroupId();
                        if (partnerGroupId != null && partnerGroupId > 0) {
                            CustomerGroup partnerGroup = customerGroupMap.get(partnerGroupId);
                            if (partnerGroup == null) {
                                partnerGroup = customerGroupDAO.findById(conn, partnerGroupId);
                                if (partnerGroup != null) {
                                    customerGroupMap.put(partnerGroupId, partnerGroup);
                                }
                            }
                            partner.setCurrentGroup(partnerGroup);
                        } else {
                            partner.setCurrentGroup(null);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    /**
     * 将排队中的顾客组分配到指定餐桌（支持空桌分配/共享/合并三种场景）
     *
     * @param conn          事务连接
     * @param tableId       目标餐桌编号（主桌）
     * @param group         排队中的顾客组（已验证 isAssigned=false）
     * @param isTwoSeat     是否2人桌
     * @param isFourSeat    是否4人桌
     * @param isSixSeat     是否6人桌
     * @param isShare       是否共享餐桌
     * @param isMerge       【新增】是否合并餐桌
     * @param secondTableId 【新增】第二张餐桌编号（合并场景必需）
     * @return 操作结果
     * @throws SQLException
     */
    private OperationResult<Boolean> assignQueuedGroupToTable(
            Connection conn,
            String tableId,
            CustomerGroup group,
            boolean isTwoSeat,
            boolean isFourSeat,
            boolean isSixSeat,
            boolean isShare,
            boolean isMerge,          // ✅ 新增参数
            String secondTableId) throws SQLException { // ✅ 新增参数

        Tables table = getTableById(tableId.trim());
        if (table == null) {
            return OperationResult.error("餐桌 #" + tableId + " 不存在");
        }

        // ===== 1. 合并场景：队列顾客分配到两张合并餐桌 =====
        if (isMerge) {
            // 验证第二张餐桌编号
            if (secondTableId == null || secondTableId.trim().isEmpty()) {
                return OperationResult.error("合并操作需要指定第二张餐桌编号");
            }

            // 查询第二张餐桌
            Tables partnerTable = tablesDAO.findByDisplayId(conn, secondTableId.trim());
            if (partnerTable == null) {
                return OperationResult.error("第二张餐桌 #" + secondTableId + " 不存在");
            }

            // 委托专用方法处理合并分配（含完整验证+事务操作）
            return assignMergedTablesWithQueuedGroup(conn, table, partnerTable, group);
        }

        // ===== 2. 共享场景：队列顾客共享到已有顾客的餐桌 =====
        if (isShare) {
            // 验证餐桌状态（必须是 OCCUPIED）
            if (table.getStatus() != Tables.TableStatus.OCCUPIED) {
                return OperationResult.error("餐桌 #" + tableId + " 不是占用状态，无法共享");
            }

            // 验证餐桌类型（不能是合并桌或子桌）
            if (table.getTableType() == Tables.TableType.MERGED ||
                    table.getTableType() == Tables.TableType.SUBTABLE) {
                return OperationResult.error("该类型餐桌不能进行共享操作");
            }

            // 调用共享业务方法（传入队列中的顾客组）
            OperationResult<Boolean> result = handleShareTableWithQueuedGroup(conn, table, group);

            // 分配成功后从队列移除
            if (result.isSuccess()) {
                removeFromAllQueues(group);
                System.out.println("✅ 队列顾客 #" + group.getCallNumber() +
                        " 已从队列移除并完成共享");
            }
            return result;
        }

        // ===== 3. 普通分配场景：队列顾客分配到空桌 =====
        // 校验餐桌状态（必须是 VACANT）
        if (table.getStatus() != Tables.TableStatus.VACANT) {
            return OperationResult.error("餐桌 #" + tableId + " 不是空闲状态");
        }

        // 校验容量规则（6人桌限制）
        int groupSize = group.getSize();
        if (table.getPhysicalCapacity() == 6 && groupSize < 4) {
            return OperationResult.warning("3人或以下的客户不能使用6人桌，请选择2人桌或4人桌");
        }

        // 校验餐桌类型匹配
        if ((isTwoSeat && table.getPhysicalCapacity() != 2) ||
                (isFourSeat && table.getPhysicalCapacity() != 4) ||
                (isSixSeat && table.getPhysicalCapacity() != 6)) {
            return OperationResult.error("餐桌 #" + tableId + " 不是所选类型的餐桌（实际容量: " +
                    table.getPhysicalCapacity() + "）");
        }

        // 执行核心分配逻辑
        processTableAssignment(conn, group, table);

        // 从队列中移除（processTableAssignment 内部已调用 removeFromAllQueues）
        System.out.println("✅ 队列顾客 #" + group.getCallNumber() +
                " 已分配到餐桌 #" + tableId);

        return OperationResult.success(true);
    }

    private OperationResult<Boolean> handleShareTableWithQueuedGroup(
            Connection conn,
            Tables mainTable,
            CustomerGroup queuedGroup) throws SQLException {

        // ===== 1. 基础验证 =====
        if (mainTable == null) {
            return OperationResult.error("餐桌不存在");
        }

        if (mainTable.getStatus() != Tables.TableStatus.OCCUPIED) {
            return OperationResult.error("餐桌 #" + mainTable.getDisplayId() + " 不是占用状态，无法共享");
        }

        if (mainTable.getTableType() == Tables.TableType.MERGED ||
                mainTable.getTableType() == Tables.TableType.SUBTABLE) {
            return OperationResult.error("该类型餐桌不能进行共享操作");
        }

        CustomerGroup existingGroup = mainTable.getCurrentGroup();
        if (existingGroup == null) {
            return OperationResult.error("餐桌 #" + mainTable.getDisplayId() + " 无关联顾客组");
        }

        int existingSize = existingGroup.getSize();
        int newGroupSize = queuedGroup.getSize();
        int totalSize = existingSize + newGroupSize;
        int physicalCapacity = mainTable.getCapacity();

        // ===== 2. 容量与规则验证 =====
        if (totalSize > physicalCapacity) {
            return OperationResult.error(
                    String.format("餐桌容量不足！已有 %d 人，新增 %d 人，总计 %d 人，超过容量 %d 人",
                            existingSize, newGroupSize, totalSize, physicalCapacity));
        }

        if (physicalCapacity == 6 && totalSize < 4) {
            return OperationResult.warning("6人桌规则：总人数必须 ≥ 4人");
        }

        if (physicalCapacity == 4) {
            if (existingSize == 3) {
                return OperationResult.error("4人桌已有3位顾客，不能再共享");
            }
            if (existingSize == 1 && newGroupSize == 3) {
                return OperationResult.error("4人桌已有1位顾客，不能添加3人共享");
            }
        }

        // ===== 3. 获取主桌订单状态 =====
        Tables.OrderStatus mainOrderStatus = orderDAO.getLatestOrderStatus(conn, mainTable.getTableId());

        // ===== 4. 关键差异：直接使用队列中的顾客组（不创建新组）=====
        CustomerGroup newGroup = queuedGroup;
        newGroup.setAssigned(true);
        newGroup.setStartTime(LocalDateTime.now());
        businessStatusDAO.incrementDailyTotalCustomers(conn, newGroupSize, LocalDate.now());
        // ===== 5. 创建子桌 =====
        Tables subTableA = createSubTableA(mainTable, existingGroup, mainOrderStatus);
        Tables subTableB = createSubTableB(mainTable, newGroup);

        // ===== 6. 持久化子桌 =====
        subTableA = tablesDAO.saveSubTable(conn, subTableA);
        subTableB = tablesDAO.saveSubTable(conn, subTableB);

        // ===== 7. 关联顾客组与子桌 =====
        customerGroupDAO.updateTableId(conn, existingGroup.getGroup_id(), subTableA.getTableId());
        customerGroupDAO.updateTableId(conn, newGroup.getGroup_id(), subTableB.getTableId());

        // ===== 8. 迁移所有订单到子桌A =====
        if (orderDAO.hasAnyOrders(conn, mainTable.getTableId())) {
            orderDAO.migrateAllOrders(conn, mainTable.getTableId(), subTableA.getTableId());
        }

        // ===== 9. 更新主桌状态为 SPLITTING + 清空顾客组引用 =====
        tablesDAO.updateSplitStatus(conn, mainTable.getTableId(), true);

        // ===== 10. 同步内存 =====
        syncMemoryAfterShare(mainTable, subTableA, subTableB, newGroup, existingGroup);

        return OperationResult.success(true);
    }

    /**
     * 创建子桌A（继承主桌状态：开始时间 + 订单状态）
     * <p>
     * 严格匹配您的设计：
     * - 使用 getCapacity() 表示物理容量
     * - 使用 getId() 获取ID
     * - 不操作 tableMap（由 syncMemoryAfterShare 统一处理）
     */
    private Tables createSubTableA(Tables mainTable, CustomerGroup existingGroup, Tables.OrderStatus orderStatus) {
        int subTableCapacity = mainTable.getCapacity() / 2; // 严格使用 getCapacity()
        Tables subTableA = new Tables(
                mainTable.getBaseId(),
                subTableCapacity,
                mainTable.getDisplayId() + "a"
        );

        subTableA.setTableType(Tables.TableType.SUBTABLE);
        subTableA.setSubTableSuffix("a");
        subTableA.setMainTableId(mainTable.getTableId()); // 严格使用 getTableId()
        subTableA.setStatus(Tables.TableStatus.OCCUPIED);
        subTableA.assignCustomerGroup(existingGroup); // 使用 assignCustomerGroup 保证ID同步
        subTableA.setStartTime(mainTable.getStartTime()); // ✅ 继承开始时间
        subTableA.setOrderStatus(orderStatus);            // ✅ 继承订单状态（瞬态字段）
        subTableA.setCapacity(subTableCapacity);          // capacity = physical_capacity
        subTableA.setActualSeats(existingGroup.getSize());
        subTableA.setSplit(false);
        subTableA.setMergedWith(null);

        return subTableA;
    }

    /**
     * 创建子桌B（新顾客组状态）
     * <p>
     * 严格匹配您的设计：
     * - 新顾客组使用当前时间
     * - 订单状态 = NO_ORDER
     */
    private Tables createSubTableB(Tables mainTable, CustomerGroup newGroup) {
        int subTableCapacity = mainTable.getCapacity() / 2; // 严格使用 getCapacity()
        Tables subTableB = new Tables(
                mainTable.getBaseId(),
                subTableCapacity,
                mainTable.getDisplayId() + "b"
        );

        subTableB.setTableType(Tables.TableType.SUBTABLE);
        subTableB.setSubTableSuffix("b");
        subTableB.setMainTableId(mainTable.getTableId()); // 严格使用 getTableId()
        subTableB.setStatus(Tables.TableStatus.OCCUPIED);
        subTableB.assignCustomerGroup(newGroup); // 使用 assignCustomerGroup 保证ID同步
        subTableB.setStartTime(LocalDateTime.now());               // ✅ 当前时间
        subTableB.setOrderStatus(Tables.OrderStatus.NO_ORDER);     // ✅ 无订单
        subTableB.setCapacity(subTableCapacity);                   // capacity = physical_capacity
        subTableB.setActualSeats(newGroup.getSize());
        subTableB.setSplit(false);
        subTableB.setMergedWith(null);

        return subTableB;
    }


    /**
     * 处理共享餐桌业务（仅新顾客入座模式）
     */
    private OperationResult<Boolean> handleShareTable(
            Connection conn,
            Tables mainTable,
            int newGroupSize) throws SQLException {

        // ===== 1. 基础验证 =====
        if (mainTable == null) {
            return OperationResult.error("餐桌不存在");
        }

        if (mainTable.getStatus() != Tables.TableStatus.OCCUPIED) {
            return OperationResult.error("餐桌 #" + mainTable.getDisplayId() + " 不是占用状态，无法共享");
        }

        if (mainTable.getTableType() == Tables.TableType.MERGED ||
                mainTable.getTableType() == Tables.TableType.SUBTABLE) {
            return OperationResult.error("该类型餐桌不能进行共享操作");
        }

        CustomerGroup existingGroup = mainTable.getCurrentGroup();
        if (existingGroup == null) {
            return OperationResult.error("餐桌 #" + mainTable.getDisplayId() + " 无关联顾客组");
        }

        int existingSize = existingGroup.getSize();
        int totalSize = existingSize + newGroupSize;
        int physicalCapacity = mainTable.getCapacity();

        // ===== 2. 容量与规则验证 =====
        if (totalSize > physicalCapacity) {
            return OperationResult.error(
                    String.format("餐桌容量不足！已有 %d 人，新增 %d 人，总计 %d 人，超过容量 %d 人",
                            existingSize, newGroupSize, totalSize, physicalCapacity));
        }

        if (physicalCapacity == 6 && totalSize < 4) {
            return OperationResult.warning("6人桌规则：总人数必须 ≥ 4人");
        }

        if (physicalCapacity == 4) {
            if (existingSize == 3) {
                return OperationResult.error("4人桌已有3位顾客，不能再共享");
            }
            if (existingSize == 1 && newGroupSize == 3) {
                return OperationResult.error("4人桌已有1位顾客，不能添加3人共享");
            }
        }

        // ===== 3. 获取主桌订单状态 =====
        Tables.OrderStatus mainOrderStatus = orderDAO.getLatestOrderStatus(conn, mainTable.getTableId());

        // ===== 4. 创建新顾客组（仅新顾客）=====
        int callNumber = businessStatusDAO.getNextCallNumber(conn, LocalDate.now());

        CustomerGroup newGroup = new CustomerGroup(callNumber, newGroupSize);
        newGroup.setAssigned(true);
        newGroup.setStartTime(LocalDateTime.now());

        customerGroupDAO.saveWithoutTableRef(conn, newGroup);
        if (newGroup.getGroup_id() <= 0) { // ✅ 严格使用 getGroup_id()
            return OperationResult.error("新顾客组ID生成失败");
        }
        businessStatusDAO.incrementNextCallNumber(conn, LocalDate.now());
        businessStatusDAO.incrementDailyTotalCustomers(conn, newGroupSize, LocalDate.now());
        // ===== 5. 创建子桌 =====
        Tables subTableA = createSubTableA(mainTable, existingGroup, mainOrderStatus);
        Tables subTableB = createSubTableB(mainTable, newGroup);

        // ===== 6. 持久化子桌 =====
        subTableA = tablesDAO.saveSubTable(conn, subTableA);
        subTableB = tablesDAO.saveSubTable(conn, subTableB);

        // ===== 7. 关联顾客组与子桌 =====
        customerGroupDAO.updateTableId(conn, existingGroup.getGroup_id(), subTableA.getTableId()); // ✅ getGroup_id()
        customerGroupDAO.updateTableId(conn, newGroup.getGroup_id(), subTableB.getTableId());       // ✅ getGroup_id()

        // ===== 8. 迁移所有订单到子桌A =====
        if (orderDAO.hasAnyOrders(conn, mainTable.getTableId())) {
            orderDAO.migrateAllOrders(conn, mainTable.getTableId(), subTableA.getTableId());
        }

        // ===== 9. 更新主桌状态为 SPLITTING =====
        tablesDAO.updateSplitStatus(conn, mainTable.getTableId(), true);

        // ===== 10. 同步内存 =====
        syncMemoryAfterShare(mainTable, subTableA, subTableB, newGroup, existingGroup);

        return OperationResult.success(true);
    }


    /**
     * 共享后同步内存状态
     * <p>
     * 严格匹配您的内存模型：
     * - 主桌: OCCUPIED → SPLITTING, currentGroup=null
     * - 子桌A/B: 添加到 tables 和 tableMap
     * - 顾客组: 更新 table_id 引用
     */
    private void syncMemoryAfterShare(
            Tables mainTable,
            Tables subTableA,
            Tables subTableB,
            CustomerGroup newGroup,
            CustomerGroup existingGroup) {

        // 1. 更新主桌内存状态 → SPLITTING
        Tables memoryMain = tableMap.get(mainTable.getDisplayId());
        if (memoryMain != null) {
            memoryMain.setSplit(true);
            memoryMain.setStatus(Tables.TableStatus.SPLITTING);
            memoryMain.setCurrentGroup(null);
            memoryMain.setCurrentGroupId(null);
            // 注意：主桌的瞬态 orderStatus 不清空（供历史查询）
        }

        // 2. 添加子桌到全局映射（严格按您风格）
        tables.add(subTableA);
        tables.add(subTableB);
        tableMap.put(subTableA.getDisplayId(), subTableA);
        tableMap.put(subTableB.getDisplayId(), subTableB);

        // 3. 注册新顾客组（ 修正：使用 getGroup_id()）
        customerGroupMap.put(newGroup.getGroup_id(), newGroup);

        // 4. 更新原顾客组餐桌引用（正确：setTableId 接收 table_id）
        existingGroup.setTableId(subTableA.getTableId());

        System.out.println("共享餐桌成功: #" + mainTable.getDisplayId() +
                " → " + subTableA.getDisplayId() + "(原顾客) + " +
                subTableB.getDisplayId() + "(新顾客)");
    }

    private boolean assignMergedTables(Connection conn,
                                       Tables table1,
                                       Tables table2,
                                       int peopleCount) throws SQLException {
        // 1. 確定主桌
        Tables mainTable = table1.getBaseId() <= table2.getBaseId() ? table1 : table2;
        Tables partnerTable = (mainTable == table1) ? table2 : table1;

        // 2. 創建顧客組（✅ 獲取叫號 + ✅ 立即遞增）
        int callNumber = businessStatusDAO.getNextCallNumber(conn, LocalDate.now());
        CustomerGroup group = new CustomerGroup(callNumber, peopleCount);
        group = customerGroupDAO.save(conn, group);

        // ✅ 關鍵修復：保存成功後立即遞增叫號
        businessStatusDAO.incrementNextCallNumber(conn, LocalDate.now());

        // 3. 分配座位
        int seatsMain = Math.min(peopleCount, mainTable.getPhysicalCapacity());
        int seatsPartner = peopleCount - seatsMain;

        // 4. 更新餐桌狀態（委託 TablesDAO）
        boolean updated = tablesDAO.updateMergeStatus(
                conn,
                mainTable.getTableId(),
                partnerTable.getTableId(),
                partnerTable.getDisplayId(),
                mainTable.getDisplayId(),
                group.getGroup_id(),
                seatsMain,
                seatsPartner
        );
        if (!updated) {
            throw new SQLException("合并餐桌状态更新失败");
        }

        // 5. 更新顧客組分配狀態（委託 CustomerGroupDAO）
        customerGroupDAO.updateAssignmentStatus(
                conn,
                group.getGroup_id(),
                mainTable.getTableId(),
                true,
                false
        );

        // 6. 同步內存狀態（Model 層職責）
        updateMemoryForMergedTables(mainTable, partnerTable, group, seatsMain, seatsPartner);

        // 7. 增加當日顧客數
        businessStatusDAO.incrementDailyTotalCustomers(conn, peopleCount, LocalDate.now());

        return true;
    }


    /**
     * 将排队中的顾客组分配到指定的两张合并餐桌（事务内完整逻辑）
     *
     * @param conn         事务连接（由 tryAssignCustomerToTable 传入）
     * @param mainTable    第一张餐桌（将自动判定为主桌）
     * @param partnerTable 第二张餐桌（伙伴桌）
     * @param group        排队中的顾客组（已验证 isAssigned=false）
     * @return 操作结果
     * @throws SQLException 数据库操作异常
     */
    private OperationResult<Boolean> assignMergedTablesWithQueuedGroup(
            Connection conn,
            Tables mainTable,
            Tables partnerTable,
            CustomerGroup group) throws SQLException {

        // ===== 1. 验证餐桌状态 =====
        if (mainTable.getStatus() != Tables.TableStatus.VACANT ||
                partnerTable.getStatus() != Tables.TableStatus.VACANT) {
            return OperationResult.error(
                    "合并的两张餐桌必须都处于空闲状态（当前: " +
                            mainTable.getStatus() + " / " + partnerTable.getStatus() + ")"
            );
        }
        if (mainTable.getTableType() != Tables.TableType.MAIN ||
                partnerTable.getTableType() != Tables.TableType.MAIN) {
            return OperationResult.error("只能合并主桌，不能合并子桌或已合并的餐桌");
        }
        if (mainTable.isSplit() || partnerTable.isSplit()) {
            return OperationResult.error("餐桌处于拆分状态，无法合并");
        }

        // ===== 2. 容量与业务规则验证 =====
        int groupSize = group.getSize();
        int totalCapacity = mainTable.getPhysicalCapacity() + partnerTable.getPhysicalCapacity();
        if (groupSize > totalCapacity) {
            return OperationResult.error(
                    String.format("顾客组人数(%d) 超过合并后总容量(%d)", groupSize, totalCapacity)
            );
        }
        // 6人桌规则：含6人桌且总人数<4
        if ((mainTable.getPhysicalCapacity() == 6 || partnerTable.getPhysicalCapacity() == 6) && groupSize < 4) {
            return OperationResult.warning("3人或以下的客户不能使用6人桌（即使合并）");
        }

        // ===== 3. 确定主桌（baseId较小的）=====
        Tables actualMainTable = mainTable.getBaseId() <= partnerTable.getBaseId() ? mainTable : partnerTable;
        Tables actualPartnerTable = (actualMainTable == mainTable) ? partnerTable : mainTable;

        // ===== 4. 计算每张餐桌实际座位数 =====
        int seatsMain = Math.min(groupSize, actualMainTable.getPhysicalCapacity());
        int seatsPartner = groupSize - seatsMain;

        // ===== 5. 执行数据库更新（核心事务操作）=====
        boolean tablesUpdated = tablesDAO.updateMergeStatus(
                actualMainTable.getTableId(),
                actualPartnerTable.getTableId(),
                actualPartnerTable.getDisplayId(),
                actualMainTable.getDisplayId(),
                group.getGroup_id(), // ← 使用已存在的 group_id
                seatsMain,
                seatsPartner
        );
        if (!tablesUpdated) {
            throw new SQLException("合并餐桌状态更新失败");
        }

        // 更新顾客组分配状态（仅关联主餐桌ID）
        boolean groupUpdated = customerGroupDAO.updateAssignmentStatus(
                group.getGroup_id(),
                actualMainTable.getTableId(), // 仅主桌ID
                true,  // isAssigned
                false  // shownWaitMessage
        );
        if (!groupUpdated) {
            throw new SQLException("更新顾客组分配状态失败");
        }

        // 增加当日顾客数
        businessStatusDAO.incrementDailyTotalCustomers(conn, groupSize,LocalDate.now());

        // ===== 6. 更新内存状态（事务提交前）=====
        updateMemoryForMergedTables(
                actualMainTable,
                actualPartnerTable,
                group,
                seatsMain,
                seatsPartner
        );

        // ===== 7. 从队列移除（事务内操作）=====
        removeFromAllQueues(group); // 内部含数据库队列移除 + 内存队列清理

        System.out.println("✅ 队列顾客组 #" + group.getCallNumber() +
                " (" + groupSize + "人) 已分配到合并餐桌 #" +
                actualMainTable.getDisplayId() + " + #" + actualPartnerTable.getDisplayId());

        return OperationResult.success(true);
    }

    /**
     * 【纯内存】获取所有有未结账订单的餐桌 display_id 列表
     * 判断标准：orderStatus 为 ORDERED_UNFINISHED 或 ORDERED_FINISHED
     */
    public List<String> getTablesWithUnpaidOrdersInMemory() {
        List<String> unpaidTables = new ArrayList<>();

        for (Tables table : tableMap.values()) {
            // 只检查占用中的餐桌
            if (table.getStatus() != Tables.TableStatus.OCCUPIED) {
                continue;
            }

            Tables.OrderStatus orderStatus = table.getOrderStatus();
            // 未结账 = 有订单且状态不是 CHECKED_OUT
            if (orderStatus == Tables.OrderStatus.ORDERED_UNFINISHED ||
                    orderStatus == Tables.OrderStatus.ORDERED_FINISHED) {
                unpaidTables.add(table.getDisplayId());
            }
        }

        // 按 display_id 排序（便于阅读）
        unpaidTables.sort(Comparator.naturalOrder());
        return unpaidTables;
    }




    /**
     * 结束营业并持久化（事务封装在 Model 内部）
     */
    public void closeForDayWithPersistence() {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            // 1. 业务逻辑：修改内存状态
            this.isOpenForBusiness = false;

            // 2. 持久化：委托内部方法
            businessStatusDAO.updateBusinessStatus(conn, LocalDate.now(), isOpenForBusiness, nextCallNumber);

            // 3. 提交事务
            conn.commit();

        } catch (SQLException e) {
            // 事务回滚
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignore) {
                }
            }
            // 记录日志（不抛异常，避免中断 UI 流程）
            System.err.println("保存营业状态失败: " + e.getMessage());
            throw new RuntimeException("打烊持久化失败", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }


    /**
     * 开始营业并持久化（对称设计）
     */
    public void openForBusinessWithPersistence() {
        Connection conn = null;
        try {
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            this.isOpenForBusiness = true;
            businessStatusDAO.updateBusinessStatus(conn, LocalDate.now(), isOpenForBusiness, nextCallNumber);

            conn.commit();

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ignore) {
                }
            }
            System.err.println("保存营业状态失败: " + e.getMessage());
            throw new RuntimeException("开始营业持久化失败", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    public List<Map<String, Object>> getDailyBusinessReport(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("无效的日期格式，应为 yyyy-MM-dd");
        }
        try (Connection conn = ConnectionPool.getConnection()) {
            return ((BusinessStatusDAOImpl) businessStatusDAO).getDailyReport(conn, date);
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getDateRangeBusinessReport(String startDate, String endDate) {
        if (startDate == null || endDate == null ||
                !startDate.matches("\\d{4}-\\d{2}-\\d{2}") ||
                !endDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("日期格式错误");
        }
        try (Connection conn = ConnectionPool.getConnection()) {
            return ((BusinessStatusDAOImpl) businessStatusDAO).getDateRangeReport(conn, startDate, endDate);
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getQuarterlyDishSalesReport(int year, String quarter, String category) {
        try (Connection conn = ConnectionPool.getConnection()) {
            return ((OrderDAOImpl) orderDAO).getQuarterlyDishSalesReport(conn, year, quarter, category);
        } catch (SQLException e) {
            throw new RuntimeException("查询失败: " + e.getMessage(), e);
        }
    }

    public List<String> getAvailableYearsForDishSales() {
        try (Connection conn = ConnectionPool.getConnection()) {
            return ((OrderDAOImpl) orderDAO).getAvailableYearsForDishSales(conn);
        } catch (SQLException e) {
            return Collections.singletonList(String.valueOf(java.time.LocalDate.now().getYear()));
        }
    }
    /***
     *分界綫
     */

    public boolean isValidTableNumberFormat(String tableNumber) {
        return tableNumber != null && tableNumber.matches("^\\d+[a-b]?$");
    }

    public boolean isMainOrderTable(String tableNumber) {
        Tables table = getTableById(tableNumber);
        if (table == null || table.getMergedWith() == null) return true;
        // 主桌逻辑：编号较小的桌为主桌
        return tableNumber.compareTo(table.getMergedWith()) < 0;
    }

    public Map<String, Object> processCheckout(String tableNumber, double paymentAmount) {
        Connection conn = null;
        Map<String, Object> result = new HashMap<>();

        try {
            //  使用连接池（非硬编码 URL）
            conn = ConnectionPool.getConnection();
            conn.setAutoCommit(false);

            Tables table = getTableById(tableNumber);
            //  修复：使用 getTableId() 替代不存在的 getId()
            if (table == null || table.getTableId() <= 0) {
                result.put("success", false);
                result.put("message", "餐桌不存在");
                return result;
            }

            // 1. 获取活跃订单ID（通过 DAO）
            Integer orderId = orderDAO.findActiveOrderIdByTableId(conn, table.getTableId());
            if (orderId == null) {
                result.put("success", false);
                result.put("message", "未找到活跃订单");
                return result;
            }

            // 2. 获取订单数据（通过 DAO）
            double totalAmount = orderDAO.getOrderTotalAmount(conn, orderId);
            Timestamp orderTime = orderDAO.getOrderCreateTime(conn, orderId);

            if (paymentAmount < totalAmount) {
                result.put("success", false);
                result.put("message", "支付金额不足");
                return result;
            }

            // 3. 业务操作（全部委托 DAO）
            orderDAO.recordQuarterlySales(conn, orderId); // 季度销售

            java.sql.Date revenueDate = new java.sql.Date(orderTime.getTime());
            orderDAO.updateDailyRevenue(conn, totalAmount, revenueDate); // 营收

            orderDAO.checkoutOrder(conn, orderId); // 标记结账

            orderItemDAO.deleteOrderItemsByOrderId(conn, orderId); //

            // 4. 构建结果
            result.put("success", true);
            result.put("message", "结账成功");
            result.put("totalAmount", totalAmount);
            result.put("changeAmount", paymentAmount - totalAmount);
            result.put("revenueDate", revenueDate);

            conn.commit();
            return result;

        } catch (SQLException e) {
            //  内联回滚逻辑（移除不存在的 rollbackSafely）
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // 忽略回滚异常
                }
            }
            result.put("success", false);
            result.put("message", "结账失败: " + e.getMessage());
            return result;
        } finally {
            //  内联关闭逻辑（移除不存在的 closeConnectionSafely）
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // 忽略关闭异常
                }
            }
        }
    }

    /**
     * 获取订单详细信息（符合 MVC+DAO：Model 层无 SQL）
     */
    public Map<String, Object> getOrderDetails(String tableNumber) {
        Connection conn = null;
        Map<String, Object> result = new HashMap<>();

        try {
            conn = ConnectionPool.getConnection(); //  使用连接池

            Tables table = getTableById(tableNumber);
            //  修复：使用 getTableId() 替代不存在的 getId()
            if (table == null || table.getTableId() <= 0) {
                result.put("error", "餐桌不存在");
                return result;
            }

            // 1. 通过 DAO 获取订单头（无 SQL）
            Map<String, Object> header = orderDAO.getActiveOrderHeaderByTableId(conn, table.getTableId());
            if (header == null) {
                result.put("error", "未找到活跃订单");
                return result;
            }

            // 2. 通过 DAO 获取订单明细（无 SQL）
            int orderId = (Integer) header.get("orderId");
            List<Map<String, Object>> items = orderDAO.getOrderItemsByOrderId(conn, orderId);

            // 3. 组装结果（与原有结构完全一致）
            result.put("orderTime", header.get("orderTime"));
            result.put("totalAmount", header.get("totalAmount"));
            result.put("orderId", orderId);
            result.put("items", items);

        } catch (SQLException e) {
            result.put("error", "加载订单数据失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) { /* ignore */ }
            }
        }

        return result;
    }


    public String getOrderStatusDisplay(String tableNumber) {
        Tables table = getTableById(tableNumber);
        if (table == null) return "餐桌不存在";
        return "订单情况：" + table.getOrderStatus().getDisplayName();
    }

    /**
     * 检查订单是否已结账（纯内存查询，零数据库操作）
     *
     * @param tableNumber 餐桌编号
     * @return true 仅当状态为 CHECKED_OUT 时返回
     */
    public boolean isOrderCheckedOut(String tableNumber) {
        Tables table = getTableById(tableNumber);
        return table != null &&
                table.getOrderStatus() == Tables.OrderStatus.CHECKED_OUT;
    }

    /**
     * 检查餐桌是否有活跃订单（未结账的订单，纯内存查询）
     *
     * @param tableNumber 餐桌编号
     * @return true=有活跃订单（ORDERED_UNFINISHED/ORDERED_FINISHED），false=无订单或已结账
     */
    public boolean hasOrder(String tableNumber) {
        Tables table = getTableById(tableNumber);
        if (table == null) return false;

        Tables.OrderStatus status = table.getOrderStatus();
        //  仅当状态为"制作中"或"已完成"时返回 true（排除 NO_ORDER 和 CHECKED_OUT）
        return status == Tables.OrderStatus.ORDERED_UNFINISHED ||
                status == Tables.OrderStatus.ORDERED_FINISHED;
    }

}
