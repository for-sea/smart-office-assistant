package com.smart.office.application.impl;

import com.smart.office.domain.chat.prompt.PromptTemplate;
import com.smart.office.interfaces.vo.SimilaritySearchResultVO;
import com.smart.office.application.PromptService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Prompt 服务默认实现，统一封装模板组装逻辑。
 */
@Slf4j
@Service
public class PromptServiceImpl implements PromptService {

    @Override
    public String buildPrompt(String question, List<SimilaritySearchResultVO> contexts) {
        return buildPrompt(question, contexts, PromptTemplate.defaultTemplate());
    }

    @Override
    public String buildPrompt(String question,
                              List<SimilaritySearchResultVO> contexts,
                              PromptTemplate template) {
        log.info("开始构建 Prompt: questionLength={}, contextSize={}",
                question != null ? question.length() : 0,
                contexts != null ? contexts.size() : 0);

        String contextText = formatContext(contexts);
        Map<String, Object> params = new HashMap<>();
        params.put("system", template.getSystemTemplate());
        params.put("context", contextText);
        params.put("question", question);

        if (StringUtils.hasText(template.getFullTemplate())) {
            return replacePlaceholders(template.getFullTemplate(), params);
        }

        String systemPart = template.getSystemTemplate();
        String contextPart = replacePlaceholders(template.getContextTemplate(), params);
        String questionPart = replacePlaceholders(template.getQuestionTemplate(), params);
        return systemPart + "\n\n" + contextPart + "\n\n" + questionPart;
    }

    private String formatContext(List<SimilaritySearchResultVO> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return "【未检索到相关文档】";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            SimilaritySearchResultVO ctx = contexts.get(i);
            builder.append(String.format("[%d] 来源: %s (相关度: %.2f%%)\n",
                    i + 1,
                    ctx.getDocName() != null ? ctx.getDocName() : "文档ID:" + ctx.getDocId(),
                    ctx.getScore() * 100));
            if (StringUtils.hasText(ctx.getHeading())) {
                builder.append("    标题: ").append(ctx.getHeading()).append("\n");
            }
            builder.append("    内容: ").append(ctx.getContent()).append("\n\n");
        }
        return builder.toString();
    }

    private String replacePlaceholders(String template, Map<String, Object> params) {
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
