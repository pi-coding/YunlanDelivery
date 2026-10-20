package com.fastdine.service.impl;

import com.fastdine.dto.GoodsSalesDTO;
import com.fastdine.entity.Orders;
import com.fastdine.mapper.OrderMapper;
import com.fastdine.mapper.UserMapper;
import com.fastdine.service.ReportService;
import com.fastdine.service.WorkspaceService;
import com.fastdine.vo.BusinessDataVO;
import com.fastdine.vo.OrderReportVO;
import com.fastdine.vo.SalesTop10ReportVO;
import com.fastdine.vo.TurnoverReportVO;
import com.fastdine.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 营业额统计：逐天查已完成订单的金额合计，拼成逗号分隔字符串给 ECharts
     */
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = buildDateList(begin, end);

        List<String> dateStrList = new ArrayList<>();
        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            dateStrList.add(date.toString());

            Map<String, Object> map = new HashMap<>(4);
            map.put("begin", date.atStartOfDay());
            map.put("end", date.atTime(LocalTime.MAX));
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            turnoverList.add(turnover == null ? 0.0 : round2(turnover));
        }

        TurnoverReportVO vo = new TurnoverReportVO();
        vo.setDateList(String.join(",", dateStrList));
        vo.setTurnoverList(join(turnoverList));
        return vo;
    }

    /**
     * 用户统计：累计用户数（截止当天 23:59:59）+ 当天新增
     */
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = buildDateList(begin, end);

        List<String> dateStrList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();
        List<Integer> newUserList = new ArrayList<>();
        for (LocalDate date : dateList) {
            dateStrList.add(date.toString());

            //累计用户：create_time <= 当天结束
            Map<String, Object> totalMap = new HashMap<>(2);
            totalMap.put("end", date.atTime(LocalTime.MAX));
            totalUserList.add(userMapper.countByMap(totalMap));

            //新增用户：当天 00:00:00 ~ 23:59:59
            Map<String, Object> newMap = new HashMap<>(2);
            newMap.put("begin", date.atStartOfDay());
            newMap.put("end", date.atTime(LocalTime.MAX));
            newUserList.add(userMapper.countByMap(newMap));
        }

        UserReportVO vo = new UserReportVO();
        vo.setDateList(String.join(",", dateStrList));
        vo.setTotalUserList(join(totalUserList));
        vo.setNewUserList(join(newUserList));
        return vo;
    }

    /**
     * 订单统计：每天订单总数与有效订单数，以及区间汇总和完成率
     */
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = buildDateList(begin, end);

        List<String> dateStrList = new ArrayList<>();
        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();
        for (LocalDate date : dateList) {
            dateStrList.add(date.toString());

            LocalDateTime beginTime = date.atStartOfDay();
            LocalDateTime endTime = date.atTime(LocalTime.MAX);

            Map<String, Object> totalMap = new HashMap<>(2);
            totalMap.put("begin", beginTime);
            totalMap.put("end", endTime);
            orderCountList.add(orderMapper.countByMap(totalMap));

            Map<String, Object> validMap = new HashMap<>(4);
            validMap.put("begin", beginTime);
            validMap.put("end", endTime);
            validMap.put("status", Orders.COMPLETED);
            validOrderCountList.add(orderMapper.countByMap(validMap));
        }

        int totalOrderCount = orderCountList.stream().mapToInt(Integer::intValue).sum();
        int validOrderCount = validOrderCountList.stream().mapToInt(Integer::intValue).sum();

        OrderReportVO vo = new OrderReportVO();
        vo.setDateList(String.join(",", dateStrList));
        vo.setOrderCountList(join(orderCountList));
        vo.setValidOrderCountList(join(validOrderCountList));
        vo.setTotalOrderCount(totalOrderCount);
        vo.setValidOrderCount(validOrderCount);
        vo.setOrderCompletionRate(totalOrderCount == 0 ? 0.0
                : round2(validOrderCount * 1.0 / totalOrderCount));
        return vo;
    }

    /**
     * 销量 TOP10
     */
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        List<GoodsSalesDTO> salesList = orderMapper.getSalesTop10(
                begin.atStartOfDay(), end.atTime(LocalTime.MAX));

        List<String> nameList = new ArrayList<>();
        List<Integer> numberList = new ArrayList<>();
        if (salesList != null) {
            for (GoodsSalesDTO dto : salesList) {
                nameList.add(dto.getName());
                numberList.add(dto.getNumber());
            }
        }

        SalesTop10ReportVO vo = new SalesTop10ReportVO();
        vo.setNameList(String.join(",", nameList));
        vo.setNumberList(join(numberList));
        return vo;
    }

    /**
     * 导出运营数据报表：标题 + 区间汇总 + 每日明细，用 POI 现场生成（不依赖模板文件）
     */
    public void exportBusinessData(javax.servlet.http.HttpServletResponse response, LocalDate begin, LocalDate end) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("运营数据报表");

            //列宽：日期宽一点，其余 14 字符
            sheet.setColumnWidth(0, 18 * 256);
            for (int i = 1; i <= 5; i++) {
                sheet.setColumnWidth(i, 14 * 256);
            }

            //标题样式
            XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);

            //表头样式
            XSSFFont headFont = workbook.createFont();
            headFont.setBold(true);
            CellStyle headStyle = workbook.createCellStyle();
            headStyle.setFont(headFont);
            headStyle.setAlignment(HorizontalAlignment.CENTER);

            int rowIndex = 0;

            //1、标题
            Row titleRow = sheet.createRow(rowIndex++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("云岚外卖运营数据报表（" + begin + " 至 " + end + "）");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
            rowIndex++;

            //2、区间汇总
            BusinessDataVO summary = workspaceService.getBusinessData(begin, end);
            Row summaryHeadRow = sheet.createRow(rowIndex++);
            String[] summaryHeads = {"营业额", "有效订单", "订单完成率", "平均客单价", "新增用户"};
            for (int i = 0; i < summaryHeads.length; i++) {
                Cell cell = summaryHeadRow.createCell(i);
                cell.setCellValue(summaryHeads[i]);
                cell.setCellStyle(headStyle);
            }
            Row summaryRow = sheet.createRow(rowIndex++);
            summaryRow.createCell(0).setCellValue(nullToZero(summary.getTurnover()));
            summaryRow.createCell(1).setCellValue(nullToZero(summary.getValidOrderCount()));
            summaryRow.createCell(2).setCellValue(nullToZero(summary.getOrderCompletionRate()));
            summaryRow.createCell(3).setCellValue(nullToZero(summary.getUnitPrice()));
            summaryRow.createCell(4).setCellValue(nullToZero(summary.getNewUsers()));
            rowIndex++;

            //3、每日明细
            Row headRow = sheet.createRow(rowIndex++);
            String[] heads = {"日期", "营业额", "有效订单", "订单完成率", "平均客单价", "新增用户"};
            for (int i = 0; i < heads.length; i++) {
                Cell cell = headRow.createCell(i);
                cell.setCellValue(heads[i]);
                cell.setCellStyle(headStyle);
            }

            LocalDate current = begin;
            while (!current.isAfter(end)) {
                BusinessDataVO data = workspaceService.getBusinessData(current, current);
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(current.toString());
                row.createCell(1).setCellValue(nullToZero(data.getTurnover()));
                row.createCell(2).setCellValue(nullToZero(data.getValidOrderCount()));
                row.createCell(3).setCellValue(nullToZero(data.getOrderCompletionRate()));
                row.createCell(4).setCellValue(nullToZero(data.getUnitPrice()));
                row.createCell(5).setCellValue(nullToZero(data.getNewUsers()));
                current = current.plusDays(1);
            }

            ServletOutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
        } catch (IOException e) {
            log.error("导出运营数据报表失败", e);
            throw new RuntimeException("导出运营数据报表失败", e);
        }
    }

    private double nullToZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 生成 begin ~ end 的连续日期（含首尾）
     */
    private List<LocalDate> buildDateList(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        LocalDate current = begin;
        while (!current.isAfter(end)) {
            dateList.add(current);
            current = current.plusDays(1);
        }
        return dateList;
    }

    private String join(List<?> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
