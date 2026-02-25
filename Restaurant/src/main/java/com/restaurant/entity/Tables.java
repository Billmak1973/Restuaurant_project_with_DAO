package com.restaurant.entity;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class Tables {


    public enum TableStatus {
        VACANT("空闲"),
        OCCUPIED("占用中"),
        SETTING_UP("准备中"),
        SPLITTING("拆分中");

        private final String displayName;

        TableStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum TableType {
        MAIN("主桌"),
        MERGED("合并桌"),
        SUBTABLE("子桌");
        private final String displayName;

        TableType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }

    }

    public  enum OrderStatus {
        NO_ORDER("未下单"),
        ORDERED_UNFINISHED("已下单(未完成)"),
        ORDERED_FINISHED("已下单(已完成)"),
        CHECKED_OUT("已结账");

        private final String displayName;
        OrderStatus(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private int table_id; // 数据库ID
    private int baseId; // 基础ID
    private String displayId; // 显示ID
    private int capacity; // 容量
    private TableStatus status; // 餐桌状态
    private TableType tableType; // 餐桌类型
    private CustomerGroup currentGroup; // 当前占用的顾客组
    private Integer customerGroupId; // 顾客组在数据库中的ID（用于持久化）
    private LocalDateTime startTime; // 占用开始时间
    private LocalDateTime endTime; // 占用结束时间
    private boolean isSplit; // 是否拆分
    private String subTableSuffix; // 子桌后缀
    private Integer mainTableId; // 主桌ID
    private int physicalCapacity; // 物理容量
    private String mergedWith; // 合并伙伴的display_id
    private Integer currentGroupId;
    private int actualSeats = 0; // 实际入座人数
    private transient CustomerGroup pendingGroup;
    private transient OrderStatus orderStatus = OrderStatus.NO_ORDER;

    /**
     * 创建新餐桌对象（默认空闲状态）
     * @param baseId 数据库存储ID
     * @param capacity 座位容量（1/2/4/6人）
     * @param displayId UI显示编号（如"7"或"7a"）
     */
    public Tables(int baseId, int capacity, String displayId) {
        this.baseId = baseId;
        this.capacity = capacity;
        this.displayId = displayId;
        this.status = TableStatus.VACANT; // 默认状态为空闲
        this.tableType = TableType.MAIN; // 默认类型为主桌
        this.currentGroup = null;
        this.customerGroupId = null;
        this.isSplit = false;
        this.subTableSuffix = null;
        this.mainTableId = null;
        this.physicalCapacity = capacity; // 默认物理容量等于当前容量
    }

    /** 数据库存储ID */
    public int getTableId() {
        return table_id;
    }

    /** 设置数据库存储ID */
    public void setTableId(int table_id) {
        this.table_id = table_id;
    }

    /** 基础餐桌ID（拆分/合并时关联） */
    public int getBaseId() {
        return baseId;
    }

    public void setBaseId(int baseId) {
        this.baseId = baseId;
    }

    public Integer getCurrentGroupId() {
        return currentGroupId;
    }

    public void setCurrentGroupId(Integer currentGroupId) {
        this.currentGroupId = currentGroupId;
    }
    /** UI显示编号（如"7a"） */
    public String getDisplayId() {
        return displayId;
    }

    /** 设置UI显示编号 */
    public void setDisplayId(String displayId) {
        this.displayId = displayId;
    }

    /** 座位容量（1/2/4/6人） */
    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
    /** 获取餐桌当前状态 */
    public TableStatus getStatus() {
        return status;
    }

    /** 获取合并后的物理总容量 */
    public int getPhysicalCapacity() {
        return physicalCapacity;
    }

    /** 设置合并后的物理总容量 */
    public void setPhysicalCapacity(int physicalCapacity) {
        this.physicalCapacity = physicalCapacity;
    }

    /** 获取合并餐桌ID（格式"7+8"） */
    public String getMergedWith() {
        return mergedWith;
    }

    /** 设置合并关系标识 */
    public void setMergedWith(String mergedWith) {
        this.mergedWith = mergedWith;
    }

    /**
     * 设置餐桌状态（自动清理关联数据）
     * @note OCCUPIED: 记录开始时间
     *       VACANT/SETTING_UP: 清空所有顾客数据
     *       SPLITTING: 保留拆分标识，清除顾客组
     */
    public void setStatus(TableStatus status) {
        this.status = status;

        if (status == TableStatus.OCCUPIED) {
            // 占用状态：设置开始时间，清空结束时间
            this.startTime = LocalDateTime.now();
            this.endTime = null; // 确保结束时间为空
        } else if (status == TableStatus.VACANT || status == TableStatus.SETTING_UP) {
            // 空闲或准备中状态：清空当前顾客组和相关数据
            this.currentGroup = null;
            this.currentGroupId = null;
            this.startTime = null;
            this.endTime = null;
            this.mergedWith = null;
            this.actualSeats = 0;
        }
        // SPLITTING状态需要特殊处理 - 不清空关键数据
        else if (status == TableStatus.SPLITTING) {
            // 保持isSplit和mainTableId等关键信息
            this.currentGroup = null; // 清除当前顾客组引用
            this.startTime = null;    // 清空开始时间
            this.endTime = null;      // 清空结束时间
            this.mergedWith = null;   // 清除合并关系
            this.actualSeats = 0;     // 重置实际座位数
            // 但保留 isSplit = true 和 mainTableId 等拆分相关的关键状态
        }
    }


    /** 餐桌类型（主桌/合并桌/子桌） */
    public TableType getTableType() {
        return tableType;
    }

    /** 设置餐桌类型 */
    public void setTableType(TableType tableType) {
        this.tableType = tableType;
    }


    public Integer getCustomerGroupId() {
        return customerGroupId;
    }

    public void setCustomerGroupId(Integer customerGroupId) {
        this.customerGroupId = customerGroupId;
    }

    /**
     * 用餐开始时间（占用时记录）
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /** 设置用餐开始时间 */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * 用餐结束时间（离店时记录）
     */
    public LocalDateTime getEndTime() {
        return endTime;
    }

    /** 设置用餐结束时间 */
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }



    /** 是否处于拆分状态 */
    public boolean isSplit() {
        return isSplit;
    }

    /** 设置拆分状态标记 */
    public void setSplit(boolean split) {
        isSplit = split;
    }

    /** 子餐桌后缀（如"a","b"，主桌为空） */
    public String getSubTableSuffix() {
        return subTableSuffix;
    }

    /** 设置子餐桌标识后缀 */
    public void setSubTableSuffix(String subTableSuffix) {
        this.subTableSuffix = subTableSuffix;
    }

    /** 所属主餐桌ID（子桌时有效） */
    public Integer getMainTableId() {
        return mainTableId;
    }

    /** 设置所属主餐桌ID */
    public void setMainTableId(Integer mainTableId) {
        this.mainTableId = mainTableId;
    }

    /** 格式化开始时间（空时返回"未开始"） */
    public String getFormattedStartTime() {
        if (startTime != null) {
            return startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return "未开始";
    }

    /** 格式化结束时间（空时返回"未结束"） */
    public String getFormattedEndTime() {
        if (endTime != null) {
            return endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return "未结束";
    }

    // 修改 setCurrentGroup 方法 - 移除ID设置逻辑
    public void setCurrentGroup(CustomerGroup group) {
        this.currentGroup = group;
        // 不再设置 currentGroupId
    }

    // 添加专用方法，确保组ID和对象同步
    public void assignCustomerGroup(CustomerGroup group) {
        this.currentGroup = group;
        this.currentGroupId = (group != null) ? group.getGroup_id() : null;
        // 同时设置时间戳
        if (group != null && this.status == TableStatus.OCCUPIED) {
            this.startTime = LocalDateTime.now();
        }
    }

    // 添加状态验证方法
//    public boolean isConsistent() {
//        if (status == TableStatus.OCCUPIED) {
//            boolean hasGroup = currentGroup != null && currentGroupId != null;
//            if (!hasGroup) {
//                System.err.println("⚠️ 餐桌 #" + displayId + " 状态为OCCUPIED，但组数据不完整");
//                return false;
//            }
//            return currentGroupId.equals(currentGroup.getGroup_id());
//        }
//        return true;
//    }

    public boolean isConsistent() {
        if (status == TableStatus.OCCUPIED) {
            if (currentGroupId == null) {
                System.err.println("⚠️ 餐桌 #" + displayId + " 狀態為OCCUPIED，但currentGroupId為空");
                return false;
            }

            if (currentGroup == null) {
                // 允許currentGroup為null，但currentGroupId必須有值
                return true;
            }

            // 當兩者都存在時，檢查ID是否匹配
            return currentGroupId.equals(currentGroup.getGroup_id());
        }
        return true;
    }
    public CustomerGroup getCurrentGroup() {
        // 如果ID存在但对象为null，尝试重新加载
        if (currentGroupId != null && currentGroup == null) {
            try {
                // 这里应该调用DAO加载，但为避免循环依赖，先简单处理
                // 实际应用中应有适当的数据加载机制
                System.err.println("警告：餐桌 #" + displayId + " 有group ID但无group对象");
            } catch (Exception e) {
                // 忽略错误，保持业务状态不变
            }
        }
        return currentGroup;
    }



    /** 合并餐桌实际使用座位数（独立餐桌始终=0） */
    public int getActualSeats() {
        return actualSeats;
    }

    /** 设置合并餐桌实际占用座位数 */
    public void setActualSeats(int actualSeats) {
        this.actualSeats = actualSeats;
    }

    /**
     * 获取待分配的顾客组
     * @return 未就座的顾客组引用
     */
    public CustomerGroup getPendingGroup() {
        return pendingGroup;
    }

    public OrderStatus getOrderStatus() { return orderStatus; }
    public void setOrderStatus(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }
    /**
     * 生成动态餐桌图标（含椅子占用状态）
     * @param tableColor 餐桌主体颜色
     * @note 支持合并/拆分状态，不同容量布局（1-6人）
     */
    public Icon createTableIcon(Color tableColor) {
        int size = 80;
        int chairSize = 15;
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // 颜色常量
                Color brown = new Color(139, 69, 19); // 空位颜色
                Color red = Color.RED; // 占位颜色

                // 绘制餐桌
                g2.setColor(tableColor);
                g2.fillOval(x + size / 4, y + size / 4, size / 2, size / 2);
                g2.setColor(Color.DARK_GRAY);
                g2.drawOval(x + size / 4, y + size / 4, size / 2, size / 2);

                // 确定椅子数量和颜色
                int chairCount;
                Color[] chairColors;

                // 检查是否为合并餐桌
                boolean isMerged = (tableType == TableType.MERGED && getStatus() == TableStatus.OCCUPIED);

                if (getStatus() == TableStatus.OCCUPIED && currentGroup != null) {
                    // 确定椅子数量
                    chairCount = capacity; // 使用餐桌容量作为椅子数量

                    // 初始化椅子颜色数组
                    chairColors = new Color[chairCount];
                    Arrays.fill(chairColors, brown); // 默认全部为棕色（空位）

                    // 确定实际占用的椅子数量
                    int occupiedChairs;

                    if (isMerged) {
                        // 合并餐桌场景：根据实际入座人数设置
                        occupiedChairs = actualSeats;
                    } else if (isSplit && subTableSuffix != null) {
                        // 拆分餐桌场景
                        occupiedChairs = currentGroup.getSize();
                    } else {
                        // 普通餐桌场景
                        occupiedChairs = Math.min(currentGroup.getSize(), capacity);
                    }

                    // 设置占用的椅子为红色
                    for (int i = 0; i < Math.min(occupiedChairs, chairCount); i++) {
                        chairColors[i] = red;
                    }
                } else {
                    // 非占用状态
                    chairCount = capacity;
                    chairColors = new Color[chairCount];
                    Arrays.fill(chairColors, brown);
                }

                // 绘制椅子
                if (capacity == 1) {
                    // 1人桌：始终将座位放在桌子边缘，而不是中心
                    if ("a".equals(subTableSuffix)) {
                        // 子桌a：座位在左边
                        drawChair(g2, x, y + size / 2 - chairSize / 2, chairSize, chairColors[0]);
                    } else if ("b".equals(subTableSuffix)) {
                        // 子桌b：座位在右边
                        drawChair(g2, x + size - chairSize, y + size / 2 - chairSize / 2, chairSize, chairColors[0]);
                    } else {
                        // 普通1人桌：座位在左边
                        drawChair(g2, x, y + size / 2 - chairSize / 2, chairSize, chairColors[0]);
                    }
                } else if (capacity == 2) {
                    // 2人桌：左右各一个座位
                    drawChair(g2, x, y + size / 2 - chairSize / 2, chairSize, chairColors[0]);
                    drawChair(g2, x + size - chairSize, y + size / 2 - chairSize / 2, chairSize, chairColors[1]);
                } else if (capacity == 4) {
                    // 4人桌：四边各一个座位
                    drawChair(g2, x, y + size / 2 - chairSize / 2, chairSize, chairColors[2]); // 左
                    drawChair(g2, x + size - chairSize, y + size / 2 - chairSize / 2, chairSize, chairColors[3]); // 右
                    drawChair(g2, x + size / 2 - chairSize / 2, y, chairSize, chairColors[0]); // 上
                    drawChair(g2, x + size / 2 - chairSize / 2, y + size - chairSize, chairSize, chairColors[1]); // 下
                } else if (capacity == 6) {
                    // 6人桌：圆形排列
                    int centerX = x + size / 2;
                    int centerY = y + size / 2;
                    int radius = (int) (size * 0.4);
                    for (int i = 0; i < 6; i++) {
                        double angle = Math.toRadians(360.0 / 6 * i - 90);
                        int chairX = (int) (centerX + radius * Math.cos(angle) - chairSize / 2);
                        int chairY = (int) (centerY + radius * Math.sin(angle) - chairSize / 2);
                        drawChair(g2, chairX, chairY, chairSize, chairColors[i]);
                    }
                } else {
                    // 其他容量的桌子
                    drawChair(g2, x + size / 2 - chairSize / 2, y + size / 2 - chairSize / 2, chairSize, chairColors[0]);
                }

                g2.dispose();
            }

            private void drawChair(Graphics2D g2, int x, int y, int size, Color color) {
                g2.setColor(color);
                g2.fillRect(x, y, size, size);
                g2.setColor(Color.BLACK);
                g2.drawRect(x, y, size, size);
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }
}