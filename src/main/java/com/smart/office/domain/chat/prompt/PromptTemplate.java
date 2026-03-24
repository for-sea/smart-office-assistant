package com.smart.office.domain.chat.prompt;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Prompt 模板
 * 结合系统指令、上下文块、用户问题
 */
@Slf4j
@Data
@Builder
public class PromptTemplate {

    /**
     * 系统指令模板
     */
    private String systemTemplate;

    /**
     * 上下文模板
     */
    private String contextTemplate;

    /**
     * 用户问题模板
     */
    private String questionTemplate;

    /**
     * 完整模板（可选，如果使用完整模板则忽略其他模板）
     */
    private String fullTemplate;

    /**
     * 模板参数
     */
    private Map<String, Object> parameters;

    /**
     * 默认系统指令
     */
    public static final String DEFAULT_SYSTEM_TEMPLATE =
            "你是一个智能办公助手，专门帮助用户解答关于企业内部文档的问题。\n" +
                    "请严格基于以下提供的上下文信息回答问题。\n" +
                    "如果上下文中没有足够的信息来回答问题，请明确告知用户：\"知识库中未找到相关信息\"。\n" +
                    "不要编造或臆想任何信息。\n" +
                    "回答要简洁、准确、专业，使用中文。\n" +
                    "如果上下文信息与问题不相关，请指出这一点。";

    /**
     * 默认上下文模板
     */
    public static final String DEFAULT_CONTEXT_TEMPLATE =
            "以下是相关的文档内容：\n" +
                    "---\n" +
                    "{context}\n" +
                    "---";

    /**
     * 默认用户问题模板
     */
    public static final String DEFAULT_QUESTION_TEMPLATE =
            "用户问题：{question}\n\n" +
                    "请根据以上上下文信息回答用户的问题。";

    /**
     * 默认完整模板（不使用单独模板时）
     */
    public static final String DEFAULT_FULL_TEMPLATE =
            "### 系统指令 ###\n" +
                    "{system}\n\n" +
                    "### 参考资料 ###\n" +
                    "{context}\n\n" +
                    "### 用户问题 ###\n" +
                    "{question}\n\n" +
                    "### 回答 ###\n";

    /**
     * 创建默认模板
     */
    public static PromptTemplate defaultTemplate() {
        return PromptTemplate.builder()
                .systemTemplate(DEFAULT_SYSTEM_TEMPLATE)
                .contextTemplate(DEFAULT_CONTEXT_TEMPLATE)
                .questionTemplate(DEFAULT_QUESTION_TEMPLATE)
                .build();
    }

    /**
     * 创建完整模板
     */
    public static PromptTemplate fullTemplate() {
        return PromptTemplate.builder()
                .fullTemplate(DEFAULT_FULL_TEMPLATE)
                .build();
    }
}
