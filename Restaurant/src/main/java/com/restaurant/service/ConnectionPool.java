package com.restaurant.service;

import com.restaurant.util.ConfigLoader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.*;

public class ConnectionPool {
    private static HikariDataSource dataSource;
    private static volatile boolean schemaInitialized = false;

    // 私有构造函数，防止实例化
    private ConnectionPool() {}

    /**
     * 从配置加载器获取数据库连接参数并初始化连接池
     */
    public static synchronized void initializePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            return; // 已经初始化
        }

        System.out.println("🔧 正在初始化数据库连接池...");

        try {
            // 确保数据库存在
            ensureDatabaseExists();

            // 创建Hikari配置
            HikariConfig config = new HikariConfig();

            // 从配置加载器获取参数
            config.setJdbcUrl(ConfigLoader.getProperty("database.url") +
                    ConfigLoader.getProperty("database.name"));
            config.setUsername(ConfigLoader.getProperty("database.username"));
            config.setPassword(ConfigLoader.getProperty("database.password", ""));
            config.setDriverClassName(ConfigLoader.getProperty("database.driver"));

            // 连接池配置
            config.setMaximumPoolSize(ConfigLoader.getIntProperty("pool.maximumPoolSize", 10));
            config.setMinimumIdle(ConfigLoader.getIntProperty("pool.minimumIdle", 2));
            config.setConnectionTimeout(ConfigLoader.getLongProperty("pool.connectionTimeout", 20000));
            config.setIdleTimeout(ConfigLoader.getLongProperty("pool.idleTimeout", 30000));
            config.setMaxLifetime(ConfigLoader.getLongProperty("pool.maxLifetime", 1800000));

            // 可选的高级配置
            String poolName = ConfigLoader.getProperty("pool.poolName", "RestaurantPool");
            config.setPoolName(poolName);

            long leakThreshold = ConfigLoader.getLongProperty("pool.leakDetectionThreshold", 0);
            if (leakThreshold > 0) {
                config.setLeakDetectionThreshold(leakThreshold);
                System.out.println("启用连接泄漏检测，阈值: " + leakThreshold + "ms");
            }

            // 设置连接验证查询（如果配置了）
            String testQuery = ConfigLoader.getProperty("connection.testQuery");
            if (testQuery != null && !testQuery.isEmpty()) {
                config.setConnectionTestQuery(testQuery);
                System.out.println("设置连接验证查询: " + testQuery);
            }

            // 初始化数据源
            dataSource = new HikariDataSource(config);

            System.out.println("数据库连接池初始化成功: " + poolName);
            System.out.println("最大连接数: " + config.getMaximumPoolSize());
            System.out.println("数据库URL: " + maskUrl(config.getJdbcUrl()));

        } catch (Exception e) {
            System.err.println(" 连接池初始化失败: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("无法初始化数据库连接池", e);
        }
    }

    /**
     * 隐藏URL中的敏感信息
     */
    private static String maskUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        return url.replaceAll("(?<=[/:])[^/:@]+(?=@)", "****");
    }

    /**
     * 确保数据库存在，不存在则创建
     */
    private static void ensureDatabaseExists() {
        String dbUrl = ConfigLoader.getProperty("database.url");
        String dbName = ConfigLoader.getProperty("database.name");
        String username = ConfigLoader.getProperty("database.username");
        String password = ConfigLoader.getProperty("database.password", "");

        try (Connection conn = DriverManager.getConnection(dbUrl, username, password);
             Statement stmt = conn.createStatement()) {

            String createDbSql = "CREATE DATABASE IF NOT EXISTS " + dbName +
                    " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
            stmt.execute(createDbSql);
            System.out.println("确保数据库存在: " + dbName);

        } catch (SQLException e) {
            System.err.println(" 创建数据库失败: " + e.getMessage());
            throw new RuntimeException("无法创建或访问数据库", e);
        }
    }

    /**
     * 初始化数据库结构 - 仅创建数据库和表结构，不插入业务数据
     */
    /**
     * 初始化数据库结构 - 仅创建数据库和表结构，不插入业务数据
     * 修正：添加一次性初始化标志，避免重复调用
     */
    public static void initializeDatabaseSchema() {
        // 关键修正：检查是否已初始化
        if (schemaInitialized) {
            System.out.println("数据库结构已初始化，跳过重复操作");
            return;
        }

        Connection conn = null;
        Statement stmt = null;

        try {
            // 1. 连接到MySQL服务器（不指定数据库）
            String dbUrl = ConfigLoader.getProperty("database.url");
            String username = ConfigLoader.getProperty("database.username");
            String password = ConfigLoader.getProperty("database.password", "");

            conn = DriverManager.getConnection(dbUrl, username, password);
            stmt = conn.createStatement();

            // 2. 创建数据库
            String dbName = ConfigLoader.getProperty("database.name");
            stmt.execute("CREATE DATABASE IF NOT EXISTS " + dbName +
                    " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

            // 3. 使用数据库
            stmt.execute("USE " + dbName);

            // 4. 创建所有表结构
            createTables(conn);

            // 标记为已初始化
            schemaInitialized = true;

            System.out.println("数据库结构初始化成功: " + dbName);

        } catch (SQLException e) {
            System.err.println("数据库结构初始化失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    /**
     * 安全创建索引 - 先检查索引是否存在，不存在才创建
     */
    private static void createIndexSafely(Connection conn, String tableName, String indexName, String columns) throws SQLException {
        // 1. 检查索引是否已经存在
        boolean indexExists = false;
        String checkSql = "SELECT COUNT(1) AS cnt FROM INFORMATION_SCHEMA.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?";

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, tableName);
            checkStmt.setString(2, indexName);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    indexExists = rs.getInt("cnt") > 0;
                }
            }
        }

        // 2. 如果索引不存在，则创建它
        if (!indexExists) {
            String createSql = "CREATE INDEX " + indexName + " ON " + tableName + "(" + columns + ")";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createSql);
                System.out.println(" 索引 " + indexName + " 已成功创建在表 " + tableName);
            }
        }
    }

    /**
     * 创建数据库表结构
     */
    private static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // ============== 无依赖的表 ==============
            // 1. 业务状态表（无依赖）
            stmt.execute("CREATE TABLE IF NOT EXISTS business_status ("
                    + "status_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '业务状态ID',"
                    + "business_date DATE NOT NULL UNIQUE COMMENT '营业日期',"
                    + "is_open BOOLEAN DEFAULT true COMMENT '是否营业',"
                    + "next_call_number INT DEFAULT 1 COMMENT '下一个叫号',"
                    + "daily_total_customers INT DEFAULT 0 COMMENT '当日总顾客数',"
                    + "daily_revenue DECIMAL(10, 2) DEFAULT 0.00 COMMENT '当日总收入',"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // 2. 顾客组表（无外键依赖）
            stmt.execute("CREATE TABLE IF NOT EXISTS customer_groups ("
                    + "group_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '顾客组ID',"
                    + "call_number INT NOT NULL UNIQUE COMMENT '排队号码',"
                    + "group_size INT NOT NULL COMMENT '顾客组人数',"
                    + "start_time DATETIME NOT NULL COMMENT '入队/入座时间',"
                    + "is_assigned BOOLEAN DEFAULT FALSE COMMENT '是否已分配餐桌',"
                    + "shown_wait_message BOOLEAN DEFAULT FALSE COMMENT '是否已显示等待提示',"
                    + "table_id INT NULL COMMENT '分配的餐桌ID'"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ============== 菜单相关表 ==============
            // 3. 菜单品类表 (基础数据)
            stmt.execute("CREATE TABLE IF NOT EXISTS menu_categories ("
                    + "category_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',"
                    + "name VARCHAR(20) NOT NULL UNIQUE COMMENT '分类名称：食物/饮料/小炒/套餐',"
                    + "prefix CHAR(1) NOT NULL UNIQUE COMMENT '菜品前缀：A/B/C/D')"
                    + " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // 4. 菜品表 (核心菜单数据)
            stmt.execute("CREATE TABLE IF NOT EXISTS menu_items ("
                    + "item_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '菜品ID',"
                    + "item_code VARCHAR(10) NOT NULL UNIQUE COMMENT '菜品编号：A1, B2等',"
                    + "name VARCHAR(50) NOT NULL COMMENT '菜品名称',"
                    + "price DECIMAL(8,2) NOT NULL COMMENT '价格',"
                    + "category_id INT NOT NULL COMMENT '所属分类ID',"
                    + "is_active BOOLEAN DEFAULT true COMMENT '是否可用',"
                    + "FOREIGN KEY (category_id) REFERENCES menu_categories(category_id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ============== 菜品季度销售统计表 ==============
            stmt.execute("CREATE TABLE IF NOT EXISTS item_quarterly_sales ("
                    + "sales_id INT AUTO_INCREMENT PRIMARY KEY COMMENT '销售记录ID',"
                    + "item_code VARCHAR(10) NOT NULL COMMENT '销售时的菜品代码快照',"
                    + "item_name VARCHAR(50) NOT NULL COMMENT '销售时的菜品名称快照',"
                    + "sale_price DECIMAL(8,2) NOT NULL COMMENT '销售时的实际单价',"
                    + "quantity_sold INT NOT NULL DEFAULT 1 COMMENT '本次销售数量',"
                    + "sale_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '销售时间戳',"
                    + "year INT NOT NULL COMMENT '年份，如2023',"
                    + "quarter ENUM('Q1', 'Q2', 'Q3', 'Q4') NOT NULL COMMENT '季度（Q1:1-3月, Q2:4-6月, Q3:7-9月, Q4:10-12月）',"
                    + "INDEX idx_item_quarter (item_code, year, quarter),"
                    + "INDEX idx_sale_timestamp (sale_timestamp),"
                    + "INDEX idx_year_quarter (year, quarter)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品季度销售明细表（独立于菜单价格变化）'");

            try {
                stmt.execute("CREATE OR REPLACE VIEW quarterly_item_summary AS "
                        + "SELECT "
                        + "item_code, item_name, year, quarter, "
                        + "SUM(quantity_sold) as total_quantity, "
                        + "COUNT(DISTINCT DATE(sale_timestamp)) as active_days, "
                        + "SUM(quantity_sold * sale_price) as total_revenue, "
                        + "AVG(sale_price) as avg_price, "
                        + "MIN(sale_price) as min_price, "
                        + "MAX(sale_price) as max_price "
                        + "FROM item_quarterly_sales "
                        + "GROUP BY item_code, item_name, year, quarter");
            } catch (SQLException e) {
                System.err.println("⚠️ 创建季度汇总视图失败: " + e.getMessage());
            }

            // ============== 餐桌核心表 ==============
            stmt.execute("CREATE TABLE IF NOT EXISTS restaurant_tables ("
                    + "table_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '餐桌ID',"
                    + "display_id VARCHAR(10) NOT NULL UNIQUE COMMENT '显示ID，如7或7a',"
                    + "base_id INT NOT NULL COMMENT '基础桌号',"
                    + "capacity INT NOT NULL COMMENT '当前可用容量',"
                    + "physical_capacity INT NOT NULL COMMENT '物理容量（桌子最大容纳人数）',"
                    + "status ENUM('VACANT', 'OCCUPIED', 'SETTING_UP', 'SPLITTING') DEFAULT 'VACANT' COMMENT '餐桌状态',"
                    + "table_type ENUM('MAIN', 'MERGED', 'SUBTABLE') DEFAULT 'MAIN' COMMENT '餐桌类型',"
                    + "start_time DATETIME COMMENT '用餐开始时间',"
                    + "end_time DATETIME COMMENT '用餐结束时间',"
                    + "is_split BOOLEAN DEFAULT FALSE COMMENT '是否处于拆分状态',"
                    + "sub_table_suffix VARCHAR(2) COMMENT '子桌后缀（如a/b）',"
                    + "main_table_id INT COMMENT '主桌ID（子桌时使用）',"
                    + "actual_seats INT DEFAULT 0 COMMENT '实际入座人数',"
                    + "current_group_id INT NULL COMMENT '当前占用该餐桌的顾客组ID',"
                    + "merged_with VARCHAR(10) COMMENT '合并伙伴的显示ID（仅支持两桌合并）',"
                    + "FOREIGN KEY (main_table_id) REFERENCES restaurant_tables(table_id) ON DELETE SET NULL"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // 7. 桌台订单主表
            stmt.execute("CREATE TABLE IF NOT EXISTS table_orders ("
                    + "order_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',"
                    + "table_id INT NOT NULL COMMENT '餐桌ID',"
                    + "order_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',"
                    + "status ENUM('ORDERED', 'CHECKED_OUT') DEFAULT 'ORDERED' COMMENT '订单状态',"
                    + "total_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '总金额',"
                    + "is_checked_out BOOLEAN DEFAULT FALSE COMMENT '是否已结账（防止误操作回退状态）',"
                    + "FOREIGN KEY (table_id) REFERENCES restaurant_tables(table_id) ON DELETE CASCADE,"
                    + "UNIQUE KEY unique_table_active (table_id, status)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // 8. 订单明细表
            stmt.execute("CREATE TABLE IF NOT EXISTS order_items ("
                    + "order_item_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '订单明细ID',"
                    + "order_id INT NOT NULL COMMENT '订单ID',"
                    + "item_id INT NOT NULL COMMENT '菜品ID',"
                    + "quantity INT NOT NULL DEFAULT 1 COMMENT '总数量',"
                    + "served_quantity INT NOT NULL DEFAULT 0 COMMENT '已上菜数量',"
                    + "status ENUM('UNSERVED', 'PARTIALLY_SERVED', 'SERVED') DEFAULT 'UNSERVED' COMMENT '上菜状态',"
                    + "price_at_order DECIMAL(8,2) NOT NULL COMMENT '下单时价格',"
                    + "FOREIGN KEY (order_id) REFERENCES table_orders(order_id) ON DELETE CASCADE,"
                    + "FOREIGN KEY (item_id) REFERENCES menu_items(item_id) ON DELETE RESTRICT"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // 9. 撤销菜品记录表
            stmt.execute("CREATE TABLE IF NOT EXISTS item_cancellations ("
                    + "cancellation_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '撤销记录ID',"
                    + "item_code VARCHAR(10) NOT NULL COMMENT '菜品编号',"
                    + "cancelled_quantity INT NOT NULL COMMENT '撤销数量',"
                    + "cancellation_reason VARCHAR(255) NOT NULL COMMENT '撤销原因',"
                    + "cancellation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '撤销时间',"
                    + "before_status ENUM('UNSERVED', 'PARTIALLY_SERVED', 'SERVED') NOT NULL COMMENT '撤销前状态'"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // 10. 队列表
            stmt.execute("CREATE TABLE IF NOT EXISTS queues ("
                    + "queue_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '队列ID',"
                    + "queue_type ENUM('2_SEAT', '4_SEAT', '6_SEAT') NOT NULL COMMENT '队列类型',"
                    + "group_id INT NOT NULL COMMENT '顾客组ID',"
                    + "position INT NOT NULL COMMENT '队列位置',"
                    + "join_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',"
                    + "FOREIGN KEY (group_id) REFERENCES customer_groups(group_id) ON DELETE CASCADE,"
                    + "UNIQUE KEY unique_position (queue_type, position) COMMENT '同一类型队列中位置唯一',"
                    + "INDEX idx_queue_type_position (queue_type, position),"
                    + "INDEX idx_join_time (join_time)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // ============== 延迟添加循环依赖的外键 ==============
            try {
                stmt.execute("ALTER TABLE restaurant_tables "
                        + "ADD CONSTRAINT fk_current_group FOREIGN KEY (current_group_id) "
                        + "REFERENCES customer_groups(group_id) ON DELETE SET NULL");
            } catch (SQLException e) {
                if (!e.getMessage().contains("Duplicate foreign key constraint name")) {
                    System.err.println("⚠️ 添加外键约束时出错: " + e.getMessage());
                }
            }

            try {
                stmt.execute("ALTER TABLE customer_groups "
                        + "ADD CONSTRAINT fk_assigned_table FOREIGN KEY (table_id) "
                        + "REFERENCES restaurant_tables(table_id) ON DELETE SET NULL");
            } catch (SQLException e) {
                if (!e.getMessage().contains("Duplicate foreign key constraint name")) {
                    System.err.println("⚠️ 添加外键约束时出错: " + e.getMessage());
                }
            }

            // ============== 为报表功能添加索引 ==============
            try {
                createIndexSafely(conn, "table_orders", "idx_order_time_status", "order_time, status");
                createIndexSafely(conn, "customer_groups", "idx_cg_table_time", "table_id, start_time");
                createIndexSafely(conn, "item_quarterly_sales", "idx_sales_date", "sale_timestamp");
                createIndexSafely(conn, "item_quarterly_sales", "idx_sales_year_quarter", "year, quarter");
                createIndexSafely(conn, "business_status", "idx_bs_date", "business_date");
                System.out.println("✅ 成功创建所有索引");
            } catch (SQLException e) {
                System.err.println("❌ 创建索引失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取数据库连接
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            initializePool();
        }
        return dataSource.getConnection();
    }

    /**
     * 关闭连接池
     */
    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("CloseOperation: 数据库连接池已关闭");
        }
    }

    /**
     * 测试数据库连接
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            boolean isValid = conn.isValid(2);
            System.out.println("数据库连接测试: " + (isValid ? "成功" : "失败"));
            return isValid;
        } catch (Exception e) {
            System.err.println("数据库连接测试失败: " + e.getMessage());
            return false;
        }
    }
}