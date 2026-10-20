package com.fastdine.utils;

/**
 * 分页参数兜底。
 * <p>
 * 前端传参不可信：page / pageSize 可能为 null、0、负数，或被手工改成一个极大值
 * （例如 pageSize=999999 会一次性把整表数据捞进内存）。这里统一做归一化，
 * 各 Service 的 pageQuery 在调用 PageHelper.startPage 前过一遍即可。
 */
public class PageQueryUtil {

    /** 未传 pageSize 时的默认值 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 单页最大条数，超出的按上限截断 */
    public static final int MAX_PAGE_SIZE = 100;

    private PageQueryUtil() {
    }

    /**
     * 页码归一化：为空或小于 1 时按第 1 页处理
     */
    public static int page(Integer page) {
        return (page == null || page < 1) ? 1 : page;
    }

    /**
     * 每页条数归一化：为空或小于 1 时取默认值，超过上限时截断到上限
     */
    public static int pageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
