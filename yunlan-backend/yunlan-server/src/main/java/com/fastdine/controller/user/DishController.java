package com.fastdine.controller.user;

import com.fastdine.constant.StatusConstant;
import com.fastdine.entity.Dish;
import com.fastdine.result.Result;
import com.fastdine.service.DishService;
import com.fastdine.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端菜品浏览接口（只返回起售中的菜品）
 */
@RestController("userDishController")
@RequestMapping("/user/dish")
@Api(tags = "C端-菜品浏览接口")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;

    /**
     * 根据分类id查询起售中的菜品（含口味）
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询起售中的菜品")
    @Cacheable(cacheNames = "dishCache", key = "#categoryId == null ? 'all' : #categoryId")
    public Result<List<DishVO>> list(Long categoryId) {
        log.info("C端查询菜品（未命中缓存）：categoryId={}", categoryId);

        Dish dish = Dish.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();

        return Result.success(dishService.listWithFlavor(dish));
    }
}
