package com.smart.office.model.prompt;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 增强版 Prompt 模板
 * 支持多种问答场景
 */
@Data
@Builder
public class EnhancedPromptTemplate {

    /**
     * 场景类型
     */
    private SceneType scene;

    /**
     * 系统指令
     */
    private String systemInstruction;

    /**
     * 上下文列表
     */
    private List<ContextItem> contexts;

    /**
     * 用户问题
     */
    private String question;

    /**
     * 对话历史
     */
    private List<HistoryItem> history;

    /**
     * 额外参数
     */
    private Map<String, Object> extraParams;

    /**
     * 场景枚举
     */
    public enum SceneType {
        QA,           // 普通问答
        SUMMARIZE,    // 文档摘要
        ANALYZE,      // 数据分析
        COMPARE,      // 对比分析
        RECOMMEND,    // 推荐
        CODE          // 代码相关
    }

    @Data
    @Builder
    public static class ContextItem {
        private String content;
        private String source;
        private Double score;
        private String heading;
        private Long docId;
    }

    @Data
    @Builder
    public static class HistoryItem {
        private String role;    // user/assistant
        private String content;
    }

    /**
     * 构建器
     */
    public static class EnhancedPromptTemplateBuilder {

        public String build() {
            StringBuilder prompt = new StringBuilder();

            // 1. 系统指令
            prompt.append("### 系统指令 ###\n");
            prompt.append(getSystemInstructionByScene()).append("\n\n");

            // 2. 对话历史（如果有）
            if (history != null && !history.isEmpty()) {
                prompt.append("### 对话历史 ###\n");
                for (HistoryItem item : history) {
                    prompt.append(item.getRole()).append(": ").append(item.getContent()).append("\n");
                }
                prompt.append("\n");
            }

            // 3. 上下文信息
            if (contexts != null && !contexts.isEmpty()) {
                prompt.append("### 参考资料 ###\n");
                for (int i = 0; i < contexts.size(); i++) {
                    ContextItem ctx = contexts.get(i);
                    prompt.append(String.format("[%d] 来源: %s (相关度: %.0f%%)\n",
                            i + 1, ctx.getSource(), ctx.getScore() * 100));
                    if (ctx.getHeading() != null) {
                        prompt.append("    章节: ").append(ctx.getHeading()).append("\n");
                    }
                    prompt.append("    内容: ").append(ctx.getContent()).append("\n\n");
                }
            } else {
                prompt.append("### 参考资料 ###\n");
                prompt.append("（未找到相关参考资料）\n\n");
            }

            // 4. 用户问题
            prompt.append("### 用户问题 ###\n");
            prompt.append(question).append("\n\n");

            // 5. 回答要求
            prompt.append("### 回答要求 ###\n");
            prompt.append(getAnswerRequirement()).append("\n\n");

            // 6. 回答
            prompt.append("### 回答 ###\n");

            return prompt.toString();
        }

        private String getSystemInstructionByScene() {
            if (systemInstruction != null) {
                return systemInstruction;
            }

            switch (scene) {
                case SUMMARIZE:
                    return "你是一个专业的文档摘要助手，请对提供的文档内容进行简洁、准确的总结。";
                case ANALYZE:
                    return "你是一个数据分析专家，请基于提供的数据进行分析，给出洞察和建议。";
                case COMPARE:
                    return "你是一个对比分析专家，请客观对比不同方案的优缺点。";
                case RECOMMEND:
                    return "你是一个智能推荐助手，请根据用户需求和上下文信息，给出合理的推荐。";
                case CODE:
                    return "你是一个技术专家，请提供清晰的代码示例和解释。";
                default:
                    return "你是一个智能办公助手，请基于提供的上下文信息回答问题。";
            }
        }

        private String getAnswerRequirement() {
            if (contexts == null || contexts.isEmpty()) {
                return "由于没有找到相关的参考资料，请告知用户\"知识库中未找到相关信息\"，不要编造答案。";
            }

            return "请严格基于以上参考资料回答问题。如果参考资料中没有相关信息，请明确告知用户。\n" +
                    "回答要简洁、准确、专业。";
        }
    }
}
