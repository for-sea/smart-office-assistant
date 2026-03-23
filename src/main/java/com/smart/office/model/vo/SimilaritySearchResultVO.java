package com.smart.office.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 相似度搜索结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilaritySearchResultVO {

    /**
     * 块ID（Milvus主键）
     */
    private String chunkId;

    /**
     * 文档ID
     */
    private Long docId;

    /**
     * 文档名称（可选，用于展示）
     */
    private String docName;

    /**
     * 文档类型
     */
    private String docType;

    /**
     * 文本块内容
     */
    private String content;

    /**
     * 相似度得分
     */
    private Double score;

    /**
     * 文本块索引（在文档中的位置）
     */
    private Integer chunkIndex;

    /**
     * 所属标题
     */
    private String heading;

    /**
     * 页码（如果是PDF）
     */
    private Integer pageNumber;
}
