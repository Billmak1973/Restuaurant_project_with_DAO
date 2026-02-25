package com.restaurant.dao;

import com.restaurant.entity.CustomerGroup;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface CustomerGroupDAO extends BaseDAO<CustomerGroup> {


    /**
     * 更新顾客组的餐桌分配状态
     *
     * @param groupId          顾客组ID
     * @param tableId          餐桌ID，可为null
     * @param isAssigned       是否已分配
     * @param shownWaitMessage 是否已显示等待提示
     * @return 更新是否成功
     * @throws SQLException 数据库操作异常
     */
    boolean updateAssignmentStatus(int groupId, Integer tableId, boolean isAssigned,
                                   boolean shownWaitMessage) throws SQLException;

    boolean updateAssignmentStatus(Connection conn,int groupId, Integer tableId, boolean isAssigned,
                                   boolean shownWaitMessage) throws SQLException;
    /**
     * 在指定事务连接中删除顾客组（用于事务场景）
     *
     * @param conn 外部事务连接
     * @param id   顾客组ID
     * @return 删除是否成功
     * @throws SQLException
     */
    boolean delete(Connection conn, int id) throws SQLException;


    CustomerGroup findByCallNumber(Connection conn, int callNumber) throws SQLException;

    CustomerGroup save(Connection conn, CustomerGroup group) throws SQLException;

    CustomerGroup findById(Connection conn, int id) throws SQLException;

    boolean update(Connection conn, CustomerGroup group) throws SQLException;

    /**
     * 保存顾客组（不关联餐桌）
     *
     * @param conn  事务连接
     * @param group 顾客组对象（table_id将设为NULL）
     * @throws SQLException
     */
    void saveWithoutTableRef(Connection conn, CustomerGroup group) throws SQLException;

    /**
     * 更新顾客组关联的餐桌ID
     *
     * @param conn    事务连接
     * @param groupId 顾客组ID
     * @param tableId 餐桌ID
     * @throws SQLException
     */
    void updateTableId(Connection conn, int groupId, int tableId) throws SQLException;
}