package com.fastdine.task;

import com.fastdine.entity.Orders;
import com.fastdine.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单状态定时处理：
 * 1. 待付款超时（默认 15 分钟）自动取消
 * 2. 派送中超时（默认 60 分钟）自动完成
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 待付款超时时间（分钟）
     */
    @Value("${fastdine.order.timeout-pay-minutes:15}")
    private long timeoutPayMinutes;

    /**
     * 派送中超时时间（分钟）
     */
    @Value("${fastdine.order.timeout-delivery-minutes:60}")
    private long timeoutDeliveryMinutes;

    /**
     * 每 30 秒扫一次（可在 application.yml 调整间隔，自测时用更短的间隔验证）
     */
    @Scheduled(fixedDelayString = "${fastdine.order.task-interval-ms:30000}")
    @Transactional
    public void processTimeoutOrder() {
        LocalDateTime now = LocalDateTime.now();
        processUnpaidTimeoutOrder(now);
        processDeliveryTimeoutOrder(now);
    }

    /**
     * 待付款超时：下单后一直没付钱，自动取消（未支付，不需要退款）
     */
    private void processUnpaidTimeoutOrder(LocalDateTime now) {
        LocalDateTime deadline = now.minusMinutes(timeoutPayMinutes);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, deadline);
        if (ordersList == null || ordersList.isEmpty()) {
            return;
        }
        for (Orders orders : ordersList) {
            Orders update = Orders.builder()
                    .id(orders.getId())
                    .status(Orders.CANCELLED)
                    .cancelReason("订单超时未支付，自动取消")
                    .cancelTime(now)
                    .build();
            orderMapper.update(update);
            log.info("定时任务：自动取消超时未支付订单 {}", orders.getNumber());
        }
    }

    /**
     * 派送中超时：一直没点完成，自动置为已完成
     */
    private void processDeliveryTimeoutOrder(LocalDateTime now) {
        LocalDateTime deadline = now.minusMinutes(timeoutDeliveryMinutes);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, deadline);
        if (ordersList == null || ordersList.isEmpty()) {
            return;
        }
        for (Orders orders : ordersList) {
            Orders update = Orders.builder()
                    .id(orders.getId())
                    .status(Orders.COMPLETED)
                    .deliveryTime(now)
                    .build();
            orderMapper.update(update);
            log.info("定时任务：派送中超时自动完成订单 {}", orders.getNumber());
        }
    }
}
