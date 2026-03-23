package com.smart.office.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能文本分块工具
 * 优先支持中文文档，兼顾英文
 */
@Slf4j
@Component
public class TextSplitter {

    /**
     * 分块配置
     */
    @Data
    public static class SplitConfig {
        private int chunkSize = 500;           // 目标块大小（字符数）
        private int chunkOverlap = 100;         // 块重叠大小
        private boolean respectParagraph = true; // 是否尊重段落边界
        private boolean respectSentence = true;  // 是否尊重句子边界
        private boolean respectHeading = true;   // 是否尊重标题边界
        private int minChunkSize = 100;          // 最小块大小
        private int maxChunkSize = 1000;         // 最大块大小

        public static SplitConfig defaultConfig() {
            return new SplitConfig();
        }
    }

    /**
     * 分块结果
     */
    @Data
    public static class ChunkResult {
        private String content;          // 块内容
        private int index;               // 块索引
        private int startPosition;        // 起始位置
        private int endPosition;          // 结束位置
        private String heading;           // 所属标题
        private int pageNumber;           // 页码（如果有）
    }

    // 中文句子结束符
    private static final Pattern CHINESE_SENTENCE_END = Pattern.compile("[。！？；]");

    // 英文句子结束符
    private static final Pattern ENGLISH_SENTENCE_END = Pattern.compile("[.!?;]");

    // 中文标题模式（如：第一章、第1节、1.2、一、）
    private static final Pattern CHINESE_HEADING = Pattern.compile(
            "^(第[一二三四五六七八九十百千万0-9]+[章节篇条])|" +  // 第一章、第1节
                    "^([一二三四五六七八九十]+[、．.])|" +                // 一、 二．
                    "^(\\d+\\.\\d+\\s)|" +                              // 1.2
                    "^(\\d+[、．.]\\s)"                                   // 1、 1．
    );

    // 英文标题模式
    private static final Pattern ENGLISH_HEADING = Pattern.compile(
            "^(Chapter|Section|Part)\\s+\\d+|" +                // Chapter 1
                    "^(\\d+\\.\\d+\\s)|" +                               // 1.2
                    "^(\\d+[.)]\\s)"                                      // 1. 1)
    );

    /**
     * 智能分块（默认配置）
     */
    public List<ChunkResult> split(String text) {
        return split(text, SplitConfig.defaultConfig());
    }

    /**
     * 智能分块（自定义配置）
     */
    public List<ChunkResult> split(String text, SplitConfig config) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        log.info("开始智能分块: 文本长度={}, 配置={}", text.length(), config);
        long startTime = System.currentTimeMillis();

        List<ChunkResult> chunks = new ArrayList<>();

        // 1. 预处理：规范化文本
        String normalizedText = normalizeText(text);

        // 2. 检测文本语言（中英文比例）
        boolean isChinesePreferred = detectLanguage(text);

        // 3. 提取标题结构
        List<HeadingInfo> headings = extractHeadings(normalizedText, isChinesePreferred);

        // 4. 根据标题划分主要章节
        List<Section> sections = splitByHeadings(normalizedText, headings);

        // 5. 对每个章节进行细粒度分块
        for (Section section : sections) {
            List<ChunkResult> sectionChunks = splitSection(section, config, isChinesePreferred);
            chunks.addAll(sectionChunks);
        }

        // 6. 如果没有分块成功，使用基础分块
        if (chunks.isEmpty()) {
            log.warn("智能分块失败，使用基础分块方案");
            chunks = basicSplit(normalizedText, config);
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("智能分块完成: 块数={}, 耗时={}ms", chunks.size(), cost);

        return chunks;
    }

    /**
     * 基础分块方案（按固定长度分割）
     */
    private List<ChunkResult> basicSplit(String text, SplitConfig config) {
        List<ChunkResult> chunks = new ArrayList<>();
        int length = text.length();
        int chunkSize = config.getChunkSize();
        int overlap = config.getChunkOverlap();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + chunkSize, length);

            // 调整结束位置到合适的边界
            if (end < length) {
                end = findBreakPoint(text, end, true, true);
            }

            ChunkResult chunk = new ChunkResult();
            chunk.setIndex(chunks.size());
            chunk.setContent(text.substring(start, end).trim());
            chunk.setStartPosition(start);
            chunk.setEndPosition(end);

            if (!chunk.getContent().isEmpty()) {
                chunks.add(chunk);
            }

            start = end - overlap;
            if (start < 0) start = 0;
        }

        return chunks;
    }

    /**
     * 分割章节
     */
    private List<ChunkResult> splitSection(Section section, SplitConfig config, boolean isChinese) {
        List<ChunkResult> chunks = new ArrayList<>();
        String text = section.getContent();
        int length = text.length();

        // 如果章节内容小于最小块大小，直接作为一个块
        if (length <= config.getMaxChunkSize()) {
            ChunkResult chunk = new ChunkResult();
            chunk.setIndex(0);
            chunk.setContent(text.trim());
            chunk.setStartPosition(section.getStartPos());
            chunk.setEndPosition(section.getEndPos());
            chunk.setHeading(section.getHeading());
            chunks.add(chunk);
            return chunks;
        }

        // 按段落分割
        List<String> paragraphs = splitParagraphs(text, isChinese);
        List<String> currentChunk = new ArrayList<>();
        int currentLength = 0;
        int chunkStartPos = section.getStartPos();

        for (String para : paragraphs) {
            int paraLength = para.length();

            // 如果单个段落超过最大块大小，需要进一步分割
            if (paraLength > config.getMaxChunkSize()) {
                // 先处理当前累积的块
                if (!currentChunk.isEmpty()) {
                    chunks.add(createChunk(currentChunk, chunkStartPos, section, chunks.size()));
                    currentChunk.clear();
                    currentLength = 0;
                    chunkStartPos = -1;
                }

                // 分割长段落
                List<String> subParas = splitLongParagraph(para, config, isChinese);
                for (String subPara : subParas) {
                    ChunkResult chunk = new ChunkResult();
                    chunk.setIndex(chunks.size());
                    chunk.setContent(subPara.trim());
                    chunk.setStartPosition(chunkStartPos == -1 ? 0 : chunkStartPos);
                    chunk.setEndPosition(chunkStartPos == -1 ? subPara.length() : chunkStartPos + subPara.length());
                    chunk.setHeading(section.getHeading());
                    chunks.add(chunk);
                }
                continue;
            }

            // 如果加入当前段落会超过目标大小，先保存当前块
            if (currentLength + paraLength > config.getChunkSize() && !currentChunk.isEmpty()) {
                chunks.add(createChunk(currentChunk, chunkStartPos, section, chunks.size()));
                currentChunk.clear();
                currentLength = 0;
                chunkStartPos = -1;
            }

            // 记录块的起始位置
            if (currentChunk.isEmpty()) {
                chunkStartPos = section.getStartPos() + text.indexOf(para);
            }

            currentChunk.add(para);
            currentLength += paraLength;
        }

        // 处理最后一个块
        if (!currentChunk.isEmpty()) {
            chunks.add(createChunk(currentChunk, chunkStartPos, section, chunks.size()));
        }

        return chunks;
    }

    /**
     * 创建块结果
     */
    private ChunkResult createChunk(List<String> paragraphs, int startPos, Section section, int index) {
        ChunkResult chunk = new ChunkResult();
        String content = String.join("\n", paragraphs);
        chunk.setIndex(index);
        chunk.setContent(content);
        chunk.setStartPosition(startPos);
        chunk.setEndPosition(startPos + content.length());
        chunk.setHeading(section.getHeading());
        return chunk;
    }

    /**
     * 分割长段落
     */
    private List<String> splitLongParagraph(String paragraph, SplitConfig config, boolean isChinese) {
        List<String> chunks = new ArrayList<>();
        int length = paragraph.length();
        int chunkSize = config.getChunkSize();
        int overlap = config.getChunkOverlap();
        int start = 0;

        // 防止死循环：最大迭代次数限制
        int maxIterations = length / Math.max(1, chunkSize - overlap) + 10;
        int iteration = 0;

        while (start < length && iteration < maxIterations) {
            iteration++;

            int end = Math.min(start + chunkSize, length);

            // 记录原始end位置
            int originalEnd = end;

            // 如果不是最后一块，尝试找到合适的分割点
            if (end < length) {
                int newEnd = findBreakPoint(paragraph, end, isChinese, config.isRespectSentence());

                // 确保分割点有效且向前推进
                if (newEnd > start && newEnd <= length) {
                    end = newEnd;
                } else {
                    // 如果找不到合适的分割点，使用原始end
                    end = originalEnd;
                    // 如果end仍然等于start，强制向前推进至少一个字符
                    if (end <= start) {
                        end = Math.min(start + 1, length);
                    }
                }
            }

            // 确保end大于start，避免无限循环
            if (end <= start) {
                end = Math.min(start + 1, length);
            }

            String chunk = paragraph.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            // 计算下一个起始位置，确保向前推进
            int nextStart = end - overlap;
            if (nextStart <= start) {
                // 如果没有向前推进，强制向前移动至少1个字符
                nextStart = start + 1;
            }
            start = Math.min(nextStart, length);
        }

        // 如果还有剩余内容未处理（理论上不应该发生，但作为安全保护）
        if (start < length) {
            String remaining = paragraph.substring(start).trim();
            if (!remaining.isEmpty()) {
                chunks.add(remaining);
            }
        }

        return chunks;
    }

    /**
     * 分割段落
     */
    private List<String> splitParagraphs(String text, boolean isChinese) {
        List<String> paragraphs = new ArrayList<>();

        // 按空行分割
        String[] parts = text.split("\\n\\s*\\n");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }

        // 如果没有空行分割，按单行分割
        if (paragraphs.size() <= 1) {
            paragraphs.clear();
            String[] lines = text.split("\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    paragraphs.add(trimmed);
                }
            }
        }

        return paragraphs;
    }

    /**
     * 提取标题结构
     */
    private List<HeadingInfo> extractHeadings(String text, boolean isChinese) {
        List<HeadingInfo> headings = new ArrayList<>();
        String[] lines = text.split("\\n");
        Pattern headingPattern = isChinese ? CHINESE_HEADING : ENGLISH_HEADING;

        int position = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (headingPattern.matcher(trimmed).find() && trimmed.length() < 100) {
                HeadingInfo heading = new HeadingInfo();
                heading.setTitle(trimmed);
                heading.setStartPos(position);
                heading.setLevel(detectHeadingLevel(trimmed, isChinese));
                headings.add(heading);
            }
            position += line.length() + 1; // +1 for newline
        }

        return headings;
    }

    /**
     * 检测标题级别
     */
    private int detectHeadingLevel(String heading, boolean isChinese) {
        if (isChinese) {
            if (heading.startsWith("第") && (heading.contains("章") || heading.contains("篇"))) {
                return 1;
            } else if (heading.matches("^[一二三四五六七八九十]+[、．.]")) {
                return 2;
            } else if (heading.matches("^\\d+\\.\\d+")) {
                return 3;
            }
        } else {
            if (heading.matches("^(Chapter|Part|Section)\\s+\\d+")) {
                return 1;
            } else if (heading.matches("^\\d+\\.\\d+")) {
                return 2;
            } else if (heading.matches("^\\d+[.)]\\s")) {
                return 3;
            }
        }
        return 3;
    }

    /**
     * 根据标题划分章节
     */
    private List<Section> splitByHeadings(String text, List<HeadingInfo> headings) {
        List<Section> sections = new ArrayList<>();

        if (headings.isEmpty()) {
            // 没有标题，整个文本作为一个章节
            Section section = new Section();
            section.setContent(text);
            section.setStartPos(0);
            section.setEndPos(text.length());
            section.setHeading("");
            sections.add(section);
            return sections;
        }

        for (int i = 0; i < headings.size(); i++) {
            HeadingInfo current = headings.get(i);
            HeadingInfo next = i < headings.size() - 1 ? headings.get(i + 1) : null;

            Section section = new Section();
            section.setHeading(current.getTitle());
            section.setStartPos(current.getStartPos());

            if (next != null) {
                section.setEndPos(next.getStartPos());
                section.setContent(text.substring(current.getStartPos(), next.getStartPos()));
            } else {
                section.setEndPos(text.length());
                section.setContent(text.substring(current.getStartPos()));
            }

            sections.add(section);
        }

        return sections;
    }

    /**
     * 检测文本语言偏好
     * @return true-中文为主，false-英文为主
     */
    private boolean detectLanguage(String text) {
        if (text == null || text.isEmpty()) {
            return true; // 默认中文
        }

        int chineseCount = 0;
        int totalCount = 0;

        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            }
            totalCount++;
        }

        double chineseRatio = (double) chineseCount / totalCount;
        return chineseRatio > 0.3; // 超过30%中文字符视为中文文档
    }

    /**
     * 规范化文本
     */
    private String normalizeText(String text) {
        // 替换多个连续空格为单个空格
        text = text.replaceAll("\\s+", " ");
        // 替换多个连续换行为双换行（段落标记）
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text;
    }

    /**
     * 找到合适的分割点
     */
    private int findBreakPoint(String text, int targetPos, boolean respectSentence, boolean respectParagraph) {
        int length = text.length();

        // 优先在段落边界分割
        if (respectParagraph) {
            int paraBreak = text.indexOf("\n\n", targetPos - 50);
            if (paraBreak > targetPos - 50 && paraBreak < targetPos + 50) {
                return paraBreak;
            }
        }

        // 然后在句子边界分割
        if (respectSentence) {
            Pattern sentenceEnd = detectLanguage(text) ? CHINESE_SENTENCE_END : ENGLISH_SENTENCE_END;
            Matcher matcher = sentenceEnd.matcher(text);

            int bestBreak = -1;
            int minDiff = Integer.MAX_VALUE;

            while (matcher.find()) {
                int pos = matcher.start() + 1;
                if (Math.abs(pos - targetPos) < minDiff && Math.abs(pos - targetPos) < 100) {
                    minDiff = Math.abs(pos - targetPos);
                    bestBreak = pos;
                }
            }

            if (bestBreak != -1) {
                return bestBreak;
            }
        }

        // 最后在空格边界分割
        int spaceBreak = text.indexOf(" ", targetPos - 30);
        if (spaceBreak != -1 && spaceBreak < targetPos + 30) {
            return spaceBreak;
        }

        return targetPos;
    }

    /**
     * 标题信息内部类
     */
    @Data
    private static class HeadingInfo {
        private String title;
        private int startPos;
        private int level;
    }

    /**
     * 章节信息内部类
     */
    @Data
    private static class Section {
        private String content;
        private int startPos;
        private int endPos;
        private String heading;
    }
}
