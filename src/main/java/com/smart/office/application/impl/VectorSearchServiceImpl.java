package com.smart.office.application.impl;

import com.smart.office.interfaces.vo.SimilaritySearchResultVO;
import com.smart.office.base.data.repository.MilvusSearchRepository;
import com.smart.office.application.VectorSearchService;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 向量检索默认实现，负责封装 Milvus 查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchServiceImpl implements VectorSearchService {

    private final MilvusSearchRepository milvusSearchRepository;

    @Value("${spring.vectorstore.milvus.default-top-k:5}")
    private int defaultTopK;

    @Value("${spring.vectorstore.milvus.min-score:0.5}")
    private double minScore;

    @Override
    public List<SimilaritySearchResultVO> searchSimilarChunks(List<Float> queryVector,
                                                              int topK,
                                                              List<Long> docIds) {
        int actualTopK = topK > 0 ? topK : defaultTopK;
        log.info("开始向量检索: topK={}, docScope={}", actualTopK, docIds != null ? docIds.size() : "all");
        try {
            List<MilvusSearchRepository.SearchResult> rawResults =
                    milvusSearchRepository.searchSimilar(queryVector, actualTopK, docIds);
            return rawResults.stream()
                    .filter(result -> result.getScore() >= minScore)
                    .map(this::convertToVO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("向量检索失败", e);
            return new ArrayList<>();
        }
    }

    private SimilaritySearchResultVO convertToVO(MilvusSearchRepository.SearchResult result) {
        return SimilaritySearchResultVO.builder()
                .chunkId(result.getChunkId())
                .docId(result.getDocId())
                .content(result.getContent())
                .score(result.getScore())
                .build();
    }
}
