package com.smart.office.base.data.repository;

import com.alibaba.fastjson.JSONObject;
import com.smart.office.base.vector.MilvusClientWrapper;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Milvus 向量检索仓库
 * 封装向量插入、检索、删除等操作
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MilvusSearchRepository {

    private final MilvusClientWrapper milvusClientWrapper;

    @Value("${milvus.collection.name:document_chunks}")
    private String collectionName;

    @Value("${milvus.search.default-top-k:5}")
    private int defaultTopK;

    @Value("${milvus.search.params.nprobe:16}")
    private int nprobe;

    @Value("${milvus.search.metric-type:COSINE}")
    private String metricType;

    /**
     * 插入单个文档块
     */
    public boolean insertChunk(ChunkData chunkData) {
        try {
            List<InsertParam.Field> fields = Arrays.asList(
                    new InsertParam.Field("id", Collections.singletonList(chunkData.getId())),
                    new InsertParam.Field("vector", Collections.singletonList(chunkData.getVector())),
                    new InsertParam.Field("doc_id", Collections.singletonList(chunkData.getDocId())),
                    new InsertParam.Field("content", Collections.singletonList(chunkData.getContent()))
            );

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();

            R<MutationResult> response =
                    milvusClientWrapper.getClient().insert(insertParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                log.debug("插入块成功: id={}, docId={}", chunkData.getId(), chunkData.getDocId());
                return true;
            } else {
                log.error("插入块失败: {}", response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("插入块异常", e);
            return false;
        }
    }

    /**
     * 批量插入文档块
     */
    public int batchInsertChunks(List<ChunkData> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }

        try {
            List<String> ids = new ArrayList<>();
            List<List<Float>> vectors = new ArrayList<>();
            List<Long> docIds = new ArrayList<>();
            List<String> contents = new ArrayList<>();

            for (ChunkData chunk : chunks) {
                ids.add(chunk.getId());
                vectors.add(chunk.getVector());
                docIds.add(chunk.getDocId());
                contents.add(chunk.getContent());
            }

            List<InsertParam.Field> fields = Arrays.asList(
                    new InsertParam.Field("id", ids),
                    new InsertParam.Field("vector", vectors),
                    new InsertParam.Field("doc_id", docIds),
                    new InsertParam.Field("content", contents)
            );

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();

            R<io.milvus.grpc.MutationResult> response =
                    milvusClientWrapper.getClient().insert(insertParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                int succCount = response.getData().getSuccIndexCount();
                int failCount = response.getData().getErrIndexCount();
                log.info("批量插入成功: {} 个块, 失败: {} 个块", succCount, failCount);
                return succCount;
            } else {
                log.error("批量插入失败: {}", response.getMessage());
                return 0;
            }
        } catch (Exception e) {
            log.error("批量插入异常", e);
            return 0;
        }
    }

    /**
     * 向量相似度检索
     */
    public List<SearchResult> searchSimilar(List<Float> queryVector, int topK, List<Long> docIds) {
        return searchSimilar(queryVector, topK, docIds, null);
    }

    /**
     * 向量相似度检索（带额外过滤条件）
     */
    public List<SearchResult> searchSimilar(List<Float> queryVector, int topK,
                                            List<Long> docIds, Map<String, Object> extraFilters) {
        long startTime = System.currentTimeMillis();

        try {
            // 构建查询向量列表
            List<List<Float>> queryVectors = Collections.singletonList(queryVector);

            // 构建输出字段
            List<String> outFields = Arrays.asList("id", "doc_id", "content");

            // 构建过滤表达式
            String expr = buildFilterExpr(docIds, extraFilters);

            // 构建搜索参数
            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("nprobe", nprobe);
            searchParams.put("metric_type", metricType);
            String searchParamsJsonStr = new JSONObject(searchParams).toJSONString();

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withVectors(queryVectors)
                    .withVectorFieldName("vector")
                    .withTopK(topK)
                    .withParams(searchParamsJsonStr)
                    .withExpr(expr)
                    .withOutFields(outFields)
                    .build();

            R<SearchResults> response = milvusClientWrapper.getClient().search(searchParam);

            if (response.getStatus() != R.Status.Success.getCode()) {
                log.error("向量检索失败: {}", response.getMessage());
                return Collections.emptyList();
            }

            // 解析结果
            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            List<SearchResult> results = parseSearchResults(wrapper);

            long cost = System.currentTimeMillis() - startTime;
            log.debug("向量检索完成: 耗时={}ms, 结果数={}, 过滤条件={}",
                    cost, results.size(), expr);

            return results;

        } catch (Exception e) {
            log.error("向量检索异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据文档ID查询所有块
     */
    public List<ChunkInfo> queryByDocId(Long docId) {
        try {
            String expr = "doc_id == " + docId;

            QueryParam queryParam = QueryParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .withOutFields(Arrays.asList("id", "content"))
                    .withLimit(10000L)  // 假设每个文档最多10000个块
                    .build();

            R<io.milvus.grpc.QueryResults> response =
                    milvusClientWrapper.getClient().query(queryParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                QueryResultsWrapper wrapper = new QueryResultsWrapper(response.getData());
                List<ChunkInfo> results = new ArrayList<>();

                // 获取所有字段数据
                List<?> ids = wrapper.getFieldWrapper("id").getFieldData();
                List<?> contents = wrapper.getFieldWrapper("content").getFieldData();

                for (int i = 0; i < ids.size(); i++) {
                    ChunkInfo info = new ChunkInfo();
                    info.setId(ids.get(i).toString());
                    info.setDocId(docId);
                    info.setContent(contents.get(i).toString());
                    results.add(info);
                }

                log.debug("查询文档块成功: docId={}, 数量={}", docId, results.size());
                return results;
            } else {
                log.error("查询文档块失败: {}", response.getMessage());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("查询文档块异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据文档ID删除向量
     */
    public boolean deleteByDocId(Long docId) {
        try {
            String expr = "doc_id == " + docId;

            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build();

            R<io.milvus.grpc.MutationResult> response =
                    milvusClientWrapper.getClient().delete(deleteParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("删除文档块成功: docId={}, 删除数量={}",
                        docId, response.getData().getDeleteCnt());
                return true;
            } else {
                log.error("删除文档块失败: {}", response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("删除文档块异常", e);
            return false;
        }
    }

    /**
     * 根据块ID删除向量
     */
    public boolean deleteByChunkId(String chunkId) {
        try {
            String expr = "id == '" + chunkId + "'";

            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build();

            R<io.milvus.grpc.MutationResult> response =
                    milvusClientWrapper.getClient().delete(deleteParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                log.info("删除块成功: chunkId={}", chunkId);
                return true;
            } else {
                log.error("删除块失败: {}", response.getMessage());
                return false;
            }
        } catch (Exception e) {
            log.error("删除块异常", e);
            return false;
        }
    }

    /**
     * 批量删除向量
     */
    public int batchDelete(List<String> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return 0;
        }

        try {
            // 构建 id in ['id1','id2'] 表达式
            String idsStr = chunkIds.stream()
                    .map(id -> "'" + id + "'")
                    .collect(Collectors.joining(", "));
            String expr = "id in [" + idsStr + "]";

            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build();

            R<io.milvus.grpc.MutationResult> response =
                    milvusClientWrapper.getClient().delete(deleteParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                int succCount = response.getData().getSuccIndexCount();
                int failCount = response.getData().getErrIndexCount();
                log.info("批量删除成功: {} 个块, 失败: {} 个块", succCount, failCount);
                return succCount;
            } else {
                log.error("批量删除失败: {}", response.getMessage());
                return 0;
            }
        } catch (Exception e) {
            log.error("批量删除异常", e);
            return 0;
        }
    }

    /**
     * 统计文档块数量
     */
    public long countByDocId(Long docId) {
        try {
            String expr = "doc_id == " + docId;

            QueryParam queryParam = QueryParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .withOutFields(Collections.singletonList("id"))
                    .build();

            R<io.milvus.grpc.QueryResults> response =
                    milvusClientWrapper.getClient().query(queryParam);

            if (response.getStatus() == R.Status.Success.getCode()) {
                return response.getData().getFieldsDataCount() > 0 ?
                        response.getData().getFieldsData(0).getScalars().getSerializedSize() : 0;
            }
        } catch (Exception e) {
            log.error("统计文档块数量异常", e);
        }
        return 0;
    }

    /**
     * 构建过滤表达式
     */
    private String buildFilterExpr(List<Long> docIds, Map<String, Object> extraFilters) {
        List<String> conditions = new ArrayList<>();

        // 文档ID过滤
        if (docIds != null && !docIds.isEmpty()) {
            String docIdsStr = docIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            conditions.add("doc_id in [" + docIdsStr + "]");
        }

        // 额外过滤条件
        if (extraFilters != null && !extraFilters.isEmpty()) {
            for (Map.Entry<String, Object> entry : extraFilters.entrySet()) {
                if (entry.getValue() instanceof String) {
                    conditions.add(entry.getKey() + " == '" + entry.getValue() + "'");
                } else {
                    conditions.add(entry.getKey() + " == " + entry.getValue());
                }
            }
        }

        return conditions.isEmpty() ? "" : String.join(" and ", conditions);
    }

    /**
     * 解析搜索结果
     */
    private List<SearchResult> parseSearchResults(SearchResultsWrapper wrapper) {
        List<SearchResult> results = new ArrayList<>();

        // 获取第一个查询向量的结果
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

        for (int i = 0; i < idScores.size(); i++) {
            SearchResultsWrapper.IDScore idScore = idScores.get(i);
            SearchResult result = new SearchResult();
            result.setScore((double) idScore.getScore());
            result.setChunkId(idScore.getStrID());

            try {
                Object docIdObj = idScore.getFieldValues().get("doc_id");
                Object contentObj = idScore.getFieldValues().get("content");

                // 获取其他字段
//                Object docIdObj = wrapper.getFieldData("doc_id", i);
//                Object contentObj = wrapper.getFieldData("content", i);

                if (docIdObj != null) {
                    result.setDocId(Long.parseLong(docIdObj.toString()));
                }
                if (contentObj != null) {
                    result.setContent(contentObj.toString());
                }

                results.add(result);
            } catch (Exception e) {
                log.warn("解析搜索结果失败：{}", e.getMessage());
            }
        }

        return results;
    }

    // ==================== 内部数据类 ====================

    /**
     * 块数据类（用于插入）
     */
    @Data
    public static class ChunkData {
        private String id;              // 块ID（UUID）
        private List<Float> vector;      // 向量
        private Long docId;              // 文档ID
        private String content;          // 内容
    }

    /**
     * 搜索结果类
     */
    @Data
    public static class SearchResult {
        private String chunkId;          // 块ID
        private Long docId;              // 文档ID
        private String content;          // 内容
        private Double score;            // 相似度得分
    }

    /**
     * 块信息类（用于查询）
     */
    @Data
    public static class ChunkInfo {
        private String id;               // 块ID
        private Long docId;               // 文档ID
        private String content;           // 内容
    }
}
