package com.smart.office.service;

import com.smart.office.model.dto.ChatRequestDTO;
import com.smart.office.model.vo.StreamResponseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 大语言模型服务
 * 使用 Ollama 本地模型，支持同步和流式返回
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private final ChatClient chatClient;

    // 默认系统提示词
    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个智能办公助手，请基于提供的上下文信息回答问题。" +
                    "如果上下文中没有相关信息，请明确告知用户'知识库中未找到相关信息'。" +
                    "不要编造信息，回答要简洁准确，使用中文回复。";

    /**
     * 同步生成回答
     *
     * @param prompt 提示词
     * @return 生成的回答
     */
    public String generateAnswer(String prompt) {
        return generateAnswer(prompt, DEFAULT_SYSTEM_PROMPT);
    }

    /**
     * 同步生成回答（带自定义系统提示）
     *
     * @param prompt 提示词
     * @param systemPrompt 系统提示词
     * @return 生成的回答
     */
    public String generateAnswer(String prompt, String systemPrompt) {
        log.info("开始同步生成回答：prompt 长度={}", prompt.length());
        long startTime = System.currentTimeMillis();

        try {
            String answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();

            long cost = System.currentTimeMillis() - startTime;
            log.info("同步生成完成：耗时={}ms, 回答长度={}", cost, answer.length());

            return answer;

        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            return "抱歉，模型调用失败，请稍后重试。错误信息：" + e.getMessage();
        }
    }

    /**
     * 同步生成回答（带上下文历史）
     *
     * @param prompt 当前提示词
     * @param history 历史对话记录
     * @return 生成的回答
     */
    public String generateAnswerWithHistory(String prompt, List<ChatRequestDTO.Message> history) {
        return generateAnswerWithHistory(prompt, history, DEFAULT_SYSTEM_PROMPT);
    }

    /**
     * 同步生成回答（带上下文历史和自定义系统提示）
     *
     * @param prompt 当前提示词
     * @param history 历史对话记录
     * @param systemPrompt 系统提示词
     * @return 生成的回答
     */
    public String generateAnswerWithHistory(String prompt, List<ChatRequestDTO.Message> history,
                                            String systemPrompt) {
        log.info("开始同步生成回答（带历史）: prompt 长度={}, 历史记录数={}",
                prompt.length(), history != null ? history.size() : 0);
        long startTime = System.currentTimeMillis();

        try {
            // 构建完整的消息列表
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));

            // 添加历史对话
            if (history != null && !history.isEmpty()) {
                for (ChatRequestDTO.Message msg : history) {
                    if ("user".equals(msg.getRole())) {
                        messages.add(new UserMessage(msg.getContent()));
                    } else if ("assistant".equals(msg.getRole())) {
                        messages.add(new org.springframework.ai.chat.messages.AssistantMessage(msg.getContent()));
                    }
                }
            }

            // 添加当前问题
            messages.add(new UserMessage(prompt));

            String answer = chatClient.prompt()
                    .messages(messages)
                    .call()
                    .content();

            long cost = System.currentTimeMillis() - startTime;
            log.info("同步生成完成（带历史）: 耗时={}ms, 回答长度={}", cost, answer.length());

            return answer;

        } catch (Exception e) {
            log.error("LLM 调用失败", e);
            return "抱歉，模型调用失败，请稍后重试。错误信息：" + e.getMessage();
        }
    }

    /**
     * 流式生成回答
     *
     * @param prompt 提示词
     * @param onNext 每块内容的回调
     * @param onComplete 完成时的回调
     * @param onError 错误时的回调
     */
    public void generateAnswerStream(String prompt,
                                     Consumer<String> onNext,
                                     Runnable onComplete,
                                     Consumer<Throwable> onError) {
        generateAnswerStream(prompt, DEFAULT_SYSTEM_PROMPT, onNext, onComplete, onError);
    }

    /**
     * 流式生成回答（带自定义系统提示）
     *
     * @param prompt 提示词
     * @param systemPrompt 系统提示词
     * @param onNext 每块内容的回调
     * @param onComplete 完成时的回调
     * @param onError 错误时的回调
     */
    public void generateAnswerStream(String prompt,
                                     String systemPrompt,
                                     Consumer<String> onNext,
                                     Runnable onComplete,
                                     Consumer<Throwable> onError) {
        log.info("开始流式生成回答：prompt 长度={}", prompt.length());
        long startTime = System.currentTimeMillis();

        try {
            Flux<String> flux = chatClient.prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .stream()
                    .content();

            flux.subscribe(
                    chunk -> {
                        if (onNext != null) {
                            onNext.accept(chunk);
                        }
                    },
                    error -> {
                        log.error("流式生成错误", error);
                        if (onError != null) {
                            onError.accept(error);
                        }
                    },
                    () -> {
                        long cost = System.currentTimeMillis() - startTime;
                        log.info("流式生成完成：耗时={}ms", cost);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
            );

        } catch (Exception e) {
            log.error("LLM 流式调用失败", e);
            if (onError != null) {
                onError.accept(e);
            }
        }
    }

    /**
     * 流式生成回答（返回 Flux）
     *
     * @param prompt 提示词
     * @return 流式响应 Flux
     */
    public Flux<StreamResponseVO> generateAnswerStreamFlux(String prompt) {
        return generateAnswerStreamFlux(prompt, DEFAULT_SYSTEM_PROMPT);
    }

    /**
     * 流式生成回答（返回 Flux，带自定义系统提示）
     *
     * @param prompt 提示词
     * @param systemPrompt 系统提示词
     * @return 流式响应 Flux
     */
    public Flux<StreamResponseVO> generateAnswerStreamFlux(String prompt, String systemPrompt) {
        log.info("开始流式生成回答 (Flux): prompt 长度={}", prompt.length());

        return chatClient.prompt()
                .system(systemPrompt)
                .user(prompt)
                .stream()
                .content()
                .map(content -> {
                    StreamResponseVO vo = new StreamResponseVO();
                    vo.setContent(content);
                    vo.setFinish(false);
                    return vo;
                })
                .concatWith(Flux.just(StreamResponseVO.finish()));
    }

    /**
     * 流式生成回答（带历史记录）
     *
     * @param prompt 当前提示词
     * @param history 历史对话记录
     * @param onNext 每块内容的回调
     * @param onComplete 完成时的回调
     * @param onError 错误时的回调
     */
    public void generateAnswerStreamWithHistory(String prompt,
                                                List<ChatRequestDTO.Message> history,
                                                Consumer<String> onNext,
                                                Runnable onComplete,
                                                Consumer<Throwable> onError) {
        generateAnswerStreamWithHistory(prompt, history, DEFAULT_SYSTEM_PROMPT,
                onNext, onComplete, onError);
    }

    /**
     * 流式生成回答（带历史记录和自定义系统提示）
     */
    public void generateAnswerStreamWithHistory(String prompt,
                                                List<ChatRequestDTO.Message> history,
                                                String systemPrompt,
                                                Consumer<String> onNext,
                                                Runnable onComplete,
                                                Consumer<Throwable> onError) {
        log.info("开始流式生成回答（带历史）: prompt 长度={}, 历史记录数={}",
                prompt.length(), history != null ? history.size() : 0);
        long startTime = System.currentTimeMillis();

        try {
            // 构建完整的消息列表
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));

            // 添加历史对话
            if (history != null && !history.isEmpty()) {
                for (ChatRequestDTO.Message msg : history) {
                    if ("user".equals(msg.getRole())) {
                        messages.add(new UserMessage(msg.getContent()));
                    } else if ("assistant".equals(msg.getRole())) {
                        messages.add(new org.springframework.ai.chat.messages.AssistantMessage(msg.getContent()));
                    }
                }
            }

            // 添加当前问题
            messages.add(new UserMessage(prompt));

            Flux<String> flux = chatClient.prompt()
                    .messages(messages)
                    .stream()
                    .content();

            flux.subscribe(
                    chunk -> {
                        if (onNext != null) {
                            onNext.accept(chunk);
                        }
                    },
                    error -> {
                        log.error("流式生成错误", error);
                        if (onError != null) {
                            onError.accept(error);
                        }
                    },
                    () -> {
                        long cost = System.currentTimeMillis() - startTime;
                        log.info("流式生成完成（带历史）: 耗时={}ms", cost);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
            );

        } catch (Exception e) {
            log.error("LLM 流式调用失败", e);
            if (onError != null) {
                onError.accept(e);
            }
        }
    }

    /**
     * 流式生成回答（返回 Flux，带历史记录）
     *
     * @param prompt 当前提示词
     * @param history 历史对话记录
     * @return 流式响应 Flux
     */
    public Flux<StreamResponseVO> generateAnswerStreamFluxWithHistory(
            String prompt, List<ChatRequestDTO.Message> history) {
        return generateAnswerStreamFluxWithHistory(prompt, history, DEFAULT_SYSTEM_PROMPT);
    }

    /**
     * 流式生成回答（返回 Flux，带历史记录和自定义系统提示）
     */
    public Flux<StreamResponseVO> generateAnswerStreamFluxWithHistory(
            String prompt, List<ChatRequestDTO.Message> history, String systemPrompt) {
        log.info("开始流式生成回答 (Flux 带历史): prompt 长度={}, 历史记录数={}",
                prompt.length(), history != null ? history.size() : 0);

        // 构建完整的消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        // 添加历史对话
        if (history != null && !history.isEmpty()) {
            for (ChatRequestDTO.Message msg : history) {
                if ("user".equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(new org.springframework.ai.chat.messages.AssistantMessage(msg.getContent()));
                }
            }
        }

        // 添加当前问题
        messages.add(new UserMessage(prompt));

        return chatClient.prompt()
                .messages(messages)
                .stream()
                .content()
                .map(content -> {
                    StreamResponseVO vo = new StreamResponseVO();
                    vo.setContent(content);
                    vo.setFinish(false);
                    return vo;
                })
                .concatWith(Flux.just(StreamResponseVO.finish()));
    }

    /**
     * 获取可用的模型列表
     *
     * @return 模型列表
     */
    public List<String> getAvailableModels() {
        try {
            // Ollama 的模型列表获取方式
            // 这里简化处理，返回常用模型
            return List.of("qwen2.5:7b", "llama3.2:3b", "deepseek-r1:7b", "mistral:7b");
        } catch (Exception e) {
            log.error("获取模型列表失败", e);
            return List.of();
        }
    }

    /**
     * 测试模型连接
     *
     * @return 是否连接成功
     */
    public boolean testConnection() {
        try {
            String testResponse = generateAnswer("你好，请回复'连接成功'", "你只需要回复'连接成功'四个字");
            return testResponse != null && !testResponse.contains("失败");
        } catch (Exception e) {
            log.error("测试模型连接失败", e);
            return false;
        }
    }
}