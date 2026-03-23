package com.smart.office.util;

import com.smart.office.util.HybridTextSplitter.HybridChunk.SplitMethod;
import com.smart.office.util.TextSplitter.ChunkResult;
import com.smart.office.util.TextSplitter.SplitConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 混合文本分块器
 * 结合语义分块和Token分块的优点，保证分块质量的同时控制Token数量
 */
@Slf4j
@Component
public class HybridTextSplitter {

    private final TextSplitter semanticSplitter;
    private final TokenTextSplitter tokenSplitter;

    // 缓存语言检测结果
    private final ConcurrentHashMap<String, Boolean> languageCache = new ConcurrentHashMap<>();

    // Token预估系数（中文1.5字符/token，英文4字符/token）
    private static final double CHINESE_CHARS_PER_TOKEN = 1.5;
    private static final double ENGLISH_CHARS_PER_TOKEN = 4.0;

    // 中文检测阈值
    private static final double CHINESE_THRESHOLD = 0.3;

    // 中文句子结束符
    private static final Pattern CHINESE_SENTENCE_END = Pattern.compile("[。！？；]");
    // 英文句子结束符
    private static final Pattern ENGLISH_SENTENCE_END = Pattern.compile("[.!?;]");

    public HybridTextSplitter() {
        this.semanticSplitter = new TextSplitter();
        this.tokenSplitter = new TokenTextSplitter(500, 100, 100, 100, true);
    }

    /**
     * 混合分块配置
     */
    @Data
    public static class HybridSplitConfig {
        private int maxTokens = 500;           // 最大token数
        private int targetTokens = 400;         // 目标token数
        private int minTokens = 100;            // 最小token数
        private boolean enableSemanticSplit = true;  // 启用语义分块
        private boolean enableTokenControl = true;    // 启用Token控制
        private boolean respectParagraph = true;      // 尊重段落边界
        private boolean respectSentence = true;       // 尊重句子边界
        private boolean respectHeading = true;        // 尊重标题边界

        public static HybridSplitConfig defaultConfig() {
            return new HybridSplitConfig();
        }

        public static HybridSplitConfig forChineseDocument() {
            HybridSplitConfig config = new HybridSplitConfig();
            config.setMaxTokens(600);      // 中文可以稍大
            config.setTargetTokens(450);
            config.setMinTokens(80);
            return config;
        }

        public static HybridSplitConfig forEnglishDocument() {
            HybridSplitConfig config = new HybridSplitConfig();
            config.setMaxTokens(500);
            config.setTargetTokens(400);
            config.setMinTokens(120);
            return config;
        }
    }

    /**
     * 混合分块结果
     */
    @Data
    public static class HybridChunk {
        private String content;           // 块内容
        private int index;                // 块索引
        private int estimatedTokens;       // 预估token数
        private int startPosition;         // 起始位置
        private int endPosition;           // 结束位置
        private String heading;            // 所属标题
        private int pageNumber;            // 页码（如果有）
        private SplitMethod splitMethod;   // 分块方法
        private double confidence;         // 分块置信度
        private String milvusId;           // Milvus存储ID（用于映射）- 新增字段

        public enum SplitMethod {
            SEMANTIC,      // 纯语义分块
            TOKEN,         // Token分块
            HYBRID         // 混合分块
        }
    }

    /**
     * 混合分块（默认配置）
     */
    public List<HybridChunk> split(String text) {
        return split(text, detectLanguageAndGetConfig(text));
    }

    /**
     * 混合分块（自定义配置）
     */
    public List<HybridChunk> split(String text, HybridSplitConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        long startTime = System.currentTimeMillis();
        log.info("开始混合分块: 文本长度={}, 配置={}", text.length(), config);

        List<HybridChunk> result = new ArrayList<>();

        try {
            // 阶段1：语义分块（保持结构完整性）
            List<ChunkResult> semanticChunks = null;
            if (config.isEnableSemanticSplit()) {
                semanticChunks = performSemanticSplit(text, config);
                log.debug("语义分块完成: 块数={}", semanticChunks.size());
            }

            // 阶段2：Token控制与调整
            if (semanticChunks != null && config.isEnableTokenControl()) {
                result = adjustByTokenControl(semanticChunks, config, text);
            } else if (semanticChunks != null) {
                // 仅使用语义分块，不进行Token控制
                result = convertToHybridChunks(semanticChunks, SplitMethod.SEMANTIC);
            } else {
                // 回退到Token分块
                result = fallbackToTokenSplit(text, config);
            }

            // 阶段3：后处理 - 添加块索引和验证
            postProcessChunks(result, text);

        } catch (Exception e) {
            log.error("混合分块失败，回退到Token分块", e);
            result = fallbackToTokenSplit(text, config);
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("混合分块完成: 块数={}, 耗时={}ms", result.size(), cost);

        return result;
    }

    /**
     * 执行语义分块
     */
    private List<ChunkResult> performSemanticSplit(String text, HybridSplitConfig config) {
        SplitConfig semanticConfig = new SplitConfig();
        semanticConfig.setChunkSize((int) (estimateCharsFromTokens(config.getTargetTokens(), text)));
        semanticConfig.setChunkOverlap(100);
        semanticConfig.setRespectParagraph(config.isRespectParagraph());
        semanticConfig.setRespectSentence(config.isRespectSentence());
        semanticConfig.setRespectHeading(config.isRespectHeading());
        semanticConfig.setMinChunkSize((int) estimateCharsFromTokens(config.getMinTokens(), text));
        semanticConfig.setMaxChunkSize((int) estimateCharsFromTokens(config.getMaxTokens() * 2, text));

        return semanticSplitter.split(text, semanticConfig);
    }

    /**
     * 根据Token控制调整分块
     */
    private List<HybridChunk> adjustByTokenControl(List<ChunkResult> semanticChunks,
                                                   HybridSplitConfig config,
                                                   String originalText) {
        List<HybridChunk> result = new ArrayList<>();

        for (ChunkResult semanticChunk : semanticChunks) {
            int estimatedTokens = estimateTokens(semanticChunk.getContent());

            if (estimatedTokens <= config.getMaxTokens()) {
                // 块大小合适，直接使用
                result.add(createHybridChunk(semanticChunk,
                        estimatedTokens,
                        SplitMethod.SEMANTIC,
                        1.0));
            } else {
                // 块过大，需要进一步切分
                List<HybridChunk> subChunks = splitOversizedChunk(semanticChunk, config);
                result.addAll(subChunks);
            }
        }

        // 合并过小的块（可选）
        result = mergeSmallChunks(result, config);

        return result;
    }

    /**
     * 切分过大的块
     */
    private List<HybridChunk> splitOversizedChunk(ChunkResult chunk, HybridSplitConfig config) {
        List<HybridChunk> result = new ArrayList<>();
        String content = chunk.getContent();

        // 尝试按句子边界切分
        List<String> sentences = splitBySentences(content);
        List<String> currentGroup = new ArrayList<>();
        int currentTokens = 0;
        int startPos = chunk.getStartPosition();

        for (String sentence : sentences) {
            int sentenceTokens = estimateTokens(sentence);

            if (currentTokens + sentenceTokens > config.getMaxTokens()) {
                // 保存当前组
                if (!currentGroup.isEmpty()) {
                    String groupContent = String.join("", currentGroup);
                    int groupTokens = estimateTokens(groupContent);
                    int groupStartPos = startPos;
                    int groupEndPos = startPos + groupContent.length();

                    HybridChunk hybridChunk = new HybridChunk();
                    hybridChunk.setContent(groupContent);
                    hybridChunk.setEstimatedTokens(groupTokens);
                    hybridChunk.setStartPosition(groupStartPos);
                    hybridChunk.setEndPosition(groupEndPos);
                    hybridChunk.setHeading(chunk.getHeading());
                    hybridChunk.setSplitMethod(SplitMethod.TOKEN);
                    hybridChunk.setConfidence(0.8);
                    result.add(hybridChunk);

                    currentGroup.clear();
                    currentTokens = 0;
                    startPos = groupEndPos;
                }
            }

            currentGroup.add(sentence);
            currentTokens += sentenceTokens;
        }

        // 处理最后一组
        if (!currentGroup.isEmpty()) {
            String groupContent = String.join("", currentGroup);
            HybridChunk hybridChunk = new HybridChunk();
            hybridChunk.setContent(groupContent);
            hybridChunk.setEstimatedTokens(estimateTokens(groupContent));
            hybridChunk.setStartPosition(startPos);
            hybridChunk.setEndPosition(startPos + groupContent.length());
            hybridChunk.setHeading(chunk.getHeading());
            hybridChunk.setSplitMethod(SplitMethod.TOKEN);
            hybridChunk.setConfidence(0.8);
            result.add(hybridChunk);
        }

        return result;
    }

    /**
     * 合并过小的块
     */
    private List<HybridChunk> mergeSmallChunks(List<HybridChunk> chunks, HybridSplitConfig config) {
        if (chunks.size() <= 1) {
            return chunks;
        }

        List<HybridChunk> merged = new ArrayList<>();
        HybridChunk current = chunks.get(0);

        for (int i = 1; i < chunks.size(); i++) {
            HybridChunk next = chunks.get(i);
            int combinedTokens = current.getEstimatedTokens() + next.getEstimatedTokens();

            if (combinedTokens <= config.getMaxTokens() &&
                    current.getEstimatedTokens() < config.getMinTokens()) {
                // 合并到当前块
                current.setContent(current.getContent() + "\n" + next.getContent());
                current.setEstimatedTokens(combinedTokens);
                current.setEndPosition(next.getEndPosition());
                current.setSplitMethod(SplitMethod.HYBRID);
                current.setConfidence(0.9);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);

        return merged;
    }

    /**
     * 回退到Token分块
     */
    private List<HybridChunk> fallbackToTokenSplit(String text, HybridSplitConfig config) {
        log.warn("使用TokenTextSplitter作为回退方案");

        // 创建Document对象（Spring AI的Document类）
        Document document = new Document(text);

        // 将单个文档包装为List
        List<Document> documents = Collections.singletonList(document);

        // 应用TokenTextSplitter
        List<Document> tokenChunks = tokenSplitter.apply(documents);

        List<HybridChunk> result = new ArrayList<>();
        int position = 0;

        for (int i = 0; i < tokenChunks.size(); i++) {
            Document doc = tokenChunks.get(i);
            String content = doc.getText();
            int tokens = estimateTokens(content);

            HybridChunk chunk = new HybridChunk();
            chunk.setIndex(i);
            chunk.setContent(content);
            chunk.setEstimatedTokens(tokens);
            chunk.setStartPosition(position);
            chunk.setEndPosition(position + content.length());
            chunk.setSplitMethod(SplitMethod.TOKEN);
            chunk.setConfidence(0.7);

            result.add(chunk);
            position += content.length();
        }

        return result;
    }

    /**
     * 按句子分割文本
     */
    private List<String> splitBySentences(String text) {
        List<String> sentences = new ArrayList<>();
        boolean isChinese = isChineseText(text);
        Pattern sentencePattern = isChinese ? CHINESE_SENTENCE_END : ENGLISH_SENTENCE_END;

        String[] parts = sentencePattern.split(text);
        int lastEnd = 0;

        for (String part : parts) {
            int end = lastEnd + part.length();
            if (end < text.length()) {
                // 包含结束符
                sentences.add(part + text.charAt(end));
                lastEnd = end + 1;
            } else {
                sentences.add(part);
            }
        }

        return sentences;
    }

    /**
     * 预估Token数量
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        boolean isChinese = isChineseText(text);
        double charsPerToken = isChinese ? CHINESE_CHARS_PER_TOKEN : ENGLISH_CHARS_PER_TOKEN;

        return (int) Math.ceil(text.length() / charsPerToken);
    }

    /**
     * 根据Token数预估字符数
     */
    private int estimateCharsFromTokens(int tokens, String text) {
        boolean isChinese = isChineseText(text);
        double charsPerToken = isChinese ? CHINESE_CHARS_PER_TOKEN : ENGLISH_CHARS_PER_TOKEN;

        return (int) (tokens * charsPerToken);
    }

    /**
     * 检测文本是否为中文
     */
    private boolean isChineseText(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }

        // 检查缓存
        String cacheKey = text.length() > 100 ? text.substring(0, 100) : text;
        return languageCache.computeIfAbsent(cacheKey, key -> {
            int chineseCount = 0;
            int sampleSize = Math.min(text.length(), 500);

            for (int i = 0; i < sampleSize; i++) {
                char c = text.charAt(i);
                if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                    chineseCount++;
                }
            }

            return (double) chineseCount / sampleSize > CHINESE_THRESHOLD;
        });
    }

    /**
     * 检测语言并获取对应配置
     */
    private HybridSplitConfig detectLanguageAndGetConfig(String text) {
        boolean isChinese = isChineseText(text);
        return isChinese ? HybridSplitConfig.forChineseDocument() : HybridSplitConfig.forEnglishDocument();
    }

    /**
     * 转换语义分块为混合分块
     */
    private List<HybridChunk> convertToHybridChunks(List<ChunkResult> semanticChunks,
                                                    SplitMethod method) {
        List<HybridChunk> result = new ArrayList<>();
        for (ChunkResult chunk : semanticChunks) {
            result.add(createHybridChunk(chunk,
                    estimateTokens(chunk.getContent()),
                    method,
                    0.9));
        }
        return result;
    }

    /**
     * 创建混合分块对象
     */
    private HybridChunk createHybridChunk(ChunkResult chunk, int tokens,
                                          SplitMethod method, double confidence) {
        HybridChunk hybridChunk = new HybridChunk();
        hybridChunk.setContent(chunk.getContent());
        hybridChunk.setEstimatedTokens(tokens);
        hybridChunk.setStartPosition(chunk.getStartPosition());
        hybridChunk.setEndPosition(chunk.getEndPosition());
        hybridChunk.setHeading(chunk.getHeading());
        hybridChunk.setPageNumber(chunk.getPageNumber());
        hybridChunk.setSplitMethod(method);
        hybridChunk.setConfidence(confidence);
        return hybridChunk;
    }

    /**
     * 后处理：添加索引和验证
     */
    private void postProcessChunks(List<HybridChunk> chunks, String originalText) {
        for (int i = 0; i < chunks.size(); i++) {
            HybridChunk chunk = chunks.get(i);
            chunk.setIndex(i);

            // 验证内容不为空
            if (chunk.getContent() == null || chunk.getContent().isEmpty()) {
                log.warn("发现空块: index={}", i);
                chunk.setContent(" ");
            }

            // 验证Token数
            if (chunk.getEstimatedTokens() > 800) {
                log.warn("块Token数过大: index={}, tokens={}",
                        i, chunk.getEstimatedTokens());
            }
        }

        // 统计分块方法分布
        long semanticCount = chunks.stream()
                .filter(c -> c.getSplitMethod() == SplitMethod.SEMANTIC)
                .count();
        long tokenCount = chunks.stream()
                .filter(c -> c.getSplitMethod() == SplitMethod.TOKEN)
                .count();
        long hybridCount = chunks.stream()
                .filter(c -> c.getSplitMethod() == SplitMethod.HYBRID)
                .count();

        log.info("分块方法统计: 语义={}, Token={}, 混合={}, 总计={}",
                semanticCount, tokenCount, hybridCount, chunks.size());
    }
}
