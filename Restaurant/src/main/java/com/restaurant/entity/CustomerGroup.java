package com.restaurant.entity;

import java.time.LocalDateTime;

public class CustomerGroup {
    private int group_id;             // 数据库ID
    private int callNumber;     // 排队号
    private int size;           // 顾客组人数
    private LocalDateTime startTime;
    private boolean isAssigned; // 是否已分配餐桌
    private boolean shownWaitMessage; // 是否已显示等待提示
    private Integer tableId;    // 分配的餐桌ID (可能为null)
    private int position;
    private transient Tables pendingTable;

    /**
     * 创建一个新的顾客组
     *
     * @param callNumber 排队号码
     * @param size       顾客组人数
     */
    public CustomerGroup(int callNumber, int size) {
        this.callNumber = callNumber;
        this.size = size;
        this.startTime = LocalDateTime.now();
        this.isAssigned = false;
        this.shownWaitMessage = false;
        this.tableId = null;
        this.position = 0;
    }

    /**
     * 从数据库加载顾客组时使用的构造函数
     *
     * @param group_id               数据库ID
     * @param callNumber       排队号码
     * @param size             顾客组人数
     * @param startTime        入队/入座时间
     * @param isAssigned       是否已分配餐桌
     * @param shownWaitMessage 是否已显示等待提示
     * @param tableId          分配的餐桌ID
     */
    public CustomerGroup(int group_id, int callNumber, int size, LocalDateTime startTime,
                         boolean isAssigned, boolean shownWaitMessage, Integer tableId, int position) {
        this.group_id = group_id;
        this.callNumber = callNumber;
        this.size = size;
        this.startTime = startTime;
        this.isAssigned = isAssigned;
        this.shownWaitMessage = shownWaitMessage;
        this.tableId = tableId;
        this.position = position;

    }

    /** 数据库唯一标识 */
    public int getGroup_id() {
        return group_id;
    }

    /** 顾客组叫号（排队序号） */
    public int getCallNumber() {
        return callNumber;
    }

    public int getSize() {
        return size;
    }

    /** 入座/排队开始时间 */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /** 是否已分配餐桌 */
    public boolean isAssigned() {
        return isAssigned;
    }

    /** 是否已显示等待提示（防重复） */
    public boolean hasShownWaitMessage() {
        return shownWaitMessage;
    }

    /** 分配的餐桌ID（未分配时为null） */
    public Integer getTableId() {
        return tableId;
    }

    /** 设置数据库唯一ID */
    public void setGroup_id(int group_id) {
        this.group_id = group_id;
    }

    /** 设置入座/排队开始时间 */
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    /** 标记是否已分配餐桌 */
    public void setAssigned(boolean assigned) {
        isAssigned = assigned;
    }

    /** 设置分配的餐桌ID（未分配时为null） */
    public void setTableId(Integer tableId) {
        this.tableId = tableId;
    }

    /** 设置队列中的等待位置（1=队首） */
    public void setPosition(int position) {
        this.position = position;
    }

    /** 修改顾客组人数（需同步更新队列） */
    public void setSize(int size) {
        this.size = size;
    }

    /**
     * 标记顾客组已分配到餐桌
     *
     * @param tableId 餐桌ID
     */
    public void assignToTable(int tableId) {
        this.tableId = tableId;
        this.isAssigned = true;
    }

    /** 预分配餐桌（排队时指定的目标餐桌） */
    public Tables getPendingTable() {
        return pendingTable;
    }
    /** 设置预分配餐桌 */
    public void setPendingTable(Tables pendingTable) {
        this.pendingTable = pendingTable;
    }

    /** 顾客组状态字符串（格式：#12 (4人) [已入座/等待中]） */
    @Override
    public String toString() {
        return "顾客组 #" + callNumber +
                " (" + size + "人)" +
                (isAssigned ? " [已入座]" : " [等待中]");
    }
}