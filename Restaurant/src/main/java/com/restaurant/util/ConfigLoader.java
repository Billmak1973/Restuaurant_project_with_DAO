package com.restaurant.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 配置加载器 - 从配置文件加载应用配置
 */
public class ConfigLoader {
    private static final Properties properties = new Properties();
    private static boolean initialized = false;

    static {
        initialize();
    }

    /**
     * 初始化配置加载器，尝试从类路径和工作目录加载配置
     */
    private static void initialize() {
        if (initialized) return;

        try {
            // 1. 尝试从类路径加载配置文件
            InputStream resourceStream = ConfigLoader.class.getClassLoader().getResourceAsStream("database.properties");
            if (resourceStream != null) {
                properties.load(resourceStream);
                System.out.println("✅ 从类路径加载配置文件成功");
            }
            // 2. 如果类路径中没有，尝试从工作目录加载
            else {
                java.io.File file = new java.io.File("database.properties");
                if (file.exists()) {
                    properties.load(new java.io.FileInputStream(file));
                    System.out.println("✅ 从工作目录加载配置文件成功: " + file.getAbsolutePath());
                }
                // 3. 都找不到时使用默认配置
                else {
                    loadDefaultProperties();
                    System.out.println("⚠️ 未找到配置文件，使用默认配置");
                }
            }

            // 4. 验证必需的配置项
            validateRequiredProperties();
            initialized = true;

        } catch (IOException e) {
            System.err.println("❌ 加载配置文件失败: " + e.getMessage());
            loadDefaultProperties(); // 回退到默认配置
        }
    }

    /**
     * 加载默认配置（当配置文件不存在时）
     */
    private static void loadDefaultProperties() {
        properties.setProperty("database.url", "jdbc:mysql://localhost:3306/");
        properties.setProperty("database.username", "root");
        properties.setProperty("database.password", "1234");
        properties.setProperty("database.name", "restaurant_init_db");
        properties.setProperty("database.driver", "com.mysql.cj.jdbc.Driver");
        properties.setProperty("pool.maximumPoolSize", "10");
        properties.setProperty("pool.minimumIdle", "2");
        properties.setProperty("pool.connectionTimeout", "20000");
        properties.setProperty("pool.idleTimeout", "30000");
        properties.setProperty("pool.maxLifetime", "1800000");
    }

    /**
     * 验证必需的配置项是否存在
     */
    private static void validateRequiredProperties() {
        String[] requiredKeys = {
                "database.url",
                "database.username",
                "database.name",
                "database.driver"
        };

        for (String key : requiredKeys) {
            if (properties.getProperty(key) == null || properties.getProperty(key).trim().isEmpty()) {
                throw new IllegalStateException("缺少必需的配置项: " + key);
            }
        }
    }

    /**
     * 获取字符串类型的配置属性
     */
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * 获取字符串类型的配置属性，带默认值
     */
    public static String getProperty(String key, String defaultValue) {
        String value = properties.getProperty(key);
        return (value != null && !value.trim().isEmpty()) ? value : defaultValue;
    }

    /**
     * 获取整数类型的配置属性
     */
    public static int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        try {
            return (value != null) ? Integer.parseInt(value.trim()) : defaultValue;
        } catch (NumberFormatException e) {
            System.err.println("⚠️ 配置项 " + key + " 的值 '" + value + "' 不是有效整数，使用默认值 " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取长整型配置属性
     */
    public static long getLongProperty(String key, long defaultValue) {
        String value = getProperty(key);
        try {
            return (value != null) ? Long.parseLong(value.trim()) : defaultValue;
        } catch (NumberFormatException e) {
            System.err.println("⚠️ 配置项 " + key + " 的值 '" + value + "' 不是有效长整数，使用默认值 " + defaultValue);
            return defaultValue;
        }
    }
}