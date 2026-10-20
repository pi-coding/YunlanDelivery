package com.fastdine.service;

import com.fastdine.vo.OrderReportVO;
import com.fastdine.vo.SalesTop10ReportVO;
import com.fastdine.vo.TurnoverReportVO;
import com.fastdine.vo.UserReportVO;

import java.time.LocalDate;

public interface ReportService {

    /**
     * 营业额统计：每天已完成订单的金额合计
     *
     * @param begin
     * @param end
     * @return dateList / turnoverList 逗号分隔
     */
    TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end);

    /**
     * 用户统计：每天累计用户数 + 新增用户数
     *
     * @param begin
     * @param end
     * @return
     */
    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);

    /**
     * 订单统计：每天订单总数 / 有效订单数，区间汇总与完成率
     *
     * @param begin
     * @param end
     * @return
     */
    OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end);

    /**
     * 销量 TOP10
     *
     * @param begin
     * @param end
     * @return nameList / numberList 逗号分隔
     */
    SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end);

    /**
     * 导出运营数据报表（Excel），直接写响应流
     *
     * @param response
     * @param begin
     * @param end
     */
    void exportBusinessData(javax.servlet.http.HttpServletResponse response, LocalDate begin, LocalDate end);
}
