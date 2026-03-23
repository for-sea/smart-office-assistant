package com.smart.office.service;

import com.smart.office.model.entity.Document;
import com.smart.office.model.vo.SimilaritySearchResultVO;
import com.smart.office.repository.milvus.MilvusSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量检索服务
 * 封装 Milvus 向量检索逻辑，提供相似度搜索功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final MilvusSearchRepository milvusSearchRepository;

    @Value("${spring.vectorstore.milvus.default-top-k:5}")
    private int defaultTopK;

    @Value("${spring.vectorstore.milvus.min-score:0.5}")
    private double minScore;

    /**
     * 搜索相似文档块
     *
     * @param queryVector 查询向量
     * @param topK 返回结果数量
     * @param docIds 可访问的文档ID列表（用于权限过滤）
     * @return 相似文本块列表
     */
    public List<SimilaritySearchResultVO> searchSimilarChunks(
            List<Float> queryVector, int topK, List<Long> docIds) {

        log.info("开始向量检索: topK={}, docIds={}", topK, docIds != null ? docIds.size() : "all");
        long startTime = System.currentTimeMillis();

        try {
            // 调用 Milvus 检索
            List<MilvusSearchRepository.SearchResult> searchResults =
                    milvusSearchRepository.searchSimilar(queryVector, topK, docIds);

            // 转换为 VO 并过滤低于阈值的结果
            List<SimilaritySearchResultVO> results = searchResults.stream()
                    .filter(result -> result.getScore() >= minScore)
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            long cost = System.currentTimeMillis() - startTime;
            log.info("向量检索完成: 返回={}, 原始={}, 耗时={}ms",
                    results.size(), searchResults.size(), cost);

            return results;

        } catch (Exception e) {
            log.error("向量检索失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 搜索相似文档块（使用默认 topK）
     */
    public List<SimilaritySearchResultVO> searchSimilarChunks(
            List<Float> queryVector, List<Long> docIds) {
        return searchSimilarChunks(queryVector, defaultTopK, docIds);
    }

    /**
     * 搜索相似文档块（带额外过滤条件）
     *
     * @param queryVector 查询向量
     * @param topK 返回结果数量
     * @param docIds 可访问的文档ID列表
     * @param extraFilters 额外过滤条件（如：status, category 等）
     * @return 相似文本块列表
     */
    public List<SimilaritySearchResultVO> searchSimilarChunksWithFilters(
            List<Float> queryVector, int topK, List<Long> docIds, Map<String, Object> extraFilters) {

        log.info("开始向量检索（带过滤）: topK={}, docIds={}, filters={}",
                topK, docIds != null ? docIds.size() : "all", extraFilters);
        long startTime = System.currentTimeMillis();

        try {
            List<MilvusSearchRepository.SearchResult> searchResults =
                    milvusSearchRepository.searchSimilar(queryVector, topK, docIds, extraFilters);

            List<SimilaritySearchResultVO> results = searchResults.stream()
                    .filter(result -> result.getScore() >= minScore)
                    .map(this::convertToVO)
                    .collect(Collectors.toList());

            long cost = System.currentTimeMillis() - startTime;
            log.info("向量检索完成（带过滤）: 返回={}, 原始={}, 耗时={}ms",
                    results.size(), searchResults.size(), cost);

            return results;

        } catch (Exception e) {
            log.error("向量检索失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 批量搜索（多个查询向量）
     *
     * @param queryVectors 查询向量列表
     * @param topK 每个查询返回结果数量
     * @param docIds 可访问的文档ID列表
     * @return 每个查询对应的相似文本块列表
     */
    public List<List<SimilaritySearchResultVO>> batchSearchSimilarChunks(
            List<List<Float>> queryVectors, int topK, List<Long> docIds) {

        log.info("开始批量向量检索: 查询数={}, topK={}", queryVectors.size(), topK);
        long startTime = System.currentTimeMillis();

        List<List<SimilaritySearchResultVO>> allResults = new ArrayList<>();

        for (int i = 0; i < queryVectors.size(); i++) {
            List<Float> queryVector = queryVectors.get(i);
            List<SimilaritySearchResultVO> results = searchSimilarChunks(queryVector, topK, docIds);
            allResults.add(results);
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("批量向量检索完成: 查询数={}, 总耗时={}ms", queryVectors.size(), cost);

        return allResults;
    }

    /**
     * 搜索相似文档块并返回详细信息（包含文档名、块内容等）
     *
     * @param queryVector 查询向量
     * @param topK 返回结果数量
     * @param docIds 可访问的文档ID列表
     * @param documentService 文档服务（用于获取文档名称）
     * @return 详细结果列表
     */
    public List<SimilaritySearchResultVO> searchSimilarChunksWithDetails(
            List<Float> queryVector, int topK, List<Long> docIds, DocumentService documentService) {

        List<SimilaritySearchResultVO> results = searchSimilarChunks(queryVector, topK, docIds);

        // 补充文档名称信息
        if (!results.isEmpty() && documentService != null) {
            for (SimilaritySearchResultVO result : results) {
                try {
                    Document document = documentService.getById(result.getDocId());
                    if (document != null) {
                        result.setDocName(document.getFileName());
                        result.setDocType(document.getFileType());
                    }
                } catch (Exception e) {
                    log.warn("获取文档信息失败: docId={}", result.getDocId(), e);
                }
            }
        }

        return results;
    }

    /**
     * 转换 Milvus 搜索结果为 VO
     */
    private SimilaritySearchResultVO convertToVO(MilvusSearchRepository.SearchResult result) {
        return SimilaritySearchResultVO.builder()
                .chunkId(result.getChunkId())
                .docId(result.getDocId())
                .content(result.getContent())
                .score(result.getScore())
                .build();
    }

    /**
     * 获取可访问的文档ID列表（根据用户权限）
     *
     * @param uploader 上传者/用户ID
     * @param documentService 文档服务
     * @return 可访问的文档ID列表
     */
    public List<Long> getAccessibleDocIds(String uploader, DocumentService documentService) {
        try {
            return documentService.getUserDocumentIds(uploader);
        } catch (Exception e) {
            log.warn("获取用户文档列表失败: uploader={}", uploader, e);
            return new ArrayList<>();
        }
    }

    /**
     * 合并检索结果（用于多路召回）
     *
     * @param resultGroups 多路检索结果
     * @param topK 最终返回数量
     * @return 合并后的结果
     */
    public List<SimilaritySearchResultVO> mergeSearchResults(
            List<List<SimilaritySearchResultVO>> resultGroups, int topK) {

        // 使用 Map 去重，保留最高分
        Map<String, SimilaritySearchResultVO> mergedMap = new java.util.HashMap<>();

        for (List<SimilaritySearchResultVO> group : resultGroups) {
            for (SimilaritySearchResultVO result : group) {
                String key = result.getChunkId();
                if (!mergedMap.containsKey(key) ||
                        mergedMap.get(key).getScore() < result.getScore()) {
                    mergedMap.put(key, result);
                }
            }
        }

        // 按分数排序并取 topK
        return mergedMap.values().stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }
}