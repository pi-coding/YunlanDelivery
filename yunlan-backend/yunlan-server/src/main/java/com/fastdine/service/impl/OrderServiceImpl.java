package com.fastdine.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fastdine.constant.MessageConstant;
import com.fastdine.context.BaseContext;
import com.fastdine.dto.OrdersCancelDTO;
import com.fastdine.dto.OrdersConfirmDTO;
import com.fastdine.dto.OrdersPageQueryDTO;
import com.fastdine.dto.OrdersPaymentDTO;
import com.fastdine.dto.OrdersRejectionDTO;
import com.fastdine.dto.OrdersSubmitDTO;
import com.fastdine.entity.AddressBook;
import com.fastdine.entity.OrderDetail;
import com.fastdine.entity.Orders;
import com.fastdine.entity.ShoppingCart;
import com.fastdine.entity.User;
import com.fastdine.exception.AddressBookBusinessException;
import com.fastdine.exception.OrderBusinessException;
import com.fastdine.exception.ShoppingCartBusinessException;
import com.fastdine.mapper.AddressBookMapper;
import com.fastdine.mapper.OrderDetailMapper;
import com.fastdine.mapper.OrderMapper;
import com.fastdine.mapper.ShoppingCartMapper;
import com.fastdine.mapper.UserMapper;
import com.fastdine.result.PageResult;
import com.fastdine.service.OrderService;
import com.fastdine.utils.PageQueryUtil;
import com.fastdine.utils.WeChatPayUtil;
import com.fastdine.vo.OrderPaymentVO;
import com.fastdine.vo.OrderStatisticsVO;
import com.fastdine.vo.OrderSubmitVO;
import com.fastdine.vo.OrderVO;
import com.fastdine.websocket.WebSocketServer;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;

    /**
     * 是否启用真实微信支付。
     * 默认 false：本机没有商户号/证书，走本地模拟支付（直接把订单标记为已支付），
     * 填好 fastdine.wechat.mchid 等配置并置为 true 后自动切回真实微信支付，代码无需改动。
     */
    @Value("${fastdine.wechat.pay-enabled:false}")
    private boolean payEnabled;

    /**
     * 用户下单
     */
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        Long userId = BaseContext.getCurrentId();

        //1、地址簿校验：不存在 / 不是自己的地址都不许下单
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        if (!userId.equals(addressBook.getUserId())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_BELONG_TO_USER);
        }

        //2、购物车不能为空
        ShoppingCart query = new ShoppingCart();
        query.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(query);
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //3、金额由服务端算：单价 × 数量 求和 + 打包费，不使用前端传来的 amount
        BigDecimal goodsAmount = BigDecimal.ZERO;
        for (ShoppingCart shoppingCart : shoppingCartList) {
            BigDecimal price = shoppingCart.getAmount() == null ? BigDecimal.ZERO : shoppingCart.getAmount();
            int number = shoppingCart.getNumber() == null ? 0 : shoppingCart.getNumber();
            goodsAmount = goodsAmount.add(price.multiply(new BigDecimal(number)));
        }
        int packAmount = ordersSubmitDTO.getPackAmount() == null ? 0 : ordersSubmitDTO.getPackAmount();
        BigDecimal amount = goodsAmount.add(new BigDecimal(packAmount)).setScale(2, RoundingMode.HALF_UP);
        if (ordersSubmitDTO.getAmount() != null && ordersSubmitDTO.getAmount().compareTo(amount) != 0) {
            log.warn("前端金额 {} 与服务端计算金额 {} 不一致，以服务端为准", ordersSubmitDTO.getAmount(), amount);
        }

        //4、写订单主表。实体里的 packAmount / tablewareNumber 是 int 基本类型，
        //   不能用 BeanUtils 整体拷贝（前端不传会变 null 触发异常），逐字段赋值
        Orders orders = new Orders();
        orders.setNumber(createOrderNumber());
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setUserId(userId);
        orders.setAddressBookId(addressBook.getId());
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayMethod(ordersSubmitDTO.getPayMethod() == 0 ? 1 : ordersSubmitDTO.getPayMethod());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setAmount(amount);
        orders.setRemark(ordersSubmitDTO.getRemark());
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserName(addressBook.getConsignee());
        orders.setAddress(buildFullAddress(addressBook));
        orders.setEstimatedDeliveryTime(ordersSubmitDTO.getEstimatedDeliveryTime());
        orders.setDeliveryStatus(ordersSubmitDTO.getDeliveryStatus() == null ? 1 : ordersSubmitDTO.getDeliveryStatus());
        orders.setPackAmount(packAmount);
        orders.setTablewareNumber(ordersSubmitDTO.getTablewareNumber() == null ? 0 : ordersSubmitDTO.getTablewareNumber());
        orders.setTablewareStatus(ordersSubmitDTO.getTablewareStatus() == null ? 1 : ordersSubmitDTO.getTablewareStatus());
        orderMapper.insert(orders);

        //5、写订单明细（购物车里的每一行都变成一条明细）
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart shoppingCart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setName(shoppingCart.getName());
            orderDetail.setImage(shoppingCart.getImage());
            orderDetail.setOrderId(orders.getId());
            orderDetail.setDishId(shoppingCart.getDishId());
            orderDetail.setSetmealId(shoppingCart.getSetmealId());
            orderDetail.setDishFlavor(shoppingCart.getDishFlavor());
            orderDetail.setNumber(shoppingCart.getNumber());
            orderDetail.setAmount(shoppingCart.getAmount());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        //6、清空购物车
        shoppingCartMapper.deleteByUserId(userId);

        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
    }

    /**
     * 订单支付：真实微信支付 / 本地模拟支付由 fastdine.wechat.pay-enabled 决定
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        Long userId = BaseContext.getCurrentId();
        Orders ordersDB = orderMapper.getByNumberAndUserId(ordersPaymentDTO.getOrderNumber(), userId);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_ALREADY_PAID);
        }
        if (!Orders.PENDING_PAYMENT.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //本地模拟支付：没有商户号也能把流程走通，直接置为已支付并返回一份"假"的调起参数
        if (!payEnabled) {
            log.warn("未启用微信支付（fastdine.wechat.pay-enabled=false），订单 {} 走本地模拟支付",
                    ordersPaymentDTO.getOrderNumber());
            paySuccess(ordersPaymentDTO.getOrderNumber());
            return OrderPaymentVO.builder()
                    .nonceStr(UUID.randomUUID().toString().replace("-", ""))
                    .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                    .signType("RSA")
                    .packageStr("prepay_id=mock_" + ordersPaymentDTO.getOrderNumber())
                    .paySign("mock-pay-sign")
                    .build();
        }

        //真实微信支付：调 jsapi 下单拿 prepay_id，再由小程序 wx.requestPayment 调起
        User user = userMapper.getById(userId);
        if (user == null || user.getOpenid() == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }
        JSONObject jsonObject = weChatPayUtil.pay(ordersPaymentDTO.getOrderNumber(), ordersDB.getAmount(),
                "云岚外卖订单", user.getOpenid());
        if (jsonObject == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_PAY_FAILED);
        }
        if ("ORDERPAID".equals(jsonObject.getString("code"))) {
            throw new OrderBusinessException(MessageConstant.ORDER_ALREADY_PAID);
        }
        if (jsonObject.getString("prepay_id") == null && jsonObject.getString("package") == null) {
            log.error("微信支付下单失败：{}", jsonObject);
            throw new OrderBusinessException(MessageConstant.ORDER_PAY_FAILED);
        }
        OrderPaymentVO orderPaymentVO = jsonObject.toJavaObject(OrderPaymentVO.class);
        orderPaymentVO.setPackageStr(jsonObject.getString("package"));
        return orderPaymentVO;
    }

    /**
     * 支付成功：待付款 -> 待接单，并给管理端推一条来单提醒
     */
    @Transactional
    public void paySuccess(String outTradeNo) {
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //幂等：微信支付回调会重复推送，已支付过的订单不能再改状态、更不能重复推来单提醒
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            log.info("订单 {} 已是已支付状态，忽略重复的支付回调", outTradeNo);
            return;
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);

        //来单提醒：type=1 新订单，type=2 客户催单（管理端页面按 type 区分提示音/弹窗）
        pushToAdmin(1, ordersDB);
    }

    /**
     * 客户催单
     */
    public void reminder(Long id) {
        Orders ordersDB = getOwnedOrder(id);
        if (Orders.COMPLETED.equals(ordersDB.getStatus()) || Orders.CANCELLED.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        pushToAdmin(2, ordersDB);
    }

    /**
     * 用户端历史订单分页
     */
    public PageResult pageQuery4User(int page, int pageSize, Integer status) {
        PageHelper.startPage(PageQueryUtil.page(page), PageQueryUtil.pageSize(pageSize));

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        if (status != null && status > 0) {
            ordersPageQueryDTO.setStatus(status);
        }

        Page<Orders> ordersPage = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = new ArrayList<>();
        if (ordersPage != null && !ordersPage.isEmpty()) {
            for (Orders orders : ordersPage) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(orders.getId()));
                list.add(orderVO);
            }
        }
        return new PageResult(ordersPage == null ? 0 : ordersPage.getTotal(), list);
    }

    /**
     * 查询订单详情（含明细）
     */
    public OrderVO details(Long id) {
        Orders orders = getOwnedOrder(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(id));
        return orderVO;
    }

    /**
     * 用户取消订单：只有待付款、待接单可以取消
     */
    @Transactional
    public void userCancelById(Long id) {
        Orders ordersDB = getOwnedOrder(id);

        Integer status = ordersDB.getStatus();
        if (!Orders.PENDING_PAYMENT.equals(status) && !Orders.TO_BE_CONFIRMED.equals(status)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.CANCELLED)
                .cancelReason("用户取消")
                .cancelTime(LocalDateTime.now())
                .build();

        //已付款的订单取消要退款：真实支付走微信退款接口，本地模拟支付只改状态
        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            refundIfPaid(ordersDB);
            orders.setPayStatus(Orders.REFUND);
        }

        orderMapper.update(orders);
    }

    /**
     * 再来一单：把该订单的明细写回购物车
     */
    @Transactional
    public void repetition(Long id) {
        getOwnedOrder(id);

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        if (orderDetailList == null || orderDetailList.isEmpty()) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Long userId = BaseContext.getCurrentId();
        LocalDateTime now = LocalDateTime.now();
        List<ShoppingCart> shoppingCartList = new ArrayList<>();
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            //id / orderId 不能带过去，否则写购物车会撞主键
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id", "orderId");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(now);
            shoppingCartList.add(shoppingCart);
        }
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    // ------------------------------------------------------------ 管理端

    /**
     * 管理端订单条件搜索
     */
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(PageQueryUtil.page(ordersPageQueryDTO.getPage()),
                PageQueryUtil.pageSize(ordersPageQueryDTO.getPageSize()));

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        if (page != null && !page.isEmpty()) {
            for (Orders orders : page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                orderVO.setOrderDishes(buildOrderDishes(orderDetailList));
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(page == null ? 0 : page.getTotal(), orderVOList);
    }

    /**
     * 各状态订单数量统计
     */
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO vo = new OrderStatisticsVO();
        vo.setToBeConfirmed(orderMapper.countByStatus(Orders.TO_BE_CONFIRMED));
        vo.setConfirmed(orderMapper.countByStatus(Orders.CONFIRMED));
        vo.setDeliveryInProgress(orderMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS));
        return vo;
    }

    /**
     * 管理端查询订单详情：管理员不受 userId 归属限制
     */
    public OrderVO detailsForAdmin(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(id));
        return orderVO;
    }

    /**
     * 接单：只有待接单的订单可以接
     */
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders ordersDB = getOrderForAdmin(ordersConfirmDTO.getId());
        if (!Orders.TO_BE_CONFIRMED.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 拒单：只有待接单可以拒，已支付的订单要退款
     */
    @Transactional
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        Orders ordersDB = getOrderForAdmin(ordersRejectionDTO.getId());
        if (!Orders.TO_BE_CONFIRMED.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason() == null
                        ? "商家拒单" : ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .build();

        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            refundIfPaid(ordersDB);
            orders.setPayStatus(Orders.REFUND);
        }
        orderMapper.update(orders);
    }

    /**
     * 管理端取消订单：已完成 / 已取消的不允许再取消
     */
    @Transactional
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        Orders ordersDB = getOrderForAdmin(ordersCancelDTO.getId());
        if (Orders.COMPLETED.equals(ordersDB.getStatus()) || Orders.CANCELLED.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason() == null
                        ? "商家取消" : ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .build();

        if (Orders.PAID.equals(ordersDB.getPayStatus())) {
            refundIfPaid(ordersDB);
            orders.setPayStatus(Orders.REFUND);
        }
        orderMapper.update(orders);
    }

    /**
     * 派送：已接单 -> 派送中
     */
    public void delivery(Long id) {
        Orders ordersDB = getOrderForAdmin(id);
        if (!Orders.CONFIRMED.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 完成：派送中 -> 已完成，记录送达时间
     */
    public void complete(Long id) {
        Orders ordersDB = getOrderForAdmin(id);
        if (!Orders.DELIVERY_IN_PROGRESS.equals(ordersDB.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 查订单并校验归属，防止 A 用户操作 B 用户的订单
     */
    private Orders getOwnedOrder(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!BaseContext.getCurrentId().equals(orders.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_BELONG_TO_USER);
        }
        return orders;
    }

    /**
     * 管理端按 id 取订单，只判存在性（不校验 userId，管理员要能看所有订单）
     */
    private Orders getOrderForAdmin(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return orders;
    }

    /**
     * 已支付订单退款：真实支付调微信退款接口，模拟支付只改状态
     */
    private void refundIfPaid(Orders ordersDB) {
        if (!payEnabled) {
            log.warn("未启用微信支付，订单 {} 直接标记退款状态", ordersDB.getNumber());
            return;
        }
        try {
            String refundResult = weChatPayUtil.refund(ordersDB.getNumber(), ordersDB.getNumber() + "_refund",
                    ordersDB.getAmount(), ordersDB.getAmount());
            log.info("微信退款返回：{}", refundResult);
        } catch (Exception e) {
            log.error("微信退款失败，订单号：{}", ordersDB.getNumber(), e);
            throw new OrderBusinessException(MessageConstant.ORDER_PAY_FAILED);
        }
    }

    /**
     * 推送给管理端 WebSocket：type=1 来单提醒，type=2 客户催单
     */
    private void pushToAdmin(int type, Orders orders) {
        try {
            Map<String, Object> map = new HashMap<>(4);
            map.put("type", type);
            map.put("orderId", orders.getId());
            map.put("content", "订单号：" + orders.getNumber());
            WebSocketServer.sendToAllClient(JSON.toJSONString(map));
        } catch (Exception e) {
            //推送失败不影响主流程：订单已经落库，管理端还能靠列表刷新看到
            log.error("WebSocket 推送失败，订单号：{}", orders.getNumber(), e);
        }
    }

    /**
     * 菜品摘要：宫保鸡丁*2;米饭*1;（管理端订单列表展示用）
     */
    private String buildOrderDishes(List<OrderDetail> orderDetailList) {        if (orderDetailList == null || orderDetailList.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (OrderDetail orderDetail : orderDetailList) {
            sb.append(orderDetail.getName()).append("*").append(orderDetail.getNumber()).append(";");
        }
        return sb.toString();
    }

    /**
     * 订单号：时间戳 + 4 位随机数，避免同一秒内下单重复
     */
    private String createOrderNumber() {
        return LocalDateTime.now().format(ORDER_NUMBER_TIME)
                + String.format("%04d", (int) (Math.random() * 10000));
    }

    /**
     * 拼完整地址：省市区 + 详细地址
     */
    private String buildFullAddress(AddressBook addressBook) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, addressBook.getProvinceName());
        appendIfPresent(sb, addressBook.getCityName());
        appendIfPresent(sb, addressBook.getDistrictName());
        appendIfPresent(sb, addressBook.getDetail());
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String part) {
        if (part != null && !part.trim().isEmpty()) {
            sb.append(part);
        }
    }
}
