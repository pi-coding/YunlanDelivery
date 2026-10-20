package com.fastdine.service;

import com.fastdine.dto.DishDTO;
import com.fastdine.dto.DishPageQueryDTO;
import com.fastdine.entity.Dish;
import com.fastdine.result.PageResult;
import com.fastdine.vo.DishVO;

import java.util.List;

public interface DishService {

    /**
     * 新增菜品和对应的口味
     *
     * @param dishDTO
     */
    void saveWithFlavor(DishDTO dishDTO);

    /**
     * 菜品分页查询
     *
     * @param dishPageQueryDTO
     * @return
     */
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 菜品批量删除
     *
     * @param ids
     */
    void deleteBatch(List<Long> ids);

    /**
     * 根据id查询菜品和对应的口味数据
     *
     * @param id
     * @return
     */
    DishVO getByIdWithFlavor(Long id);

    /**
     * 根据id修改菜品基本信息和对应的口味信息
     *
     * @param dishDTO
     */
    void updateWithFlavor(DishDTO dishDTO);

    /**
     * 菜品起售停售
     *
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 条件查询菜品和口味（C 端商品浏览用，调用方负责把 status 设成起售中）
     *
     * @param dish
     * @return
     */
    List<DishVO> listWithFlavor(Dish dish);

    /**
     * 根据分类 id 查询起售中的菜品（管理端套餐管理里「选择菜品」用，不带口味）
     *
     * @param categoryId 分类 id，为空时查全部起售中的菜品
     * @return
     */
    List<Dish> list(Long categoryId);
}
