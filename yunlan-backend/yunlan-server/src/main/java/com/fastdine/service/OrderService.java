package com.fastdine.service;

import com.fastdine.dto.OrdersCancelDTO;
import com.fastdine.dto.OrdersConfirmDTO;
import com.fastdine.dto.OrdersPageQueryDTO;
import com.fastdine.dto.OrdersPaymentDTO;
import com.fastdine.dto.OrdersRejectionDTO;
import com.fastdine.dto.OrdersSubmitDTO;
import com.fastdine.result.PageResult;
import com.fastdine.vo.OrderPaymentVO;
import com.fastdine.vo.OrderStatisticsVO;
import com.fastdine.vo.OrderSubmitVO;
import com.fastdine.vo.OrderVO;

public interface OrderService {

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return 订单 id / 订单号 / 金额 / 下单时间
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return 小程序调起支付所需的参数
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功后的状态流转：待付款 -> 待接单，支付状态改为已支付
     *
     * @param outTradeNo 订单号
     */
    void paySuccess(String outTradeNo);

    /**
     * 用户端历史订单分页
     *
     * @param page
     * @param pageSize
     * @param status   订单状态，为空或 0 查全部
     * @return
     */
    PageResult pageQuery4User(int page, int pageSize, Integer status);

    /**
     * 查询订单详情（含明细）
     *
     * @param id
     * @return
     */
    OrderVO details(Long id);

    /**
     * 用户取消订单
     *
     * @param id
     */
    void userCancelById(Long id);

    /**
     * 再来一单：把该订单的明细写回购物车
     *
     * @param id
     */
    void repetition(Long id);

    /**
     * 客户催单：向管理端 WebSocket 推送催单提醒
     *
     * @param id 订单 id
     */
    void reminder(Long id);

    // ------------------------------------------------------------ 管理端

    /**
     * 管理端订单条件搜索（订单号 / 手机号 / 状态 / 下单时间区间）
     *
     * @param ordersPageQueryDTO
     * @return 每条带「菜品明细摘要」orderDishes
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 管理端各状态订单数量统计
     *
     * @return 待接单 / 待派送 / 派送中
     */
    OrderStatisticsVO statistics();

    /**
     * 管理端查询订单详情（不校验 userId 归属）
     *
     * @param id
     * @return
     */
    OrderVO detailsForAdmin(Long id);

    /**
     * 接单：待接单 -> 已接单
     *
     * @param ordersConfirmDTO
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 拒单：待接单 -> 已取消，已支付的订单要退款
     *
     * @param ordersRejectionDTO
     */
    void rejection(OrdersRejectionDTO ordersRejectionDTO);

    /**
     * 管理端取消订单：已完成 / 已取消之外的状态都可以取消，已支付的退款
     *
     * @param ordersCancelDTO
     */
    void cancel(OrdersCancelDTO ordersCancelDTO);

    /**
     * 派送：已接单 -> 派送中
     *
     * @param id
     */
    void delivery(Long id);

    /**
     * 完成：派送中 -> 已完成
     *
     * @param id
     */
    void complete(Long id);
}
