package com.fastdine.mapper;

import com.fastdine.dto.GoodsSalesDTO;
import com.fastdine.dto.OrdersPageQueryDTO;
import com.fastdine.entity.Orders;
import com.github.pagehelper.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    /**
     * 插入订单，并回填主键
     *
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 动态更新订单（只更新非空字段，状态机流转都走这里）
     *
     * @param orders
     */
    void update(Orders orders);

    /**
     * 根据订单号查询
     *
     * @param number
     * @return 查不到返回 null
     */
    @Select("select * from orders where number = #{number} limit 1")
    Orders getByNumber(String number);

    /**
     * 根据订单号 + 用户id 查询（C 端支付前校验归属）
     *
     * @param number
     * @param userId
     * @return
     */
    Orders getByNumberAndUserId(@Param("number") String number, @Param("userId") Long userId);

    /**
     * 根据主键查询
     *
     * @param id
     * @return 查不到返回 null
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 订单分页查询：C 端传 userId，管理端可传 number / phone / status / 时间区间
     *
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 按状态统计订单数量（管理端工作台：待接单 / 待派送 / 派送中）
     *
     * @param status
     * @return
     */
    @Select("select count(id) from orders where status = #{status}")
    Integer countByStatus(@Param("status") Integer status);

    /**
     * 按状态 + 下单时间早于某时刻查询（定时任务：超时未支付 / 派送中超时）
     *
     * @param status
     * @param time
     * @return
     */
    @Select("select * from orders where status = #{status} and order_time < #{time}")
    List<Orders> getByStatusAndOrderTimeLT(@Param("status") Integer status,
                                           @Param("time") java.time.LocalDateTime time);

    /**
     * 营业额统计：价格区间内已完成订单金额求和（可带 status）
     *
     * @param map begin / end / status，都可为空
     * @return 没有数据时返回 null，调用方要判空
     */
    Double sumByMap(Map<String, Object> map);

    /**
     * 按条件统计订单数
     *
     * @param map begin / end / status
     * @return
     */
    Integer countByMap(Map<String, Object> map);

    /**
     * 销量 TOP10（只算已完成订单）
     *
     * @param begin
     * @param end
     * @return
     */
    List<GoodsSalesDTO> getSalesTop10(@Param("begin") java.time.LocalDateTime begin,
                                      @Param("end") java.time.LocalDateTime end);
}
