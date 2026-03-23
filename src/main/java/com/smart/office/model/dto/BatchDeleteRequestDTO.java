package com.smart.office.model.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量删除请求
 */
@Data
public class BatchDeleteRequestDTO {
    @NotEmpty(message = "文档ID列表不能为空")
    private List<@NotNull Long> documentIds;

    @NotBlank(message = "上传者不能为空")
    private String uploader;
}
