package com.fastdine.controller.user;

import com.fastdine.constant.StatusConstant;
import com.fastdine.entity.Setmeal;
import com.fastdine.result.Result;
import com.fastdine.service.SetmealService;
import com.fastdine.vo.DishItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端套餐浏览接口（只返回起售中的套餐）
 */
@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
@Api(tags = "C端-套餐浏览接口")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    /**
     * 根据分类id查询起售中的套餐
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询起售中的套餐")
    @Cacheable(cacheNames = "setmealCache", key = "#categoryId == null ? 'all' : #categoryId")
    public Result<List<Setmeal>> list(Long categoryId) {
        log.info("C端查询套餐（未命中缓存）：categoryId={}", categoryId);

        Setmeal setmeal = Setmeal.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();

        return Result.success(setmealService.list(setmeal));
    }

    /**
     * 根据套餐id查询包含的菜品列表
     *
     * @param id
     * @return
     */
    @GetMapping("/dish/{id}")
    @ApiOperation("根据套餐id查询包含的菜品列表")
    public Result<List<DishItemVO>> dishList(@PathVariable("id") Long id) {
        log.info("C端查询套餐包含的菜品：setmealId={}", id);
        return Result.success(setmealService.getDishItemById(id));
    }
}
