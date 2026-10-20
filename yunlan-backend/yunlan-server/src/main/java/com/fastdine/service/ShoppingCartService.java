package com.fastdine.service;

import com.fastdine.dto.ShoppingCartDTO;
import com.fastdine.entity.ShoppingCart;

import java.util.List;

/**
 * C 端购物车服务
 */
public interface ShoppingCartService {

    /**
     * 添加购物车：同一菜品+口味（或同一套餐）已存在则数量 +1，否则新增一条
     *
     * @param shoppingCartDTO
     */
    void add(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查看当前登录用户的购物车
     *
     * @return
     */
    List<ShoppingCart> showShoppingCart();

    /**
     * 删除购物车中的一个商品：数量为 1 时删除整条，否则数量 -1
     *
     * @param shoppingCartDTO
     */
    void sub(ShoppingCartDTO shoppingCartDTO);

    /**
     * 清空当前登录用户的购物车
     */
    void clean();
}
