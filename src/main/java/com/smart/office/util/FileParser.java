package com.smart.office.util;

import com.smart.office.common.exception.DocumentParseException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档解析工具类
 * 基于 Spring AI TikaDocumentReader 实现多格式文档解析
 * 支持格式：PDF、Word、Excel、PowerPoint、HTML、Markdown、TXT等
 *
 * @author office-assistant
 * @version 1.0
 */
@Slf4j
@Component
public class FileParser {

    /**
     * 支持的文档格式
     */
    public enum DocumentType {
        PDF("application/pdf", ".pdf"),
        WORD("application/msword", ".doc", ".docx"),
        EXCEL("application/vnd.ms-excel", ".xls", ".xlsx"),
        POWERPOINT("application/vnd.ms-powerpoint", ".ppt", ".pptx"),
        HTML("text/html", ".html", ".htm"),
        MARKDOWN("text/markdown", ".md", ".markdown"),
        TEXT("text/plain", ".txt"),
        XML("application/xml", ".xml"),
        RTF("application/rtf", ".rtf");

        private final String mimeType;
        private final String[] extensions;

        DocumentType(String mimeType, String... extensions) {
            this.mimeType = mimeType;
            this.extensions = extensions;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String[] getExtensions() {
            return extensions;
        }

        /**
         * 根据文件扩展名判断文档类型
         */
        public static DocumentType fromFileName(String fileName) {
            if (StringUtils.isBlank(fileName)) {
                return null;
            }
            String lowerName = fileName.toLowerCase();
            for (DocumentType type : values()) {
                for (String ext : type.extensions) {
                    if (lowerName.endsWith(ext)) {
                        return type;
                    }
                }
            }
            return null;
        }
    }

    /**
     * 解析结果封装类
     */
    @Data
    public static class ParseResult {
        private String content;                 // 提取的文本内容
        private Map<String, Object> metadata;    // 文档元数据
        private DocumentType documentType;       // 文档类型
        private String fileName;                  // 文件名
        private long fileSize;                    // 文件大小
        private String language;                  // 检测到的语言
        private List<String> paragraphs;          // 分段后的段落
        private Map<String, Object> extraInfo;    // 额外信息

        public ParseResult() {
            this.metadata = new HashMap<>();
            this.extraInfo = new HashMap<>();
            this.paragraphs = new ArrayList<>();
        }
    }

    /**
     * 解析配置类
     */
    @Data
    public static class ParseConfig {
        private boolean extractMetadata = true;           // 是否提取元数据
        private boolean detectLanguage = false;           // 是否检测语言
        private boolean splitParagraphs = false;          // 是否分割段落
        private int maxContentLength = -1;                 // 最大内容长度（-1表示无限制）
        private String encoding = "UTF-8";                 // 字符编码

        public static ParseConfig defaultConfig() {
            return new ParseConfig();
        }
    }

    /**
     * 从 MultipartFile 解析文档
     *
     * @param file 上传的文件
     * @return 解析结果
     * @throws DocumentParseException 解析异常
     */
    public ParseResult parse(MultipartFile file) throws DocumentParseException {
        return parse(file, ParseConfig.defaultConfig());
    }

    /**
     * 从 MultipartFile 解析文档（带配置）
     *
     * @param file   上传的文件
     * @param config 解析配置
     * @return 解析结果
     * @throws DocumentParseException 解析异常
     */
    public ParseResult parse(MultipartFile file, ParseConfig config) throws DocumentParseException {
        if (file == null || file.isEmpty()) {
            throw new DocumentParseException("文件为空");
        }

        String fileName = file.getOriginalFilename();
        log.info("开始解析文档: {}, 大小: {} bytes", fileName, file.getSize());

        try {
            // 创建资源对象
            Resource resource = new InputStreamResource(file.getInputStream()) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };

            return parseResource(resource, fileName, file.getSize(), config);

        } catch (IOException e) {
            log.error("读取文件流失败: {}", fileName, e);
            throw new DocumentParseException("读取文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从文件路径解析文档
     *
     * @param filePath 文件路径
     * @return 解析结果
     * @throws DocumentParseException 解析异常
     */
    public ParseResult parse(String filePath) throws DocumentParseException {
        return parse(filePath, ParseConfig.defaultConfig());
    }

    /**
     * 从文件路径解析文档（带配置）
     *
     * @param filePath 文件路径
     * @param config   解析配置
     * @return 解析结果
     * @throws DocumentParseException 解析异常
     */
    public ParseResult parse(String filePath, ParseConfig config) throws DocumentParseException {
        if (StringUtils.isBlank(filePath)) {
            throw new DocumentParseException("文件路径为空");
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            throw new DocumentParseException("文件不存在: " + filePath);
        }

        log.info("开始解析文档: {}, 大小: {} bytes", filePath, file.length());

        try {
            Resource resource = new FileSystemResource(file);
            return parseResource(resource, file.getName(), file.length(), config);

        } catch (Exception e) {
            log.error("解析文档失败: {}", filePath, e);
            throw new DocumentParseException("解析文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节数组解析文档
     *
     * @param bytes    文件字节数组
     * @param fileName 文件名
     * @return 解析结果
     * @throws DocumentParseException 解析异常
     */
    public ParseResult parse(byte[] bytes, String fileName) throws DocumentParseException {
        return parse(bytes, fileName, ParseConfig.defaultConfig());
    }

    /**
     * 从字节数组解析文档（带配置）
     *
     * @param bytes    文件字节数组
     * @param fileName 文件名
     * @param config   解析配置
     * @return 解析结果
     * @throws DocumentParseException 解析异常
     */
    public ParseResult parse(byte[] bytes, String fileName, ParseConfig config) throws DocumentParseException {
        if (bytes == null || bytes.length == 0) {
            throw new DocumentParseException("字节数组为空");
        }

        log.info("开始解析文档: {}, 大小: {} bytes", fileName, bytes.length);

        try {
            Resource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };

            return parseResource(resource, fileName, bytes.length, config);

        } catch (Exception e) {
            log.error("解析文档失败: {}", fileName, e);
            throw new DocumentParseException("解析文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从输入流解析文档
     *
     * @param inputStream 输入流
     * @param fileName    文件名
     * @return 解析结果
     * @throws DocumentParseException 解析异常
     */
    public ParseResult parse(InputStream inputStream, String fileName) throws DocumentParseException {
        return parse(inputStream, fileName, ParseConfig.defaultConfig());
    }

    /**
     * 从输入流解析文档（带配置）
     *
     * @param inputStream 输入流
     * @param fileName    文件名
     * @param config      解析配置
     * @return 解析结果
     * @throws DocumentParseException 解析异常
     */
    public ParseResult parse(InputStream inputStream, String fileName, ParseConfig config) throws DocumentParseException {
        if (inputStream == null) {
            throw new DocumentParseException("输入流为空");
        }

        try {
            // 保存输入流到临时文件，以便获取大小和重复读取
            Path tempFile = Files.createTempFile("upload_", "_" + fileName);
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            long fileSize = Files.size(tempFile);
            log.info("开始解析文档: {}, 大小: {} bytes", fileName, fileSize);

            Resource resource = new FileSystemResource(tempFile.toFile());
            ParseResult result = parseResource(resource, fileName, fileSize, config);

            // 删除临时文件
            Files.deleteIfExists(tempFile);

            return result;

        } catch (IOException e) {
            log.error("解析文档失败: {}", fileName, e);
            throw new DocumentParseException("解析文档失败: " + e.getMessage(), e);
        }
    }

    /**
     * 核心解析方法
     */
    private ParseResult parseResource(Resource resource, String fileName, long fileSize, ParseConfig config)
            throws DocumentParseException {

        ParseResult result = new ParseResult();
        result.setFileName(fileName);
        result.setFileSize(fileSize);
        result.setDocumentType(DocumentType.fromFileName(fileName));

        try {
            // 创建 TikaDocumentParser
            TikaDocumentReader reader = new TikaDocumentReader(resource);

            // 读取文档
            List<Document> documents = reader.get();

            if (documents == null || documents.isEmpty()) {
                throw new DocumentParseException("未提取到文档内容");
            }

            // 合并所有文档内容（通常只有一个Document）
            StringBuilder contentBuilder = new StringBuilder();
            for (Document doc : documents) {
                if (StringUtils.isNotBlank(doc.getText())) {
                    contentBuilder.append(doc.getText()).append("\n");
                }

                // 提取元数据
                if (config.isExtractMetadata() && doc.getMetadata() != null) {
                    result.getMetadata().putAll(doc.getMetadata());
                }
            }

            String content = contentBuilder.toString();

            // 限制内容长度
            if (config.getMaxContentLength() > 0 && content.length() > config.getMaxContentLength()) {
                content = content.substring(0, config.getMaxContentLength());
                log.info("内容已截断，长度: {} -> {}", contentBuilder.length(), config.getMaxContentLength());
            }

            result.setContent(content);

            // 语言检测（简单实现，实际可集成语言检测库）
            if (config.isDetectLanguage()) {
                result.setLanguage(detectLanguage(content));
            }

            // 段落分割
            if (config.isSplitParagraphs()) {
                result.setParagraphs(splitIntoParagraphs(content));
            }

            // 添加额外信息
            result.getExtraInfo().put("charCount", content.length());
            result.getExtraInfo().put("wordCount", countWords(content));
            result.getExtraInfo().put("lineCount", content.split("\n").length);

            log.info("文档解析完成: {}, 内容长度: {} 字符", fileName, content.length());

        } catch (Exception e) {
            log.error("Tika 解析失败: {}", fileName, e);
            throw new DocumentParseException("Tika 解析失败: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * 简单的语言检测
     */
    private String detectLanguage(String text) {
        if (StringUtils.isBlank(text)) {
            return "unknown";
        }

        // 统计中文字符比例
        int chineseCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            }
        }

        double chineseRatio = (double) chineseCount / text.length();

        if (chineseRatio > 0.1) {
            return "zh";
        } else {
            return "en";
        }
    }

    /**
     * 分割段落
     */
    private List<String> splitIntoParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return paragraphs;
        }

        // 按空行分割
        String[] parts = text.split("\\n\\s*\\n");
        for (String part : parts) {
            String trimmed = part.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                paragraphs.add(trimmed);
            }
        }

        // 如果分割后太少，按单行分割
        if (paragraphs.size() <= 1) {
            paragraphs.clear();
            String[] lines = text.split("\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (StringUtils.isNotBlank(trimmed)) {
                    paragraphs.add(trimmed);
                }
            }
        }

        return paragraphs;
    }

    /**
     * 统计单词数（简单实现）
     */
    private int countWords(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        return words.length;
    }

    /**
     * 检测文档类型
     */
    public DocumentType detectDocumentType(String fileName) {
        return DocumentType.fromFileName(fileName);
    }

    /**
     * 快速解析仅获取文本内容
     */
    public String extractText(MultipartFile file) throws DocumentParseException {
        ParseResult result = parse(file, ParseConfig.defaultConfig());
        return result.getContent();
    }

    /**
     * 快速解析仅获取文本内容（从文件路径）
     */
    public String extractText(String filePath) throws DocumentParseException {
        ParseResult result = parse(filePath, ParseConfig.defaultConfig());
        return result.getContent();
    }

    /**
     * 检查文件是否受支持
     */
    public boolean isSupported(String fileName) {
        return DocumentType.fromFileName(fileName) != null;
    }

    /**
     * 获取支持的文件格式描述
     */
    public String getSupportedFormats() {
        StringBuilder sb = new StringBuilder("支持格式：");
        for (DocumentType type : DocumentType.values()) {
            sb.append("\n- ").append(type.name()).append(": ");
            sb.append(String.join(", ", type.getExtensions()));
        }
        return sb.toString();
    }
}