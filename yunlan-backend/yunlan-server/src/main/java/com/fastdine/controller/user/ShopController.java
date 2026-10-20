package com.fastdine.controller.user;

import com.fastdine.constant.ShopConstant;
import com.fastdine.constant.StatusConstant;
import com.fastdine.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺接口（C 端小程序只看营业状态，不能修改）
 */
@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "C端-店铺操作接口")
@Slf4j
public class ShopController {

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 商家客服电话，小程序「联系商家」用；号码来自配置，未配置时给默认值
     */
    @Value("${fastdine.shop.phone:400-618-4000}")
    private String shopPhone;

    /**
     * 查询店铺的营业状态
     *
     * @return 1 营业中，0 打烊中
     */
    @GetMapping("/status")
    @ApiOperation("获取营业状态")
    public Result<Integer> getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(ShopConstant.SHOP_STATUS_KEY);
        log.info("C端获取到店铺的营业状态为：{}", StatusConstant.ENABLE.equals(status) ? "营业中" : "打烊中");
        return Result.success(status);
    }

    /**
     * 查询商家客服电话
     *
     * @return 客服电话
     */
    @GetMapping("/phone")
    @ApiOperation("获取商家电话")
    public Result<String> getPhone() {
        return Result.success(shopPhone);
    }
}
