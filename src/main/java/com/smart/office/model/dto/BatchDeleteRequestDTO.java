package com.smart.office.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 批量删除请求 DTO。
 */
@Data
public class BatchDeleteRequestDTO {

    @NotEmpty(message = "文档ID列表不能为空")
    private List<@NotNull Long> documentIds;
}
