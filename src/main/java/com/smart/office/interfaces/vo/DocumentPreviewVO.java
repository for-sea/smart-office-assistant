package com.smart.office.interfaces.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档预览VO
 */
@Data
@Builder
public class DocumentPreviewVO {
    private Long documentId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer status;
    private String statusDesc;
    private Integer chunkCount;
    private List<String> previewContent;
    private LocalDateTime uploadTime;
}
