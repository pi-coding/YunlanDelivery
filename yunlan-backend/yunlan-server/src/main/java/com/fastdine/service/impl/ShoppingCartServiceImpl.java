package com.fastdine.service.impl;

import com.fastdine.constant.MessageConstant;
import com.fastdine.context.BaseContext;
import com.fastdine.dto.ShoppingCartDTO;
import com.fastdine.entity.Dish;
import com.fastdine.entity.Setmeal;
import com.fastdine.entity.ShoppingCart;
import com.fastdine.exception.ShoppingCartBusinessException;
import com.fastdine.mapper.DishMapper;
import com.fastdine.mapper.SetmealMapper;
import com.fastdine.mapper.ShoppingCartMapper;
import com.fastdine.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加购物车
     *
     * @param shoppingCartDTO
     */
    public void add(ShoppingCartDTO shoppingCartDTO) {
        checkParam(shoppingCartDTO);

        ShoppingCart query = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, query);
        query.setUserId(BaseContext.getCurrentId());

        //1、购物车里已经有「同一菜品+同口味」或「同一套餐」，数量加一即可
        ShoppingCart exist = shoppingCartMapper.getByDishOrSetmeal(query);
        if (exist != null) {
            exist.setNumber(exist.getNumber() + 1);
            shoppingCartMapper.updateNumberById(exist);
            return;
        }

        //2、否则新增一条，名称、图片、单价从菜品/套餐表里取，避免前端传假数据
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());
        shoppingCart.setNumber(1);
        shoppingCart.setCreateTime(LocalDateTime.now());

        if (shoppingCartDTO.getDishId() != null) {
            Dish dish = dishMapper.getById(shoppingCartDTO.getDishId());
            if (dish == null) {
                throw new ShoppingCartBusinessException(MessageConstant.DISH_NOT_FOUND);
            }
            shoppingCart.setName(dish.getName());
            shoppingCart.setImage(dish.getImage());
            shoppingCart.setAmount(dish.getPrice());
        } else {
            Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
            if (setmeal == null) {
                throw new ShoppingCartBusinessException(MessageConstant.SETMEAL_NOT_FOUND);
            }
            shoppingCart.setName(setmeal.getName());
            shoppingCart.setImage(setmeal.getImage());
            shoppingCart.setAmount(setmeal.getPrice());
        }

        shoppingCartMapper.insert(shoppingCart);
    }

    /**
     * 查看购物车
     *
     * @return
     */
    public List<ShoppingCart> showShoppingCart() {
        ShoppingCart query = new ShoppingCart();
        query.setUserId(BaseContext.getCurrentId());
        return shoppingCartMapper.list(query);
    }

    /**
     * 删除购物车中的一个商品
     *
     * @param shoppingCartDTO
     */
    public void sub(ShoppingCartDTO shoppingCartDTO) {
        checkParam(shoppingCartDTO);

        ShoppingCart query = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, query);
        query.setUserId(BaseContext.getCurrentId());

        ShoppingCart shoppingCart = shoppingCartMapper.getByDishOrSetmeal(query);
        if (shoppingCart == null) {
            //购物车里本来就没有，直接忽略
            return;
        }

        if (shoppingCart.getNumber() == null || shoppingCart.getNumber() <= 1) {
            shoppingCartMapper.deleteById(shoppingCart.getId());
        } else {
            shoppingCart.setNumber(shoppingCart.getNumber() - 1);
            shoppingCartMapper.updateNumberById(shoppingCart);
        }
    }

    /**
     * 清空购物车
     */
    public void clean() {
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());
    }

    /**
     * 菜品id与套餐id必须二选一
     */
    private void checkParam(ShoppingCartDTO dto) {
        if (dto.getDishId() == null && dto.getSetmealId() == null) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_PARAM_ERROR);
        }
    }
}
