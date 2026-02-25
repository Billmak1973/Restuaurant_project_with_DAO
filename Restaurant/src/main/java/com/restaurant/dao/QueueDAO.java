package com.restaurant.dao;

import com.restaurant.entity.CustomerGroup;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface QueueDAO {
    /**
     * 事务安全：插入顾客组到指定队列
     * @param conn 外部事务连接
     * @param queueType 队列类型 ("2_SEAT"/"4_SEAT"/"6_SEAT")
     * @param groupId 顾客组ID
     * @param position 队列位置
     * @throws SQLException
     */
    void insertQueue(Connection conn, String queueType, int groupId, int position) throws SQLException;

    void updateQueuePositions(Connection conn, String queueType) throws SQLException;

    int getNextQueuePosition(Connection conn, String queueType) throws SQLException;

    /**
     * 根据顾客组ID查询其所在的队列类型
     * @param conn 事务连接
     * @param groupId 顾客组ID
     * @return 队列类型（"2_SEAT"/"4_SEAT"/"6_SEAT"），未找到返回 null
     * @throws SQLException
     */
    String findQueueTypeByGroupId(Connection conn, int groupId) throws SQLException;

    /**
     * 从指定队列中移除顾客组（仅数据库操作）
     * @param conn 事务连接
     * @param groupId 顾客组ID
     * @param queueType 队列类型 ("2_SEAT"/"4_SEAT"/"6_SEAT")
     * @throws SQLException
     */
    void removeFromQueue(Connection conn, int groupId, String queueType) throws SQLException;

    /**
     * 根据队列类型加载顾客组列表（事务安全版本）
     * @param conn 外部传入的事务连接
     * @param queueType 队列类型 ("2_SEAT"/"4_SEAT"/"6_SEAT")
     * @return 顾客组列表（按 position 排序）
     * @throws SQLException
     */
    List<CustomerGroup> loadQueueByType(Connection conn, String queueType) throws SQLException;
}
