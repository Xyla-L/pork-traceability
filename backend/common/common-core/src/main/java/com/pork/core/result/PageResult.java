package com.pork.core.result;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 统一分页响应体
 * 配合 Result<T> 使用，例如：Result<PageResult<UserVO>>
 */
@Data
@Builder
public class PageResult<T> {

    /**
     * 当前页码
     */
    private long current;

    /**
     * 每页条数
     */
    private long size;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private long pages;

    /**
     * 数据列表
     */
    private List<T> records;

    // --- 静态工厂方法 ---

    /**
     * 手动构建分页结果
     */
    public static <T> PageResult<T> of(long current, long size, long total, List<T> records) {
        long pages = (total + size - 1) / size; // 计算总页数
        return PageResult.<T>builder()
                .current(current)
                .size(size)
                .total(total)
                .pages(pages)
                .records(records)
                .build();
    }

    /**
     * 【核心方法】直接从 MyBatis-Plus 的 Page 对象转换
     * 这样 Controller 层就不需要感知 MyBatis-Plus 的类了
     */
    public static <T> PageResult<T> of(Page<T> page) {
        return PageResult.<T>builder()
                .current(page.getCurrent())
                .size(page.getSize())
                .total(page.getTotal())
                .pages(page.getPages())
                .records(page.getRecords())
                .build();
    }
}