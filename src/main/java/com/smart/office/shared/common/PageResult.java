package com.smart.office.shared.common;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 分页结果
 */
@Data
@Builder
public class PageResult<T> {
    private List<T> records;
    private Long total;
    private Integer page;
    private Integer size;
    private Long pages;
}