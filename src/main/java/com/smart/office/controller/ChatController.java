package com.smart.office.controller;

import com.smart.office.common.response.R;
import com.smart.office.model.dto.ChatRequestDTO;
import com.smart.office.model.dto.ChatResponseDTO;
import com.smart.office.model.entity.ChatHistory;
import com.smart.office.model.vo.StreamResponseVO;
import com.smart.office.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 聊天控制
 * 处理用户问答请求，支持同步返回和流式响应
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 获取当前用户ID（从请求中获取）
     * 实际项目中可根据Token/Session解析用户信息
     */
    private String getCurrentUserId(HttpServletRequest request) {
        // TODO: 从Token或Session中获取用户ID
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            userId = "default_user";
        }
        return userId;
    }

    /**
     * 同步聊天接口
     *
     * @param request 聊天请求
     * @param request HTTP请求
     * @return 聊天响应
     */
    @PostMapping("/sync")
    public R<ChatResponseDTO> chatSync(@Validated @RequestBody ChatRequestDTO request,
                                       HttpServletRequest httpRequest) {
        String userId = getCurrentUserId(httpRequest);
        log.info("收到同步聊天请求: userId={}, sessionId={}, question={}, historySize={}",
                userId, request.getSessionId(), request.getQuestion(),
                request.getHistory() != null ? request.getHistory().size() : 0);

        ChatResponseDTO response = chatService.chatSync(request, userId);
        return R.success(response);
    }

    /**
     * 流式聊天接口（返回SSE格式）
     *
     * @param request 聊天请求
     * @param httpRequest HTTP请求
     * @return 流式响应Flux
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamResponseVO> chatStream(@RequestBody ChatRequestDTO request,
                                             HttpServletRequest httpRequest) {
        String userId = getCurrentUserId(httpRequest);
        log.info("收到流式聊天请求: userId={}, sessionId={}, question={}, historySize={}",
                userId, request.getSessionId(), request.getQuestion(),
                request.getHistory() != null ? request.getHistory().size() : 0);

        // 强制设置为流式模式
        request.setStream(true);
        return chatService.chatStream(request, userId);
    }

    /**
     * 通用聊天接口（根据请求参数决定同步还是流式）
     *
     * @param request 聊天请求
     * @param httpRequest HTTP请求
     * @return 同步返回R<ChatResponseDTO> 或 流式返回Flux<StreamResponseVO>
     */
    @PostMapping
    public Object chat(@Validated @RequestBody ChatRequestDTO request,
                       HttpServletRequest httpRequest) {
        String userId = getCurrentUserId(httpRequest);
        log.info("收到聊天请求: userId={}, sessionId={}, question={}, stream={}, historySize={}",
                userId, request.getSessionId(), request.getQuestion(), request.isStream(),
                request.getHistory() != null ? request.getHistory().size() : 0);

        // 根据stream标志决定返回类型
        if (request.isStream()) {
            return chatService.chatStream(request, userId);
        } else {
            ChatResponseDTO response = chatService.chatSync(request, userId);
            return R.success(response);
        }
    }

    /**
     * 获取会话历史
     *
     * @param sessionId 会话ID
     * @param httpRequest HTTP请求
     * @return 历史记录列表
     */
    @GetMapping("/history/{sessionId}")
    public R<List<ChatHistory>> getHistory(@PathVariable String sessionId,
                                           HttpServletRequest httpRequest) {
        String userId = getCurrentUserId(httpRequest);
        log.info("获取会话历史: userId={}, sessionId={}", userId, sessionId);

        // 验证会话归属
        List<ChatHistory> histories = chatService.getChatHistory(sessionId);
        if (!histories.isEmpty() && !histories.get(0).getUserId().equals(userId)) {
            return R.fail(403, "无权访问该会话");
        }

        return R.success(histories);
    }

    /**
     * 获取用户所有会话列表
     *
     * @param httpRequest HTTP请求
     * @return 会话ID列表
     */
    @GetMapping("/sessions")
    public R<List<String>> getUserSessions(HttpServletRequest httpRequest) {
        String userId = getCurrentUserId(httpRequest);
        log.info("获取用户会话列表: userId={}", userId);

        List<String> sessions = chatService.getUserSessions(userId);
        return R.success(sessions);
    }

    /**
     * 更新对话反馈
     *
     * @param historyId 历史记录ID
     * @param feedback  反馈：1点赞，-1点踩
     * @param comment   反馈意见（可选）
     * @param httpRequest HTTP请求
     * @return 操作结果
     */
    @PostMapping("/feedback/{historyId}")
    public R<Boolean> updateFeedback(@PathVariable Long historyId,
                                     @RequestParam Integer feedback,
                                     @RequestParam(required = false) String comment,
                                     HttpServletRequest httpRequest) {
        String userId = getCurrentUserId(httpRequest);
        log.info("更新对话反馈: userId={}, historyId={}, feedback={}", userId, historyId, feedback);

        // 验证操作权限
        List<ChatHistory> histories = chatService.getChatHistory(chatService.getChatHistory(null).stream()
                .filter(h -> h.getId().equals(historyId))
                .findFirst()
                .map(ChatHistory::getSessionId)
                .orElse(null));
        if (!histories.isEmpty() && !histories.get(0).getUserId().equals(userId)) {
            return R.fail(403, "无权操作该反馈");
        }

        boolean success = chatService.updateFeedback(historyId, feedback, comment);
        if (success) {
            return R.success(true);
        } else {
            return R.fail("更新反馈失败");
        }
    }

    /**
     * 清除会话历史
     *
     * @param sessionId 会话ID
     * @param httpRequest HTTP请求
     * @return 操作结果
     */
    @DeleteMapping("/history/{sessionId}")
    public R<Boolean> clearHistory(@PathVariable String sessionId,
                                   HttpServletRequest httpRequest) {
        String userId = getCurrentUserId(httpRequest);
        log.info("清除会话历史: userId={}, sessionId={}", userId, sessionId);

        boolean success = chatService.clearChatHistory(sessionId, userId);
        if (success) {
            return R.success(true);
        } else {
            return R.fail("清除会话历史失败");
        }
    }

    /**
     * 健康检查
     *
     * @return 服务状态
     */
    @GetMapping("/health")
    public R<String> health() {
        return R.success("Chat service is running");
    }
}