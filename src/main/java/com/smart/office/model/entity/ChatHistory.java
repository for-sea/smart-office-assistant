package com.smart.office.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 对话历史表
 */
@Data
@TableName("chat_history")
public class ChatHistory {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 会话ID
     */
    @TableField("session_id")
    private String sessionId;
    
    /**
     * 用户标识
     */
    @TableField("user_id")
    private String userId;
    
    /**
     * 用户问题
     */
    private String question;
    
    /**
     * 问题向量（可选，用于分析）
     */
    @TableField("question_embedding")
    private String questionEmbedding;
    
    /**
     * 模型回答
     */
    private String answer;
    
    /**
     * 答案来源（引用的文档块信息，JSON格式）
     */
    private String sources;
    
    /**
     * Prompt令牌数
     */
    @TableField("prompt_tokens")
    private Integer promptTokens;
    
    /**
     * 回答令牌数
     */
    @TableField("completion_tokens")
    private Integer completionTokens;
    
    /**
     * 总令牌数
     */
    @TableField("total_tokens")
    private Integer totalTokens;
    
    /**
     * 响应耗时（毫秒）
     */
    @TableField("latency_ms")
    private Integer latencyMs;
    
    /**
     * 用户反馈：1-点赞，-1-点踩，0-无反馈
     */
    private Integer feedback;
    
    /**
     * 反馈意见
     */
    @TableField("feedback_comment")
    private String feedbackComment;
    
    /**
     * 提问时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}