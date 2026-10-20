package com.fastdine.controller.admin;

import com.fastdine.result.Result;
import com.fastdine.service.WorkspaceService;
import com.fastdine.vo.BusinessDataVO;
import com.fastdine.vo.DishOverViewVO;
import com.fastdine.vo.OrderOverViewVO;
import com.fastdine.vo.SetmealOverViewVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 管理端工作台接口
 */
@RestController
@RequestMapping("/admin/workspace")
@Api(tags = "管理端-工作台接口")
@Slf4j
public class WorkspaceController {

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 今日运营数据
     *
     * @return 营业额 / 有效订单 / 完成率 / 客单价 / 新增用户
     */
    /**
     * 运营数据（营业额 / 有效订单 / 完成率 / 客单价 / 新增用户）
     * <p>
     * 路径同时挂 businessData 和 todayData：
     * 课程前端（管理端 dist）调的是 /api/workspace/businessData，
     * 而本文档和自测一直用 todayData，两个都保留最省事。
     *
     * @return
     */
    @GetMapping({"/businessData", "/todayData"})
    @ApiOperation("运营数据")
    public Result<BusinessDataVO> todayData() {
        LocalDate today = LocalDate.now();
        log.info("今日运营数据：{}", today);
        return Result.success(workspaceService.getBusinessData(today, today));
    }

    /**
     * 订单概览
     *
     * @return
     */
    @GetMapping("/overviewOrders")
    @ApiOperation("订单概览")
    public Result<OrderOverViewVO> overviewOrders() {
        log.info("订单概览");
        return Result.success(workspaceService.getOrderOverView());
    }

    /**
     * 菜品概览
     *
     * @return
     */
    @GetMapping("/overviewDishes")
    @ApiOperation("菜品概览")
    public Result<DishOverViewVO> overviewDishes() {
        log.info("菜品概览");
        return Result.success(workspaceService.getDishOverView());
    }

    /**
     * 套餐概览
     *
     * @return
     */
    @GetMapping("/overviewSetmeals")
    @ApiOperation("套餐概览")
    public Result<SetmealOverViewVO> overviewSetmeals() {
        log.info("套餐概览");
        return Result.success(workspaceService.getSetmealOverView());
    }
}
