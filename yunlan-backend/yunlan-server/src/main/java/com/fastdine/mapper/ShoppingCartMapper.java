package com.fastdine.mapper;

import com.fastdine.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    /**
     * 条件查询购物车（查看购物车：只传 userId）
     *
     * @param shoppingCart
     * @return
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 查询当前用户购物车中是否已有「同一菜品+同口味」或「同一套餐」的记录，用于判断是累加还是新增
     *
     * @param shoppingCart
     * @return 查不到返回 null
     */
    ShoppingCart getByDishOrSetmeal(ShoppingCart shoppingCart);

    /**
     * 插入购物车数据，并回填主键
     *
     * @param shoppingCart
     */
    void insert(ShoppingCart shoppingCart);

    /**
     * 修改商品数量
     *
     * @param shoppingCart
     */
    @Update("update shopping_cart set number = #{number} where id = #{id}")
    void updateNumberById(ShoppingCart shoppingCart);

    /**
     * 根据主键删除购物车中的一条
     *
     * @param id
     */
    @Delete("delete from shopping_cart where id = #{id}")
    void deleteById(Long id);

    /**
     * 清空当前用户的购物车
     *
     * @param userId
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteByUserId(Long userId);

    /**
     * 统计当前用户购物车中的商品种类数（下单前校验用）
     *
     * @param userId
     * @return
     */
    @Select("select count(id) from shopping_cart where user_id = #{userId}")
    Integer countByUserId(Long userId);

    /**
     * 批量插入购物车（「再来一单」把历史订单明细回写到购物车）
     *
     * @param shoppingCartList
     */
    void insertBatch(List<ShoppingCart> shoppingCartList);
}
