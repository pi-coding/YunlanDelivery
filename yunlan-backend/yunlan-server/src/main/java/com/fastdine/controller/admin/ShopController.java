package com.fastdine.controller.admin;

import com.fastdine.constant.ShopConstant;
import com.fastdine.constant.StatusConstant;
import com.fastdine.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 店铺管理（管理端）
 */
@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Api(tags = "店铺相关接口")
@Slf4j
public class ShopController {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 设置店铺的营业状态
     *
     * @param status 1 营业中，0 打烊中
     * @return
     */
    @PutMapping("/{status}")
    @ApiOperation("设置店铺的营业状态")
    public Result setStatus(@PathVariable Integer status) {
        log.info("设置店铺的营业状态为：{}", StatusConstant.ENABLE.equals(status) ? "营业中" : "打烊中");
        redisTemplate.opsForValue().set(ShopConstant.SHOP_STATUS_KEY, status);
        return Result.success();
    }

    /**
     * 查询店铺的营业状态
     *
     * @return 1 营业中，0 打烊中；从未设置过时返回 null
     */
    @GetMapping("/status")
    @ApiOperation("获取店铺的营业状态")
    public Result<Integer> getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(ShopConstant.SHOP_STATUS_KEY);
        log.info("获取到店铺的营业状态为：{}", StatusConstant.ENABLE.equals(status) ? "营业中" : "打烊中");
        return Result.success(status);
    }
}
