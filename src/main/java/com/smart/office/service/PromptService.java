package com.smart.office.service;

import com.smart.office.model.prompt.PromptTemplate;
import com.smart.office.model.vo.SimilaritySearchResultVO;
import java.util.List;

/**
 * Prompt 模板抽象定义。
 */
public interface PromptService {

    /**
     * 根据默认模板构建 Prompt。
     */
    public abstract String buildPrompt(String question, List<SimilaritySearchResultVO> contexts);

    /**
     * 自定义模板版本的 Prompt 构建。
     */
    public abstract String buildPrompt(String question,
                                       List<SimilaritySearchResultVO> contexts,
                                       PromptTemplate template);
}
