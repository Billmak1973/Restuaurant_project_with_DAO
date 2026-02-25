package com.restaurant.dao;

import com.restaurant.entity.MenuItem;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface MenuItemDAO {
    boolean addItem(MenuItem item) throws SQLException;
    /**
     * 根据分类ID查询菜品
     * @param categoryId 菜单分类ID (1=特色食物, 2=饮料, 3=小炒, 4=套餐)
     * @return 菜品列表
     * @throws SQLException
     */
    List<MenuItem> findByCategory(int categoryId) throws SQLException;

    /**
     * 更新菜品售卖状态
     * @param itemCode 菜品编号（如 "A1", "B3"）
     * @param isActive true=售卖中, false=已售罄
     * @return 是否更新成功
     * @throws SQLException
     */
    boolean updateStatus(String itemCode, boolean isActive) throws SQLException;

    /**
     * 根据菜品编号查询菜品
     * @param itemCode 菜品编号（如 "A1", "B3"）
     * @return 菜品对象，未找到返回 null
     * @throws SQLException 数据库异常
     */
    MenuItem findById(String itemCode) throws SQLException;


    /**
     * 纯物理删除菜品（不检查订单引用，不操作 order_items）
     * @param itemCode 菜品编号
     * @return 删除成功返回 true
     * @throws SQLException 仅当数据库操作失败时抛出（如连接问题）
     */
    boolean deletePhysically(String itemCode) throws SQLException;

    /**
     * 检查菜品是否存在于历史订单中
     * @param itemCode 菜品编号
     * @return 存在返回 true
     * @throws SQLException 仅当数据库操作失败时抛出
     */
    boolean existsInOrderItems(String itemCode) throws SQLException;

    /**
     * 更新菜品价格（纯数据持久化，不含业务验证）
     * @param itemCode 菜品编号（如 "A1"）
     * @param newPrice 新价格（必须 > 0）
     * @return 更新成功返回 true
     * @throws SQLException 数据库操作异常
     */
    boolean updatePrice(String itemCode, double newPrice) throws SQLException;

    /**
     * 通过菜品编号查询数据库ID
     */
    Integer findItemIdByCode(Connection conn, String itemCode) throws SQLException;
}
