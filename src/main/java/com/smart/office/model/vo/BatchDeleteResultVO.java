package com.smart.office.model.vo;


import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 批量删除结果
 */
@Data
@Builder
public class BatchDeleteResultVO {
    private int successCount;
    private int failCount;
    private List<Long> failedIds;
    private List<String> errors;
}
