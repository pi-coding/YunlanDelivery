package com.fastdine.service;

import com.fastdine.vo.BusinessDataVO;
import com.fastdine.vo.DishOverViewVO;
import com.fastdine.vo.OrderOverViewVO;
import com.fastdine.vo.SetmealOverViewVO;

import java.time.LocalDate;

public interface WorkspaceService {

    /**
     * 工作台核心数据：营业额 / 有效订单 / 完成率 / 客单价 / 新增用户
     *
     * @param begin 区间开始（当天 00:00:00）
     * @param end   区间结束（当天 23:59:59）
     * @return
     */
    BusinessDataVO getBusinessData(LocalDate begin, LocalDate end);

    /**
     * 订单概览：待接单 / 已接单 / 已完成 / 已取消 / 全部
     *
     * @return
     */
    OrderOverViewVO getOrderOverView();

    /**
     * 菜品概览：起售 / 停售
     *
     * @return
     */
    DishOverViewVO getDishOverView();

    /**
     * 套餐概览：起售 / 停售
     *
     * @return
     */
    SetmealOverViewVO getSetmealOverView();
}
