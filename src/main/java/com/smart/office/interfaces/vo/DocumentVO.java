package com.smart.office.interfaces.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档VO
 */
@Data
@Builder
public class DocumentVO {
    private Long id;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private Integer status;
    private String statusDesc;
    private Integer chunkCount;
    private LocalDateTime uploadTime;
    private String description;
}
