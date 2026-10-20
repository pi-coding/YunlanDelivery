package com.fastdine.controller.user;

import com.fastdine.entity.Category;
import com.fastdine.result.Result;
import com.fastdine.service.CategoryService;
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
 * C 端分类接口。
 * 与管理端 CategoryController 同名，因此显式指定 bean 名 userCategoryController。
 */
@RestController("userCategoryController")
@RequestMapping("/user/category")
@Api(tags = "C端-分类接口")
@Slf4j
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 根据类型查询分类（type=1 菜品分类，2 套餐分类）
     *
     * @param type
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据类型查询分类")
    //key 必须 null 安全：小程序首页调 getCategoryList() 不带 type，
    //SpEL 求值为 null 时 Spring Cache 会抛 "Null key returned for cache operation" → 整个接口 500
    @Cacheable(cacheNames = "categoryCache", key = "#type == null ? 'all' : #type")
    public Result<List<Category>> list(Integer type) {
        log.info("C端查询分类（未命中缓存）：type={}", type);
        return Result.success(categoryService.list(type));
    }
}
