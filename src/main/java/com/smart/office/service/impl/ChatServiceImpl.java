package com.smart.office.service.impl;

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
import com.smart.office.security.LoginUserDetails;
import com.smart.office.service.ChatService;
import com.smart.office.service.LLMService;
import com.smart.office.service.PermissionService;
import com.smart.office.service.PromptService;
import com.smart.office.service.VectorSearchService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * 聊天业务默认实现，负责对话检索、Prompt 构建与历史存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final int DEFAULT_TOP_K = 5;

    private final EmbeddingModel embeddingModel;
    private final VectorSearchService vectorSearchService;
    private final PromptService promptService;
    private final LLMService llmService;
    private final PermissionService permissionService;
    private final ChatHistoryMapper chatHistoryMapper;
    private final DocumentMapper documentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatResponseDTO chatSync(ChatRequestDTO request, LoginUserDetails currentUser) {
        long startTime = System.currentTimeMillis();
        String question = request.getQuestion();
        String sessionId = request.getSessionId();
        List<ChatRequestDTO.Message> history = request.getHistory();
        String userId = resolveUserId(currentUser);

        log.info("开始处理同步对话: userId={}, sessionId={}, question={}, historySize={}",
                userId, sessionId, question, history != null ? history.size() : 0);

        try {
            List<SimilaritySearchResultVO> searchResults = retrieveSimilarChunks(currentUser, question);
            String prompt = buildPromptWithHistory(question, searchResults, history);

            String answer = (history != null && !history.isEmpty())
                    ? llmService.generateAnswerWithHistory(prompt, history)
                    : llmService.generateAnswer(prompt);

            String finalSessionId = saveChatHistory(
                    userId, sessionId, question, answer, searchResults, startTime);

            long latency = System.currentTimeMillis() - startTime;
            log.info("同步对话完成: userId={}, sessionId={}, latency={}ms", userId, finalSessionId, latency);
            return buildChatResponse(answer, finalSessionId, searchResults, latency);

        } catch (Exception e) {
            log.error("同步对话处理失败: userId={}, question={}", userId, question, e);
            return buildErrorResponse(question, sessionId, startTime, e.getMessage());
        }
    }

    @Override
    public Flux<StreamResponseVO> chatStream(ChatRequestDTO request, LoginUserDetails currentUser) {
        String question = request.getQuestion();
        String sessionId = request.getSessionId();
        List<ChatRequestDTO.Message> history = request.getHistory();
        String userId = resolveUserId(currentUser);

        log.info("开始处理流式对话: userId={}, sessionId={}, question={}, historySize={}",
                userId, sessionId, question, history != null ? history.size() : 0);

        return Flux.create(sink -> {
            try {
                List<SimilaritySearchResultVO> searchResults = retrieveSimilarChunks(currentUser, question);
                String prompt = buildPromptWithHistory(question, searchResults, history);
                long startTime = System.currentTimeMillis();
                StringBuilder fullAnswer = new StringBuilder();

                Runnable completeTask = () -> {
                    long latency = System.currentTimeMillis() - startTime;
                    String finalAnswer = fullAnswer.toString();
                    String finalSessionId = saveChatHistory(
                            userId, sessionId, question, finalAnswer, searchResults, startTime);
                    sink.next(StreamResponseVO.finish());
                    sink.complete();
                    log.info("流式对话完成: userId={}, sessionId={}, latency={}ms, answerLength={}",
                            userId, finalSessionId, latency, finalAnswer.length());
                };

                if (history != null && !history.isEmpty()) {
                    llmService.generateAnswerStreamWithHistory(
                            prompt,
                            history,
                            chunk -> {
                                fullAnswer.append(chunk);
                                sink.next(StreamResponseVO.of(chunk));
                            },
                            completeTask,
                            sink::error
                    );
                } else {
                    llmService.generateAnswerStream(
                            prompt,
                            chunk -> {
                                fullAnswer.append(chunk);
                                sink.next(StreamResponseVO.of(chunk));
                            },
                            completeTask,
                            sink::error
                    );
                }
            } catch (Exception e) {
                log.error("流式对话处理失败: userId={}, question={}", userId, question, e);
                sink.error(e);
            }
        });
    }

    @Override
    public List<ChatHistory> getChatHistory(String sessionId) {
        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatHistory::getSessionId, sessionId)
                .orderByAsc(ChatHistory::getCreateTime);
        return chatHistoryMapper.selectList(wrapper);
    }

    @Override
    public List<String> getUserSessions(String userId) {
        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatHistory::getUserId, userId)
                .select(ChatHistory::getSessionId)
                .groupBy(ChatHistory::getSessionId)
                .orderByDesc(ChatHistory::getCreateTime);
        return chatHistoryMapper.selectList(wrapper).stream()
                .map(ChatHistory::getSessionId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public boolean updateFeedback(String userId, Long historyId, Integer feedback, String comment) {
        try {
            ChatHistory original = chatHistoryMapper.selectById(historyId);
            if (original == null || !Objects.equals(original.getUserId(), userId)) {
                return false;
            }
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean clearChatHistory(String sessionId, String userId) {
        try {
            LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatHistory::getSessionId, sessionId)
                    .eq(ChatHistory::getUserId, userId);
            int deleted = chatHistoryMapper.delete(wrapper);
            log.info("清理会话历史: sessionId={}, userId={}, deleted={}", sessionId, userId, deleted);
            return deleted > 0;
        } catch (Exception e) {
            log.error("清理会话历史失败: sessionId={}, userId={}", sessionId, userId, e);
            return false;
        }
    }

    private List<SimilaritySearchResultVO> retrieveSimilarChunks(LoginUserDetails user, String question) {
        List<Long> accessibleDocIds = permissionService.getReadableDocumentIds(user);
        if (accessibleDocIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Float> queryVector = generateQueryVector(question);
        List<SimilaritySearchResultVO> searchResults =
                vectorSearchService.searchSimilarChunks(queryVector, DEFAULT_TOP_K, accessibleDocIds);
        enrichSearchResultsWithDocName(searchResults);
        return searchResults;
    }

    private List<Float> generateQueryVector(String question) {
        try {
            EmbeddingRequest embeddingRequest = new EmbeddingRequest(Collections.singletonList(question), null);
            EmbeddingResponse response = embeddingModel.call(embeddingRequest);
            float[] floats = response.getResult().getOutput();
            List<Float> vector = new ArrayList<>(floats.length);
            for (float value : floats) {
                vector.add(value);
            }
            return vector;
        } catch (Exception e) {
            log.error("生成检索向量失败", e);
            throw new IllegalStateException("生成检索向量失败: " + e.getMessage(), e);
        }
    }

    private String buildPromptWithHistory(String question,
                                          List<SimilaritySearchResultVO> searchResults,
                                          List<ChatRequestDTO.Message> history) {
        String basePrompt = promptService.buildPrompt(question, searchResults);
        if (history == null || history.isEmpty()) {
            return basePrompt;
        }

        StringBuilder historyBlock = new StringBuilder("\n\n### 历史对话 ###\n");
        for (ChatRequestDTO.Message msg : history) {
            if ("user".equals(msg.getRole())) {
                historyBlock.append("用户: ").append(msg.getContent()).append("\n");
            } else if ("assistant".equals(msg.getRole())) {
                historyBlock.append("助手: ").append(msg.getContent()).append("\n");
            }
        }
        return basePrompt + historyBlock;
    }

    private void enrichSearchResultsWithDocName(List<SimilaritySearchResultVO> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return;
        }
        List<Long> docIds = searchResults.stream()
                .map(SimilaritySearchResultVO::getDocId)
                .distinct()
                .collect(Collectors.toList());

        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Document::getId, docIds);
        Map<Long, String> docNameMap = documentMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(Document::getId, Document::getFileName, (a, b) -> a));

        for (SimilaritySearchResultVO result : searchResults) {
            String docName = docNameMap.get(result.getDocId());
            if (docName != null) {
                result.setDocName(docName);
            }
        }
    }

    private String saveChatHistory(String userId,
                                   String sessionId,
                                   String question,
                                   String answer,
                                   List<SimilaritySearchResultVO> searchResults,
                                   long startTime) {
        try {
            String finalSessionId = StringUtils.hasText(sessionId)
                    ? sessionId
                    : generateSessionId(userId);
            String sourcesJson = buildSourcesJson(searchResults);

            ChatHistory history = new ChatHistory();
            history.setSessionId(finalSessionId);
            history.setUserId(userId);
            history.setQuestion(question);
            history.setAnswer(answer);
            history.setSources(sourcesJson);
            history.setLatencyMs((int) (System.currentTimeMillis() - startTime));
            history.setCreateTime(LocalDateTime.now());
            history.setFeedback(0);

            chatHistoryMapper.insert(history);
            return finalSessionId;
        } catch (Exception e) {
            log.error("保存会话历史失败", e);
            return StringUtils.hasText(sessionId) ? sessionId : generateSessionId(userId);
        }
    }

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

    private ChatResponseDTO buildChatResponse(String answer,
                                              String sessionId,
                                              List<SimilaritySearchResultVO> searchResults,
                                              long latency) {
        List<ChatResponseDTO.SourceInfo> sources = new ArrayList<>();
        if (searchResults != null) {
            for (SimilaritySearchResultVO result : searchResults) {
                sources.add(ChatResponseDTO.SourceInfo.builder()
                        .docId(result.getDocId())
                        .docName(result.getDocName())
                        .chunkId(result.getChunkId())
                        .content(truncateContent(result.getContent(), 200))
                        .score(result.getScore())
                        .build());
            }
        }
        return ChatResponseDTO.builder()
                .answer(answer)
                .sessionId(sessionId)
                .sources(sources)
                .latencyMs(latency)
                .totalTokens(null)
                .build();
    }

    private ChatResponseDTO buildErrorResponse(String question,
                                               String sessionId,
                                               long startTime,
                                               String errorMsg) {
        long latency = System.currentTimeMillis() - startTime;
        return ChatResponseDTO.builder()
                .answer("抱歉，处理您的问题时出现异常：" + errorMsg)
                .sessionId(sessionId)
                .sources(Collections.emptyList())
                .latencyMs(latency)
                .totalTokens(0)
                .build();
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    private String resolveUserId(LoginUserDetails user) {
        if (user == null) {
            return "anonymous";
        }
        if (StringUtils.hasText(user.getUserId())) {
            return user.getUserId();
        }
        return user.getUsername();
    }

    private String generateSessionId(String userId) {
        return userId + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
