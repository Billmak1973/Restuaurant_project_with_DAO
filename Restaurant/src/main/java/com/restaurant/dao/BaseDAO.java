package com.restaurant.dao;

import java.sql.SQLException;


public interface BaseDAO<T> {
    /**
     * 保存实体到数据库
     * @param entity 要保存的实体
     * @return 保存后的实体（包含生成的ID等）
     * @throws SQLException 数据库操作异常
     */
    T save(T entity) throws SQLException;

    /**
     * 根据ID查找实体
     * @param id 实体ID
     * @return 找到的实体，未找到返回null
     * @throws SQLException 数据库操作异常
     */
    T findById(int id) throws SQLException;

    /**
     * 更新实体
     * @param entity 要更新的实体
     * @return 更新是否成功
     * @throws SQLException 数据库操作异常
     */
    boolean update(T entity) throws SQLException;
}