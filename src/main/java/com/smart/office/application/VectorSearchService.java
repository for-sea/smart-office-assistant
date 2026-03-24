package com.smart.office.application;

import com.smart.office.interfaces.vo.SimilaritySearchResultVO;
import java.util.List;

/**
 * 向量检索抽象定义。
 */
public interface VectorSearchService {

    /**
     * 基于 Milvus 等向量库检索最相似的文本块。
     */
    public abstract List<SimilaritySearchResultVO> searchSimilarChunks(List<Float> queryVector,
                                                                       int topK,
                                                                       List<Long> docIds);
}
