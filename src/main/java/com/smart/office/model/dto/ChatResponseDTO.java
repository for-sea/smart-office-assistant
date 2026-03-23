package com.smart.office.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 聊天响应 DTO
 */
@Data
@Builder
public class ChatResponseDTO {

    /**
     * 回答内容
     */
    private String answer;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 引用来源
     */
    private List<SourceInfo> sources;

    /**
     * 处理耗时（毫秒）
     */
    private Long latencyMs;

    /**
     * 使用的Token数
     */
    private Integer totalTokens;

    @Data
    @Builder
    public static class SourceInfo {
        private Long docId;
        private String docName;
        private String chunkId;
        private String content;
        private Double score;
    }
}