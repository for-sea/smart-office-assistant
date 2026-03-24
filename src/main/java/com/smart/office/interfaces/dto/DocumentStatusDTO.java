package com.smart.office.interfaces.dto;

import lombok.Data;

/**
 * 文档状态查询DTO
 */
@Data
public class DocumentStatusDTO {

    private Long documentId;        // 文档ID
    private String fileName;        // 文件名
    private Integer status;         // 状态码
    private String statusDesc;      // 状态描述
    private Integer chunkCount;     // 已处理块数
    private Integer totalChunks;    // 总块数（预估）
    private Integer progress;       // 处理进度（0-100）
    private String errorMessage;    // 错误信息
    private String uploadTime;      // 上传时间
    private String completeTime;    // 完成时间
}
