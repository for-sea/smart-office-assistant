package com.smart.office.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传请求DTO
 */
@Data
public class DocumentUploadDTO {

    @NotNull(message = "文件不能为空")
    private MultipartFile file;

    @NotBlank(message = "上传者不能为空")
    private String uploader;

    private String department;

    private String description;
}
