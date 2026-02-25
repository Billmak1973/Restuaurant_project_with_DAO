package com.restaurant.entity;

/**
 * 季度销售统计用的订单项分组容器
 * 用于按 (item_code + item_name + sale_price) 三元组聚合相同商品
 */
public class OrderItemGroup {
    private String itemCode;
    private String itemName;
    private double salePrice;
    private int quantity;
    private Integer salesId; // 仅用于更新操作，插入时为 null

    public OrderItemGroup(String itemCode, String itemName, double salePrice, int quantity) {
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.salePrice = salePrice;
        this.quantity = quantity;
        this.salesId = null;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public double getSalePrice() {
        return salePrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void addQuantity(int additional) {
        this.quantity += additional;
    }

    public Integer getSalesId() {
        return salesId;
    }

    public void setSalesId(Integer salesId) {
        this.salesId = salesId;
    }
}