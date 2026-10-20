package com.fastdine.service.impl;

import com.fastdine.entity.Orders;
import com.fastdine.mapper.DishMapper;
import com.fastdine.mapper.OrderMapper;
import com.fastdine.mapper.SetmealMapper;
import com.fastdine.mapper.UserMapper;
import com.fastdine.service.WorkspaceService;
import com.fastdine.vo.BusinessDataVO;
import com.fastdine.vo.DishOverViewVO;
import com.fastdine.vo.OrderOverViewVO;
import com.fastdine.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 工作台核心数据
     */
    public BusinessDataVO getBusinessData(LocalDate begin, LocalDate end) {
        //1、营业额：区间内已完成订单金额合计
        Map<String, Object> turnoverMap = new HashMap<>(4);
        turnoverMap.put("begin", begin.atStartOfDay());
        turnoverMap.put("end", end.atTime(LocalTime.MAX));
        turnoverMap.put("status", Orders.COMPLETED);
        Double turnover = orderMapper.sumByMap(turnoverMap);
        turnover = turnover == null ? 0.0 : turnover;

        //2、有效订单数 / 订单总数
        Integer validOrderCount = orderMapper.countByMap(turnoverMap);

        Map<String, Object> totalMap = new HashMap<>(2);
        totalMap.put("begin", begin.atStartOfDay());
        totalMap.put("end", end.atTime(LocalTime.MAX));
        Integer totalOrderCount = orderMapper.countByMap(totalMap);

        //3、订单完成率
        double orderCompletionRate = 0.0;
        if (totalOrderCount != null && totalOrderCount != 0 && validOrderCount != null) {
            orderCompletionRate = round2(validOrderCount * 1.0 / totalOrderCount);
        }

        //4、平均客单价
        double unitPrice = 0.0;
        if (validOrderCount != null && validOrderCount != 0) {
            unitPrice = round2(turnover / validOrderCount);
        }

        //5、新增用户数（区间内注册）
        Map<String, Object> userMap = new HashMap<>(2);
        userMap.put("begin", begin.atStartOfDay());
        userMap.put("end", end.atTime(LocalTime.MAX));
        Integer newUsers = userMapper.countByMap(userMap);

        return BusinessDataVO.builder()
                .turnover(round2(turnover))
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }

    /**
     * 订单概览
     */
    public OrderOverViewVO getOrderOverView() {
        Map<String, Object> allMap = new HashMap<>(1);
        Integer allOrders = orderMapper.countByMap(allMap);

        return OrderOverViewVO.builder()
                .waitingOrders(orderMapper.countByStatus(Orders.TO_BE_CONFIRMED))
                .deliveredOrders(orderMapper.countByStatus(Orders.CONFIRMED))
                .completedOrders(orderMapper.countByStatus(Orders.COMPLETED))
                .cancelledOrders(orderMapper.countByStatus(Orders.CANCELLED))
                .allOrders(allOrders)
                .build();
    }

    /**
     * 菜品概览
     */
    public DishOverViewVO getDishOverView() {
        return DishOverViewVO.builder()
                .sold(dishMapper.countByStatus(1))
                .discontinued(dishMapper.countByStatus(0))
                .build();
    }

    /**
     * 套餐概览
     */
    public SetmealOverViewVO getSetmealOverView() {
        return SetmealOverViewVO.builder()
                .sold(setmealMapper.countByStatus(1))
                .discontinued(setmealMapper.countByStatus(0))
                .build();
    }

    private double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
