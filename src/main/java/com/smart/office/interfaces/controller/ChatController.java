package com.smart.office.interfaces.controller;

import com.smart.office.shared.common.R;
import com.smart.office.interfaces.dto.ChatRequestDTO;
import com.smart.office.interfaces.dto.ChatResponseDTO;
import com.smart.office.domain.chat.entity.ChatHistory;
import com.smart.office.interfaces.vo.StreamResponseVO;
import com.smart.office.shared.security.LoginUserDetails;
import com.smart.office.domain.chat.service.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 对话控制器，负责同步/流式问答及历史管理。
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/sync")
    public R<ChatResponseDTO> chatSync(@Valid @RequestBody ChatRequestDTO request,
                                       @AuthenticationPrincipal LoginUserDetails currentUser) {
        if (currentUser == null) {
            return R.fail(401, "未登录，无法发起对话");
        }
        log.info("同步对话: user={}, sessionId={}, question={}, historySize={}",
                currentUser.getUsername(), request.getSessionId(), request.getQuestion(),
                request.getHistory() != null ? request.getHistory().size() : 0);

        ChatResponseDTO response = chatService.chatSync(request, currentUser);
        return R.success(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamResponseVO> chatStream(@RequestBody ChatRequestDTO request,
                                             @AuthenticationPrincipal LoginUserDetails currentUser) {
        if (currentUser == null) {
            return Flux.error(new IllegalStateException("未登录，无法发起对话"));
        }
        log.info("流式对话: user={}, sessionId={}, question={}, historySize={}",
                currentUser.getUsername(), request.getSessionId(), request.getQuestion(),
                request.getHistory() != null ? request.getHistory().size() : 0);
        request.setStream(true);
        return chatService.chatStream(request, currentUser);
    }

    @PostMapping
    public Object chat(@Valid @RequestBody ChatRequestDTO request,
                       @AuthenticationPrincipal LoginUserDetails currentUser) {
        if (currentUser == null) {
            return R.fail(401, "未登录，无法发起对话");
        }
        log.info("通用对话: user={}, sessionId={}, question={}, stream={}, historySize={}",
                currentUser.getUsername(), request.getSessionId(), request.getQuestion(), request.isStream(),
                request.getHistory() != null ? request.getHistory().size() : 0);

        if (request.isStream()) {
            return chatService.chatStream(request, currentUser);
        }
        ChatResponseDTO response = chatService.chatSync(request, currentUser);
        return R.success(response);
    }

    @GetMapping("/history/{sessionId}")
    public R<List<ChatHistory>> getHistory(@PathVariable String sessionId,
                                           @AuthenticationPrincipal LoginUserDetails currentUser) {
        if (currentUser == null) {
            return R.fail(401, "未登录");
        }
        log.info("查询会话历史: user={}, sessionId={}", currentUser.getUsername(), sessionId);
        List<ChatHistory> histories = chatService.getChatHistory(sessionId);
        if (!histories.isEmpty() && !currentUserMatches(currentUser, histories.get(0).getUserId())) {
            return R.fail(403, "没有权限访问该会话");
        }
        return R.success(histories);
    }

    @GetMapping("/sessions")
    public R<List<String>> getUserSessions(@AuthenticationPrincipal LoginUserDetails currentUser) {
        if (currentUser == null) {
            return R.fail(401, "未登录");
        }
        log.info("查询会话列表: user={}", currentUser.getUsername());
        List<String> sessions = chatService.getUserSessions(resolveUserId(currentUser));
        return R.success(sessions);
    }

    @PostMapping("/feedback/{historyId}")
    public R<Boolean> updateFeedback(@PathVariable Long historyId,
                                     @RequestParam Integer feedback,
                                     @RequestParam(required = false) String comment,
                                     @AuthenticationPrincipal LoginUserDetails currentUser) {
        if (currentUser == null) {
            return R.fail(401, "未登录");
        }
        log.info("更新对话反馈: user={}, historyId={}, feedback={}",
                currentUser.getUsername(), historyId, feedback);
        boolean success = chatService.updateFeedback(resolveUserId(currentUser), historyId, feedback, comment);
        return success ? R.success(true) : R.fail("更新反馈失败");
    }

    @DeleteMapping("/history/{sessionId}")
    public R<Boolean> clearHistory(@PathVariable String sessionId,
                                   @AuthenticationPrincipal LoginUserDetails currentUser) {
        if (currentUser == null) {
            return R.fail(401, "未登录");
        }
        log.info("清除会话历史: user={}, sessionId={}", currentUser.getUsername(), sessionId);
        boolean success = chatService.clearChatHistory(sessionId, resolveUserId(currentUser));
        return success ? R.success(true) : R.fail("清除会话历史失败");
    }

    @GetMapping("/health")
    public R<String> health() {
        return R.success("Chat service is running");
    }

    private boolean currentUserMatches(LoginUserDetails user, String ownerId) {
        return resolveUserId(user).equals(ownerId);
    }

    private String resolveUserId(LoginUserDetails user) {
        if (user == null) {
            return "anonymous";
        }
        if (org.springframework.util.StringUtils.hasText(user.getUserId())) {
            return user.getUserId();
        }
        return user.getUsername();
    }
}
