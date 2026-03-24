package com.smart.office.service;

import com.smart.office.model.dto.ChatRequestDTO;
import com.smart.office.model.dto.ChatResponseDTO;
import com.smart.office.model.entity.ChatHistory;
import com.smart.office.model.vo.StreamResponseVO;
import com.smart.office.security.LoginUserDetails;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * 聊天业务抽象定义，约束同步、流式问答以及历史管理能力。
 */
public interface ChatService {

    /**
     * 处理同步对话请求。
     */
    ChatResponseDTO chatSync(ChatRequestDTO request, LoginUserDetails currentUser);

    /**
     * 处理流式对话请求。
     */
    Flux<StreamResponseVO> chatStream(ChatRequestDTO request, LoginUserDetails currentUser);

    /**
     * 查询会话历史。
     */
    List<ChatHistory> getChatHistory(String sessionId);

    /**
     * 查询用户的会话列表。
     */
    List<String> getUserSessions(String userId);

    /**
     * 更新用户对回答的反馈。
     */
    boolean updateFeedback(String userId, Long historyId, Integer feedback, String comment);

    /**
     * 清理用户的某个会话历史。
     */
    boolean clearChatHistory(String sessionId, String userId);
}
