package com.smart.office.service;

import com.smart.office.model.dto.ChatRequestDTO;
import java.util.List;
import java.util.function.Consumer;

/**
 * 大模型服务抽象定义，约定问答与流式输出的核心接口。
 */
public interface LLMService {

    /**
     * 生成标准回答。
     */
     String generateAnswer(String prompt);

    /**
     * 基于历史上下文生成回答。
     */
     String generateAnswerWithHistory(String prompt, List<ChatRequestDTO.Message> history);

    /**
     * 以流式方式返回回答。
     */
     void generateAnswerStream(String prompt,
                                              Consumer<String> onNext,
                                              Runnable onComplete,
                                              Consumer<Throwable> onError);

    /**
     * 基于历史上下文的流式回答。
     */
     void generateAnswerStreamWithHistory(String prompt,
                                                         List<ChatRequestDTO.Message> history,
                                                         Consumer<String> onNext,
                                                         Runnable onComplete,
                                                         Consumer<Throwable> onError);
}
