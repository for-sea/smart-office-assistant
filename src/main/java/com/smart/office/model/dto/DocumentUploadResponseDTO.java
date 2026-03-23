package com.smart.office.model.dto;

import lombok.Data;

/**
 * 文档上传响应DTO
 */
@Data
public class DocumentUploadResponseDTO {

    private Long taskId;           // 任务ID（文档ID）
    private String fileName;       // 文件名
    private Long fileSize;         // 文件大小
    private String fileType;       // 文件类型
    private String status;         // 状态
    private String message;        // 提示信息
    private String uploadTime;     // 上传时间
}
