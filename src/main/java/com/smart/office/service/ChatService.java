package com.smart.office.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.office.model.dto.ChatRequestDTO;
import com.smart.office.model.dto.ChatResponseDTO;
import com.smart.office.model.entity.ChatHistory;
import com.smart.office.model.entity.Document;
import com.smart.office.model.vo.SimilaritySearchResultVO;
import com.smart.office.model.vo.StreamResponseVO;
import com.smart.office.repository.mapper.ChatHistoryMapper;
import com.smart.office.repository.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 聊天服务
 * 负责处理聊天请求的完整流程：向量化 → 检索 → 构建Prompt → 调用LLM → 保存历史
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final EmbeddingModel embeddingModel;
    private final VectorSearchService vectorSearchService;
    private final PromptService promptService;
    private final LLMService llmService;
    private final DocumentService documentService;
    private final ChatHistoryMapper chatHistoryMapper;
    private final DocumentMapper documentMapper;

    // 用于存储流式响应的会话上下文
    private final Map<String, StringBuilder> streamContextMap = new ConcurrentHashMap<>();

    /**
     * 同步处理聊天请求
     *
     * @param request 聊天请求
     * @param userId  用户ID
     * @return 聊天响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatResponseDTO chatSync(ChatRequestDTO request, String userId) {
        long startTime = System.currentTimeMillis();
        String question = request.getQuestion();
        String sessionId = request.getSessionId();
        List<ChatRequestDTO.Message> history = request.getHistory();

        log.info("开始同步处理聊天请求: userId={}, sessionId={}, question={}, historySize={}",
                userId, sessionId, question, history != null ? history.size() : 0);

        try {
            // 1. 获取用户可访问的文档ID列表
            List<Long> accessibleDocIds = vectorSearchService.getAccessibleDocIds(userId, documentService);

            // 2. 生成问题向量
            List<Float> queryVector = generateQueryVector(question);

            // 3. 向量相似度检索
            List<SimilaritySearchResultVO> searchResults = new ArrayList<>();
            if (!accessibleDocIds.isEmpty()) {
                searchResults = vectorSearchService.searchSimilarChunks(
                        queryVector, 5, accessibleDocIds);
                log.info("向量检索完成: 结果数量={}", searchResults.size());
                // 补充文档名称信息
                enrichSearchResultsWithDocName(searchResults);
            }

            // 4. 构建Prompt（包含历史对话）
            String prompt = buildPromptWithHistory(question, searchResults, history);

            // 5. 调用LLM生成回答
            String answer;
            if (history != null && !history.isEmpty()) {
                answer = llmService.generateAnswerWithHistory(prompt, history);
            } else {
                answer = llmService.generateAnswer(prompt);
            }

            // 6. 保存对话历史
            String finalSessionId = saveChatHistory(userId, sessionId, question, answer, searchResults, startTime);

            // 7. 构建返回结果
            long latency = System.currentTimeMillis() - startTime;
            log.info("同步聊天处理完成: userId={}, sessionId={}, latency={}ms", userId, finalSessionId, latency);

            return buildChatResponse(answer, finalSessionId, searchResults, latency);

        } catch (Exception e) {
            log.error("同步聊天处理失败: userId={}, question={}", userId, question, e);
            return buildErrorResponse(question, sessionId, startTime, e.getMessage());
        }
    }

    /**
     * 流式处理聊天请求
     *
     * @param request 聊天请求
     * @param userId  用户ID
     * @return 流式响应Flux
     */
    public Flux<StreamResponseVO> chatStream(ChatRequestDTO request, String userId) {
        String question = request.getQuestion();
        String sessionId = request.getSessionId();
        List<ChatRequestDTO.Message> history = request.getHistory();

        log.info("开始流式处理聊天请求: userId={}, sessionId={}, question={}, historySize={}",
                userId, sessionId, question, history != null ? history.size() : 0);

        // 初始化流式上下文
        String contextKey = sessionId != null ? sessionId : userId + "_" + System.currentTimeMillis();
        streamContextMap.put(contextKey, new StringBuilder());

        return Flux.create(sink -> {
            try {
                // 1. 获取用户可访问的文档ID列表
                List<Long> accessibleDocIds = vectorSearchService.getAccessibleDocIds(userId, documentService);

                // 2. 生成问题向量
                List<Float> queryVector = generateQueryVector(question);

                // 3. 向量相似度检索
                List<SimilaritySearchResultVO> searchResults = new ArrayList<>();
                if (!accessibleDocIds.isEmpty()) {
                    searchResults = vectorSearchService.searchSimilarChunks(
                            queryVector, 5, accessibleDocIds);
                    log.info("向量检索完成: 结果数量={}", searchResults.size());
                    // 补充文档名称信息
                    enrichSearchResultsWithDocName(searchResults);
                }

                // 4. 构建Prompt（包含历史对话）
                String prompt = buildPromptWithHistory(question, searchResults, history);

                // 5. 准备保存历史的数据
                long startTime = System.currentTimeMillis();
                StringBuilder fullAnswer = new StringBuilder();

                // 将需要传递给lambda的变量声明为final
                final String finalUserId = userId;
                final String finalSessionId = sessionId;
                final String finalQuestion = question;
                final List<SimilaritySearchResultVO> finalSearchResults = searchResults;

                // 6. 流式调用LLM
                if (history != null && !history.isEmpty()) {
                    llmService.generateAnswerStreamWithHistory(
                            prompt,
                            history,
                            chunk -> {
                                // 每收到一个chunk，立即发送给客户端
                                fullAnswer.append(chunk);
                                sink.next(StreamResponseVO.of(chunk));
                            },
                            () -> {
                                // 流式完成，保存历史并发送完成信号
                                long latency = System.currentTimeMillis() - startTime;
                                String finalAnswer = fullAnswer.toString();

                                // 使用final变量调用saveChatHistory
                                String savedSessionId = saveChatHistory(
                                        finalUserId,
                                        finalSessionId,
                                        finalQuestion,
                                        finalAnswer,
                                        finalSearchResults,
                                        startTime);

                                sink.next(StreamResponseVO.finish());
                                sink.complete();
                                streamContextMap.remove(contextKey);

                                log.info("流式聊天处理完成: userId={}, sessionId={}, latency={}ms, answerLength={}",
                                        finalUserId, savedSessionId, latency, finalAnswer.length());
                            },
                            sink::error
                    );
                } else {
                    llmService.generateAnswerStream(
                            prompt,
                            chunk -> {
                                fullAnswer.append(chunk);
                                sink.next(StreamResponseVO.of(chunk));
                            },
                            () -> {
                                long latency = System.currentTimeMillis() - startTime;
                                String finalAnswer = fullAnswer.toString();

                                // 使用final变量调用saveChatHistory
                                String savedSessionId = saveChatHistory(
                                        finalUserId,
                                        finalSessionId,
                                        finalQuestion,
                                        finalAnswer,
                                        finalSearchResults,
                                        startTime);

                                sink.next(StreamResponseVO.finish());
                                sink.complete();
                                streamContextMap.remove(contextKey);

                                log.info("流式聊天处理完成: userId={}, sessionId={}, latency={}ms, answerLength={}",
                                        finalUserId, savedSessionId, latency, finalAnswer.length());
                            },
                            sink::error
                    );
                }

            } catch (Exception e) {
                log.error("流式聊天处理失败: userId={}, question={}", userId, question, e);
                sink.error(e);
                streamContextMap.remove(contextKey);
            }
        });
    }

    /**
     * 生成问题向量
     */
    private List<Float> generateQueryVector(String question) {
        try {
            EmbeddingRequest embeddingRequest = new EmbeddingRequest(
                    Collections.singletonList(question),
                    null
            );
            EmbeddingResponse response = embeddingModel.call(embeddingRequest);
            float[] floats = response.getResult().getOutput();

            List<Float> floatsList = new ArrayList<>();
            for (float f : floats) {
                floatsList.add(f);
            }
            return floatsList;
        } catch (Exception e) {
            log.error("向量生成失败: {}", e.getMessage());
            throw new RuntimeException("向量生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建Prompt（包含历史对话）
     */
    private String buildPromptWithHistory(String question, List<SimilaritySearchResultVO> searchResults,
                                          List<ChatRequestDTO.Message> history) {
        // 构建基础Prompt
        String basePrompt = promptService.buildPrompt(question, searchResults);

        // 如果有历史对话，将其融入到Prompt中
        if (history != null && !history.isEmpty()) {
            StringBuilder historyText = new StringBuilder();
            historyText.append("\n\n### 历史对话 ###\n");
            for (ChatRequestDTO.Message msg : history) {
                if ("user".equals(msg.getRole())) {
                    historyText.append("用户: ").append(msg.getContent()).append("\n");
                } else if ("assistant".equals(msg.getRole())) {
                    historyText.append("助手: ").append(msg.getContent()).append("\n");
                }
            }
            return basePrompt + historyText.toString();
        }

        return basePrompt;
    }

    /**
     * 补充检索结果的文档名称
     */
    private void enrichSearchResultsWithDocName(List<SimilaritySearchResultVO> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return;
        }

        // 收集所有文档ID
        List<Long> docIds = searchResults.stream()
                .map(SimilaritySearchResultVO::getDocId)
                .distinct()
                .collect(Collectors.toList());

        // 批量查询文档信息
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Document::getId, docIds);
        List<Document> documents = documentMapper.selectList(wrapper);

        Map<Long, String> docNameMap = documents.stream()
                .collect(Collectors.toMap(Document::getId, Document::getFileName, (v1, v2) -> v1));

        // 补充文档名称
        for (SimilaritySearchResultVO result : searchResults) {
            String docName = docNameMap.get(result.getDocId());
            if (docName != null) {
                result.setDocName(docName);
            }
        }
    }

    /**
     * 保存对话历史到MySQL
     */
    private String saveChatHistory(String userId, String sessionId, String question,
                                   String answer, List<SimilaritySearchResultVO> searchResults,
                                   long startTime) {
        try {
            // 生成或使用传入的sessionId
            String finalSessionId = sessionId;
            if (finalSessionId == null || finalSessionId.isEmpty()) {
                finalSessionId = generateSessionId(userId);
            }

            // 构建来源信息JSON
            String sourcesJson = buildSourcesJson(searchResults);

            // 创建历史记录
            ChatHistory history = new ChatHistory();
            history.setSessionId(finalSessionId);
            history.setUserId(userId);
            history.setQuestion(question);
            history.setAnswer(answer);
            history.setSources(sourcesJson);
            history.setLatencyMs((int) (System.currentTimeMillis() - startTime));
            history.setCreateTime(LocalDateTime.now());
            history.setFeedback(0); // 初始无反馈

            chatHistoryMapper.insert(history);

            log.info("对话历史已保存: sessionId={}, historyId={}", finalSessionId, history.getId());
            return finalSessionId;

        } catch (Exception e) {
            // 保存历史失败不影响主流程，仅记录日志
            log.error("保存对话历史失败: {}", e.getMessage());
            return sessionId != null ? sessionId : generateSessionId(userId);
        }
    }

    /**
     * 构建来源信息JSON
     */
    private String buildSourcesJson(List<SimilaritySearchResultVO> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> sources = new ArrayList<>();
        for (SimilaritySearchResultVO result : searchResults) {
            Map<String, Object> source = new HashMap<>();
            source.put("docId", result.getDocId());
            source.put("docName", result.getDocName());
            source.put("chunkId", result.getChunkId());
            source.put("content", result.getContent());
            source.put("score", result.getScore());
            source.put("chunkIndex", result.getChunkIndex());
            source.put("heading", result.getHeading());
            source.put("pageNumber", result.getPageNumber());
            sources.add(source);
        }

        return JSON.toJSONString(sources);
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId(String userId) {
        return userId + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 构建聊天响应DTO
     */
    private ChatResponseDTO buildChatResponse(String answer, String sessionId,
                                              List<SimilaritySearchResultVO> searchResults,
                                              long latency) {

        List<ChatResponseDTO.SourceInfo> sources = new ArrayList<>();
        if (searchResults != null && !searchResults.isEmpty()) {
            for (SimilaritySearchResultVO result : searchResults) {
                ChatResponseDTO.SourceInfo sourceInfo = ChatResponseDTO.SourceInfo.builder()
                        .docId(result.getDocId())
                        .docName(result.getDocName())
                        .chunkId(result.getChunkId())
                        .content(truncateContent(result.getContent(), 200))
                        .score(result.getScore())
                        .build();
                sources.add(sourceInfo);
            }
        }

        return ChatResponseDTO.builder()
                .answer(answer)
                .sessionId(sessionId)
                .sources(sources)
                .latencyMs(latency)
                .totalTokens(null) // TODO: 从LLM响应中获取token信息
                .build();
    }

    /**
     * 构建错误响应
     */
    private ChatResponseDTO buildErrorResponse(String question, String sessionId, long startTime, String errorMsg) {
        String answer = "抱歉，处理您的问题时出现了错误：" + errorMsg;

        long latency = System.currentTimeMillis() - startTime;

        return ChatResponseDTO.builder()
                .answer(answer)
                .sessionId(sessionId)
                .sources(Collections.emptyList())
                .latencyMs(latency)
                .totalTokens(0)
                .build();
    }

    /**
     * 截取内容摘要
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return null;
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    /**
     * 获取对话历史
     *
     * @param sessionId 会话ID
     * @return 历史记录列表
     */
    public List<ChatHistory> getChatHistory(String sessionId) {
        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatHistory::getSessionId, sessionId)
                .orderByAsc(ChatHistory::getCreateTime);
        return chatHistoryMapper.selectList(wrapper);
    }

    /**
     * 获取用户所有会话
     *
     * @param userId 用户ID
     * @return 会话ID列表
     */
    public List<String> getUserSessions(String userId) {
        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatHistory::getUserId, userId)
                .select(ChatHistory::getSessionId)
                .groupBy(ChatHistory::getSessionId)
                .orderByDesc(ChatHistory::getCreateTime);
        List<ChatHistory> histories = chatHistoryMapper.selectList(wrapper);
        return histories.stream()
                .map(ChatHistory::getSessionId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 更新对话反馈
     *
     * @param historyId 历史记录ID
     * @param feedback  反馈：1点赞，-1点踩，0无反馈
     * @param comment   反馈意见
     * @return 是否成功
     */
    public boolean updateFeedback(Long historyId, Integer feedback, String comment) {
        try {
            ChatHistory history = new ChatHistory();
            history.setId(historyId);
            history.setFeedback(feedback);
            history.setFeedbackComment(comment);
            return chatHistoryMapper.updateById(history) > 0;
        } catch (Exception e) {
            log.error("更新反馈失败: historyId={}", historyId, e);
            return false;
        }
    }

    /**
     * 清除会话历史
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean clearChatHistory(String sessionId, String userId) {
        try {
            LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatHistory::getSessionId, sessionId)
                    .eq(ChatHistory::getUserId, userId);
            int deleted = chatHistoryMapper.delete(wrapper);
            log.info("清除会话历史: sessionId={}, userId={}, deletedCount={}", sessionId, userId, deleted);
            return deleted > 0;
        } catch (Exception e) {
            log.error("清除会话历史失败: sessionId={}, userId={}", sessionId, userId, e);
            return false;
        }
    }
}