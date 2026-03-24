package com.smart.office.interfaces.dto;

import lombok.Data;
import java.util.List;

/**
 * 聊天请求 DTO
 */
@Data
public class ChatRequestDTO {

    /**
     * 用户问题
     */
    private String question;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 历史消息
     */
    private List<Message> history;

    /**
     * 是否流式输出
     */
    private boolean stream = false;

    @Data
    public static class Message {
        private String role;      // user, assistant, system
        private String content;
    }
}