package com.smart.office.task;

import com.smart.office.common.exception.DocumentParseException;
import com.smart.office.model.entity.DocChunk;
import com.smart.office.model.entity.Document;
import com.smart.office.repository.mapper.DocChunkMapper;
import com.smart.office.repository.mapper.DocumentMapper;
import com.smart.office.repository.milvus.MilvusSearchRepository;
import com.smart.office.service.DocumentService;
import com.smart.office.service.ProgressService;
import com.smart.office.util.FileParser;
import com.smart.office.util.HybridTextSplitter;
import com.smart.office.util.HybridTextSplitter.HybridChunk;
import com.smart.office.util.HybridTextSplitter.HybridSplitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文档处理异步任务 - 使用混合分块器
 * 完整的ETL流程：Extract（解析）- Transform（分块、向量化）- Load（存储）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessTask {

    private final ProgressService progressService;
    private final DocumentMapper documentMapper;
    private final DocChunkMapper docChunkMapper;
    private final FileParser fileParser;
    private final HybridTextSplitter hybridTextSplitter;
    private final EmbeddingModel embeddingModel;
    private final MilvusSearchRepository milvusSearchRepository;

    @Value("${async.task.document.batch-size:50}")
    private int batchSize;

    @Value("${async.task.document.max-tokens:500}")
    private int maxTokens;

    @Value("${async.task.document.target-tokens:400}")
    private int targetTokens;

    /**
     * 处理文档（完整ETL流程）
     */
    @Transactional(rollbackFor = Exception.class)
    public void processDocument(Long documentId, MultipartFile file) {
        log.info("========== 开始ETL流程（混合分块） ==========");
        log.info("文档ID: {}, 文件名: {}", documentId, file.getOriginalFilename());

        LocalDateTime startTime = LocalDateTime.now();
        AtomicInteger progress = new AtomicInteger(0);

        try {
            // 1. 更新状态为处理中
            updateProgress(documentId, progress, 5, "开始处理文档");

            // 2. EXTRACT: 解析文档
            log.info("【EXTRACT】开始解析文档...");
            FileParser.ParseResult parseResult = extractDocument(file);
            updateProgress(documentId, progress, 20, "文档解析完成");

            // 3. TRANSFORM: 混合分块（语义 + Token控制）
            log.info("【TRANSFORM】开始混合分块...");
            List<HybridChunk> chunks = transformChunks(parseResult);
            updateProgress(documentId, progress, 40, "混合分块完成");

            // 4. TRANSFORM: 生成向量
            log.info("【TRANSFORM】开始生成向量...");
            List<MilvusSearchRepository.ChunkData> vectorData = generateVectors(documentId, chunks);
            updateProgress(documentId, progress, 60, "向量生成完成");

            // 5. LOAD: 保存到Milvus
            log.info("【LOAD】开始保存向量到Milvus...");
            boolean milvusSuccess = loadToMilvus(vectorData);
            if (!milvusSuccess) {
                throw new RuntimeException("Milvus存储失败");
            }
            updateProgress(documentId, progress, 80, "向量存储完成");

            // 6. LOAD: 保存元数据到MySQL
            log.info("【LOAD】开始保存元数据到MySQL...");
            loadToMySQL(documentId, chunks, vectorData);
            updateProgress(documentId, progress, 95, "元数据保存完成");

            // 7. 完成处理
            LocalDateTime endTime = LocalDateTime.now();
            int processTime = (int) Duration.between(startTime, endTime).getSeconds();

            // 更新文档状态为完成
            progressService.updateProcessResult(documentId, 1, chunks.size(), processTime);

            log.info("========== ETL流程完成 ==========");
            log.info("文档ID: {}, 总块数: {}, 耗时: {}秒", documentId, chunks.size(), processTime);

            progressService.updateProgress(documentId, 100);

        } catch (DocumentParseException e) {
            log.error("文档解析失败: {}", documentId, e);
            handleError(documentId, "文档解析失败: " + e.getMessage());

        } catch (Exception e) {
            log.error("文档处理异常: {}", documentId, e);
            handleError(documentId, "系统异常: " + e.getMessage());
        }
    }

    /**
     * EXTRACT: 解析文档
     */
    private FileParser.ParseResult extractDocument(MultipartFile file) throws DocumentParseException {
        log.info("使用FileParser解析文档: {}", file.getOriginalFilename());

        FileParser.ParseConfig config = new FileParser.ParseConfig();
        config.setExtractMetadata(true);
        config.setDetectLanguage(true);
        config.setMaxContentLength(-1); // 无限制

        FileParser.ParseResult result = fileParser.parse(file, config);

        log.info("文档解析完成: 内容长度={}字符, 语言={}, 元数据={}",
                result.getContent().length(),
                result.getLanguage(),
                result.getMetadata().size());

        return result;
    }

    /**
     * EXTRACT: 从文件路径解析文档
     */
    private FileParser.ParseResult extractDocumentFromPath(String filePath) throws DocumentParseException {
        log.info("使用FileParser解析文档: {}", filePath);

        FileParser.ParseConfig config = new FileParser.ParseConfig();
        config.setExtractMetadata(true);
        config.setDetectLanguage(true);
        config.setMaxContentLength(-1); // 无限制

        FileParser.ParseResult result = fileParser.parse(filePath, config);

        log.info("文档解析完成: 内容长度={}字符, 语言={}, 元数据={}",
                result.getContent().length(),
                result.getLanguage(),
                result.getMetadata().size());

        return result;
    }

    /**
     * TRANSFORM: 混合分块
     */
    private List<HybridChunk> transformChunks(FileParser.ParseResult parseResult) {
        String content = parseResult.getContent();
        String language = parseResult.getLanguage();

        log.info("开始混合分块: 语言={}, 内容长度={}", language, content.length());

        // 根据语言选择配置
        HybridSplitConfig config;
        if ("zh".equals(language)) {
            config = HybridSplitConfig.forChineseDocument();
            log.info("使用中文文档配置: maxTokens={}", config.getMaxTokens());
        } else {
            config = HybridSplitConfig.forEnglishDocument();
            log.info("使用英文文档配置: maxTokens={}", config.getMaxTokens());
        }

        // 覆盖默认配置
        config.setMaxTokens(maxTokens);
        config.setTargetTokens(targetTokens);

        // 执行混合分块
        List<HybridChunk> chunks = hybridTextSplitter.split(content, config);

        log.info("混合分块完成: 总块数={}", chunks.size());

        // 统计块大小分布
        int[] tokenStats = chunks.stream()
                .mapToInt(HybridChunk::getEstimatedTokens)
                .sorted()
                .toArray();

        if (tokenStats.length > 0) {
            log.info("Token分布: 最小={}, 最大={}, 平均={}, 中位数={}",
                    tokenStats[0],
                    tokenStats[tokenStats.length - 1],
                    (int) chunks.stream().mapToInt(HybridChunk::getEstimatedTokens).average().orElse(0),
                    tokenStats[tokenStats.length / 2]);
        }

        // 统计分块方法
        long semanticCount = chunks.stream()
                .filter(c -> c.getSplitMethod() == HybridChunk.SplitMethod.SEMANTIC)
                .count();
        long tokenCount = chunks.stream()
                .filter(c -> c.getSplitMethod() == HybridChunk.SplitMethod.TOKEN)
                .count();
        long hybridCount = chunks.stream()
                .filter(c -> c.getSplitMethod() == HybridChunk.SplitMethod.HYBRID)
                .count();

        log.info("分块方法: 语义={}, Token={}, 混合={}", semanticCount, tokenCount, hybridCount);

        return chunks;
    }

    /**
     * TRANSFORM: 生成向量
     */
    private List<MilvusSearchRepository.ChunkData> generateVectors(
            Long documentId, List<HybridChunk> chunks) {

        log.info("开始生成向量: 文档ID={}, 块数={}", documentId, chunks.size());

        List<MilvusSearchRepository.ChunkData> vectorDataList = new ArrayList<>();

        // 分批处理，避免内存溢出
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<HybridChunk> batch = chunks.subList(i, end);

            log.info("处理批次 {}-{}/{}", i, end, chunks.size());

            for (HybridChunk chunk : batch) {
                try {
                    // 生成向量
                    String content = chunk.getContent();
                    float[] embed = embeddingModel.embed(content);

                    // 转换为Float
                    List<Float> vectorFloat = new ArrayList<>();
                    for (float d : embed) {
                        vectorFloat.add(d);
                    }

                    // 创建向量数据
                    MilvusSearchRepository.ChunkData vectorData = new MilvusSearchRepository.ChunkData();
                    vectorData.setId(UUID.randomUUID().toString());
                    vectorData.setDocId(documentId);
                    vectorData.setContent(content);
                    vectorData.setVector(vectorFloat);

                    // 保存映射关系
                    chunk.setMilvusId(vectorData.getId());

                    vectorDataList.add(vectorData);

                } catch (Exception e) {
                    log.error("生成向量失败: 块索引={}, 方法={}",
                            chunk.getIndex(), chunk.getSplitMethod(), e);
                    throw new RuntimeException("向量生成失败", e);
                }
            }
        }

        log.info("向量生成完成: 总数={}", vectorDataList.size());
        return vectorDataList;
    }

    /**
     * LOAD: 保存到Milvus
     */
    private boolean loadToMilvus(List<MilvusSearchRepository.ChunkData> vectorData) {
        log.info("开始保存到Milvus: 总数={}", vectorData.size());

        long startTime = System.currentTimeMillis();

        // 分批插入
        int successCount = 0;
        for (int i = 0; i < vectorData.size(); i += batchSize) {
            int end = Math.min(i + batchSize, vectorData.size());
            List<MilvusSearchRepository.ChunkData> batch = vectorData.subList(i, end);

            int inserted = milvusSearchRepository.batchInsertChunks(batch);
            successCount += inserted;

            log.debug("Milvus批次插入: {}-{}/{} 成功={}",
                    i, end, vectorData.size(), inserted);
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("Milvus存储完成: 成功={}/{}, 耗时={}ms",
                successCount, vectorData.size(), cost);

        return successCount == vectorData.size();
    }

    /**
     * LOAD: 保存到MySQL
     */
    @Transactional(rollbackFor = Exception.class)
    public void loadToMySQL(Long documentId,
                            List<HybridChunk> chunks,
                            List<MilvusSearchRepository.ChunkData> vectorData) {

        log.info("开始保存元数据到MySQL: 文档ID={}, 块数={}", documentId, chunks.size());

        List<DocChunk> docChunks = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            HybridChunk chunk = chunks.get(i);
            MilvusSearchRepository.ChunkData vector = vectorData.get(i);

            DocChunk docChunk = new DocChunk();
            docChunk.setDocId(documentId);
            docChunk.setChunkIndex(chunk.getIndex());
            docChunk.setMilvusId(vector.getId());
            docChunk.setContent(chunk.getContent());
            docChunk.setContentLength(chunk.getContent().length());
            docChunk.setStartPosition(chunk.getStartPosition());
            docChunk.setEndPosition(chunk.getEndPosition());
            docChunk.setHeading(chunk.getHeading());
            docChunk.setPageNumber(chunk.getPageNumber());

            docChunks.add(docChunk);
        }

        // 批量插入
        long startTime = System.currentTimeMillis();
        int inserted = docChunkMapper.batchInsert(docChunks);
        long cost = System.currentTimeMillis() - startTime;

        log.info("MySQL存储完成: 成功={}/{}, 耗时={}ms", inserted, docChunks.size(), cost);

        if (inserted != docChunks.size()) {
            throw new RuntimeException("MySQL批量插入失败");
        }

        // 可选：保存分块统计信息到文档表
        saveChunkStatistics(documentId, chunks);
    }

    /**
     * 保存分块统计信息
     */
    private void saveChunkStatistics(Long documentId, List<HybridChunk> chunks) {
        // 统计各种分块方法的使用情况
        long semanticCount = chunks.stream()
                .filter(c -> c.getSplitMethod() == HybridChunk.SplitMethod.SEMANTIC)
                .count();
        long tokenCount = chunks.stream()
                .filter(c -> c.getSplitMethod() == HybridChunk.SplitMethod.TOKEN)
                .count();
        long hybridCount = chunks.stream()
                .filter(c -> c.getSplitMethod() == HybridChunk.SplitMethod.HYBRID)
                .count();

        double avgTokens = chunks.stream()
                .mapToInt(HybridChunk::getEstimatedTokens)
                .average()
                .orElse(0);

        log.info("文档分块统计 - 语义:{}, Token:{}, 混合:{}, 平均Token:{:.2f}",
                semanticCount, tokenCount, hybridCount, avgTokens);

        // 可以将统计信息保存到文档表的扩展字段
        // documentMapper.updateStatistics(documentId, statisticsJson);
    }

    /**
     * 更新进度
     */
    private void updateProgress(Long documentId, AtomicInteger progress,
                                int targetProgress, String message) {
        int current = progress.get();
        while (current < targetProgress) {
            if (progress.compareAndSet(current, targetProgress)) {
                progressService.updateProgress(documentId, targetProgress);
                log.info("处理进度: {}% - {}", targetProgress, message);
                break;
            }
            current = progress.get();
        }
    }

    /**
     * 处理错误
     */
    private void handleError(Long documentId, String errorMessage) {
        progressService.updateDocumentStatus(documentId, 2, errorMessage);
        progressService.updateProgress(documentId, 0);
        log.error("文档处理失败: id={}, 错误={}", documentId, errorMessage);
    }

    /**
     * 异步处理文档
     */
    public CompletableFuture<Void> processDocumentAsync(Long documentId, MultipartFile file) {
        return CompletableFuture.runAsync(() -> processDocument(documentId, file))
                .exceptionally(throwable -> {
                    log.error("异步处理失败: {}", documentId, throwable);
                    handleError(documentId, "异步处理异常: " + throwable.getMessage());
                    return null;
                });
    }

    /**
     * 流式处理文档（使用文件路径，使用FileParser和HybridTextSplitter）
     *
     * @param documentId 文档ID
     * @param filePath   文件路径
     */
    @Transactional(rollbackFor = Exception.class)
    public void processDocumentStreaming(Long documentId, String filePath) {
        log.info("========== 开始流式ETL流程 ==========");
        log.info("文档ID: {}, 文件路径: {}", documentId, filePath);

        LocalDateTime startTime = LocalDateTime.now();
        AtomicInteger progress = new AtomicInteger(0);

        try {
            // 1. 更新状态为处理中
            updateProgress(documentId, progress, 5, "开始处理文档");

            // 2. 检查文件是否存在
            Path filePathObj = Paths.get(filePath);
            if (!Files.exists(filePathObj)) {
                throw new RuntimeException("文件不存在: " + filePath);
            }

            // 3. EXTRACT: 使用FileParser解析文档
            log.info("【EXTRACT】开始解析文档...");
            FileParser.ParseResult parseResult = extractDocumentFromPath(filePath);
            updateProgress(documentId, progress, 20, "文档解析完成");

            // 4. TRANSFORM: 使用HybridTextSplitter混合分块
            log.info("【TRANSFORM】开始混合分块...");
            List<HybridChunk> chunks = transformChunks(parseResult);
            updateProgress(documentId, progress, 40, "混合分块完成");

            // 5. TRANSFORM: 生成向量
            log.info("【TRANSFORM】开始生成向量...");
            List<MilvusSearchRepository.ChunkData> vectorData = generateVectors(documentId, chunks);
            updateProgress(documentId, progress, 60, "向量生成完成");

            // 6. LOAD: 保存到Milvus
            log.info("【LOAD】开始保存向量到Milvus...");
            boolean milvusSuccess = loadToMilvus(vectorData);
            if (!milvusSuccess) {
                throw new RuntimeException("Milvus存储失败");
            }
            updateProgress(documentId, progress, 80, "向量存储完成");

            // 7. LOAD: 保存元数据到MySQL
            log.info("【LOAD】开始保存元数据到MySQL...");
            loadToMySQL(documentId, chunks, vectorData);
            updateProgress(documentId, progress, 95, "元数据保存完成");

            // 8. 完成处理
            LocalDateTime endTime = LocalDateTime.now();
            int processTime = (int) Duration.between(startTime, endTime).getSeconds();

            // 更新文档状态为完成
            progressService.updateProcessResult(documentId, 1, chunks.size(), processTime);

            log.info("========== 流式ETL流程完成 ==========");
            log.info("文档ID: {}, 总块数: {}, 耗时: {}秒", documentId, chunks.size(), processTime);

            progressService.updateProgress(documentId, 100);

        } catch (DocumentParseException e) {
            log.error("文档解析失败: {}", documentId, e);
            handleError(documentId, "文档解析失败: " + e.getMessage());

        } catch (Exception e) {
            log.error("文档处理异常: {}", documentId, e);
            handleError(documentId, "系统异常: " + e.getMessage());
        }
    }

    /**
     * 流式处理文档（异步版本）
     */
    public CompletableFuture<Void> processDocumentStreamingAsync(Long documentId, String filePath) {
        return CompletableFuture.runAsync(() -> processDocumentStreaming(documentId, filePath))
                .exceptionally(throwable -> {
                    log.error("异步流式处理失败: {}", documentId, throwable);
                    handleError(documentId, "异步处理异常: " + throwable.getMessage());
                    return null;
                });
    }

    /**
     * 批量保存DocChunk到MySQL
     */
    @Transactional(rollbackFor = Exception.class)
    protected void saveDocChunksBatch(List<DocChunk> docChunks) {
        if (docChunks.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int batchSize = 200;
        int totalInserted = 0;

        for (int i = 0; i < docChunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, docChunks.size());
            List<DocChunk> batch = docChunks.subList(i, end);

            int inserted = docChunkMapper.batchInsert(batch);
            totalInserted += inserted;

            log.debug("MySQL批次插入: {}-{}/{} 成功={}",
                    i, end, docChunks.size(), inserted);
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("MySQL存储完成: 成功={}/{}, 耗时={}ms",
                totalInserted, docChunks.size(), cost);

        if (totalInserted != docChunks.size()) {
            throw new RuntimeException("MySQL批量插入失败");
        }
    }
}