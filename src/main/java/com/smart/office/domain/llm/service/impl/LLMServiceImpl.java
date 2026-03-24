package com.smart.office.domain.llm.service.impl;

import com.smart.office.interfaces.dto.ChatRequestDTO;
import com.smart.office.domain.llm.service.LLMService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 大模型默认实现，封装同步与流式问答能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一名智能办公助手，请基于提供的上下文回答问题。" +
            "若上下文不足以回答，请明确告知用户未找到相关信息。";

    private final ChatClient chatClient;

    @Override
    public String generateAnswer(String prompt) {
        log.info("开始同步生成回答: promptLength={}", prompt.length());
        try {
            String answer = chatClient.prompt()
                    .system(DEFAULT_SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();
            log.info("同步生成完成: answerLength={}", answer.length());
            return answer;
        } catch (Exception e) {
            log.error("调用大模型失败", e);
            return "抱歉，模型调用失败，请稍后重试。错误信息：" + e.getMessage();
        }
    }

    @Override
    public String generateAnswerWithHistory(String prompt, List<ChatRequestDTO.Message> history) {
        log.info("开始带历史的同步生成: promptLength={}, historySize={}",
                prompt.length(), history != null ? history.size() : 0);
        try {
            List<Message> messages = buildMessages(prompt, history);
            String answer = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content();
            log.info("带历史同步生成完成: answerLength={}", answer.length());
            return answer;
        } catch (Exception e) {
            log.error("调用大模型失败", e);
            return "抱歉，模型调用失败，请稍后重试。错误信息：" + e.getMessage();
        }
    }

    @Override
    public void generateAnswerStream(String prompt,
                                     Consumer<String> onNext,
                                     Runnable onComplete,
                                     Consumer<Throwable> onError) {
        log.info("开始流式生成回答: promptLength={}", prompt.length());
        try {
            Flux<String> flux = chatClient.prompt()
                    .system(DEFAULT_SYSTEM_PROMPT)
                    .user(prompt)
                    .stream()
                    .content();
            subscribeFlux(flux, onNext, onComplete, onError);
        } catch (Exception e) {
            log.error("流式调用大模型失败", e);
            if (onError != null) {
                onError.accept(e);
            }
        }
    }

    @Override
    public void generateAnswerStreamWithHistory(String prompt,
                                                List<ChatRequestDTO.Message> history,
                                                Consumer<String> onNext,
                                                Runnable onComplete,
                                                Consumer<Throwable> onError) {
        log.info("开始带历史的流式生成: promptLength={}, historySize={}",
                prompt.length(), history != null ? history.size() : 0);
        try {
            List<Message> messages = buildMessages(prompt, history);
            Flux<String> flux = chatClient.prompt()
                    .messages(messages)
                    .stream()
                    .content();
            subscribeFlux(flux, onNext, onComplete, onError);
        } catch (Exception e) {
            log.error("流式调用大模型失败", e);
            if (onError != null) {
                onError.accept(e);
            }
        }
    }

    private List<Message> buildMessages(String prompt, List<ChatRequestDTO.Message> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(DEFAULT_SYSTEM_PROMPT));
        if (history != null) {
            for (ChatRequestDTO.Message msg : history) {
                if ("user".equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }
        messages.add(new UserMessage(prompt));
        return messages;
    }

    private void subscribeFlux(Flux<String> flux,
                               Consumer<String> onNext,
                               Runnable onComplete,
                               Consumer<Throwable> onError) {
        flux.subscribe(
                chunk -> {
                    if (onNext != null) {
                        onNext.accept(chunk);
                    }
                },
                error -> {
                    log.error("流式推送出错", error);
                    if (onError != null) {
                        onError.accept(error);
                    }
                },
                () -> {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
        );
    }
}
