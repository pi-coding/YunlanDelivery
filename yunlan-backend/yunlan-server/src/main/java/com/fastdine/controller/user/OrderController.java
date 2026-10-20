package com.fastdine.controller.user;

import com.fastdine.dto.OrdersPaymentDTO;
import com.fastdine.dto.OrdersSubmitDTO;
import com.fastdine.result.PageResult;
import com.fastdine.result.Result;
import com.fastdine.service.OrderService;
import com.fastdine.vo.OrderPaymentVO;
import com.fastdine.vo.OrderSubmitVO;
import com.fastdine.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * C 端订单接口
 */
@RestController("userOrderController")
@RequestMapping("/user/order")
@Api(tags = "C端-订单接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单：{}", ordersSubmitDTO);
        return Result.success(orderService.submitOrder(ordersSubmitDTO));
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return 小程序调起微信支付所需参数
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO.getOrderNumber());
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成支付参数：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 用户端历史订单分页查询
     *
     * @param page
     * @param pageSize
     * @param status   1待付款 2待接单 3已接单 4派送中 5已完成 6已取消，不传查全部
     * @return
     */
    @GetMapping({"/historyPage", "/historyOrders"})
    @ApiOperation("历史订单查询")
    public Result<PageResult> historyPage(int page, int pageSize, Integer status) {
        log.info("历史订单查询：page={}，pageSize={}，status={}", page, pageSize, status);
        return Result.success(orderService.pageQuery4User(page, pageSize, status));
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable("id") Long id) {
        log.info("查询订单详情：{}", id);
        return Result.success(orderService.details(id));
    }

    /**
     * 用户取消订单
     *
     * @param id
     * @return
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancel(@PathVariable("id") Long id) throws Exception {
        log.info("用户取消订单：{}", id);
        orderService.userCancelById(id);
        return Result.success();
    }

    /**
     * 再来一单
     *
     * @param id 历史订单 id
     * @return
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result repetition(@PathVariable("id") Long id) {
        log.info("再来一单：{}", id);
        orderService.repetition(id);
        return Result.success();
    }

    /**
     * 客户催单
     *
     * @param id 订单 id
     * @return
     */
    @GetMapping("/reminder/{id}")
    @ApiOperation("客户催单")
    public Result reminder(@PathVariable("id") Long id) {
        log.info("客户催单：{}", id);
        orderService.reminder(id);
        return Result.success();
    }
}
