package com.smart.office.service;

import com.smart.office.model.prompt.PromptTemplate;
import com.smart.office.model.vo.SimilaritySearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prompt 管理服务
 * 负责组装 Prompt 模板
 */
@Slf4j
@Service
public class PromptService {

    /**
     * 组装完整 Prompt
     *
     * @param question 用户问题
     * @param contexts 检索到的上下文块
     * @return 完整的 Prompt
     */
    public String buildPrompt(String question, List<SimilaritySearchResultVO> contexts) {
        return buildPrompt(question, contexts, PromptTemplate.defaultTemplate());
    }

    /**
     * 组装完整 Prompt（带自定义模板）
     *
     * @param question 用户问题
     * @param contexts 检索到的上下文块
     * @param template Prompt 模板
     * @return 完整的 Prompt
     */
    public String buildPrompt(String question, List<SimilaritySearchResultVO> contexts,
                              PromptTemplate template) {

        log.info("开始组装 Prompt: 问题长度={}, 上下文数量={}",
                question.length(), contexts.size());

        String contextText = formatContext(contexts);

        Map<String, Object> params = new HashMap<>();
        params.put("system", template.getSystemTemplate());
        params.put("context", contextText);
        params.put("question", question);

        String prompt;

        if (template.getFullTemplate() != null && !template.getFullTemplate().isEmpty()) {
            // 使用完整模板
            prompt = replacePlaceholders(template.getFullTemplate(), params);
        } else {
            // 使用分段模板
            String systemPart = template.getSystemTemplate();
            String contextPart = replacePlaceholders(template.getContextTemplate(), params);
            String questionPart = replacePlaceholders(template.getQuestionTemplate(), params);

            prompt = systemPart + "\n\n" + contextPart + "\n\n" + questionPart;
        }

        log.info("Prompt 组装完成: 总长度={}", prompt.length());

        return prompt;
    }

    /**
     * 格式化上下文块
     */
    private String formatContext(List<SimilaritySearchResultVO> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "（未找到相关文档内容）";
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < contexts.size(); i++) {
            SimilaritySearchResultVO ctx = contexts.get(i);

            sb.append(String.format("[%d] 来源文档: %s (相关度: %.2f%%)\n",
                    i + 1,
                    ctx.getDocName() != null ? ctx.getDocName() : "文档ID:" + ctx.getDocId(),
                    ctx.getScore() * 100));

            if (ctx.getHeading() != null && !ctx.getHeading().isEmpty()) {
                sb.append("    章节: ").append(ctx.getHeading()).append("\n");
            }

            sb.append("    内容: ").append(ctx.getContent()).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 替换模板中的占位符
     */
    private String replacePlaceholders(String template, Map<String, Object> params) {
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * 创建带评分的上下文格式化（用于调试）
     */
    public String formatContextWithScores(List<SimilaritySearchResultVO> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "无相关上下文";
        }

        return contexts.stream()
                .map(ctx -> String.format("[得分:%.3f] %s",
                        ctx.getScore(),
                        ctx.getContent().substring(0, Math.min(100, ctx.getContent().length()))))
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * 获取带来源信息的上下文（用于前端展示）
     */
    public List<Map<String, Object>> getContextWithSources(List<SimilaritySearchResultVO> contexts) {
        return contexts.stream()
                .map(ctx -> {
                    Map<String, Object> source = new HashMap<>();
                    source.put("docId", ctx.getDocId());
                    source.put("docName", ctx.getDocName());
                    source.put("chunkId", ctx.getChunkId());
                    source.put("content", ctx.getContent());
                    source.put("score", ctx.getScore());
                    source.put("heading", ctx.getHeading());
                    return source;
                })
                .collect(Collectors.toList());
    }
}