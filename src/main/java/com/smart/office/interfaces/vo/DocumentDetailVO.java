package com.smart.office.interfaces.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档详情VO
 */
@Data
@Builder
public class DocumentDetailVO {
    private Long id;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private String uploader;
    private Integer status;
    private String statusDesc;
    private Integer chunkCount;
    private Integer processTime;
    private String errorMessage;
    private String department;
    private String description;
    private LocalDateTime uploadTime;
    private LocalDateTime updateTime;
}
