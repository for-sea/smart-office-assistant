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

import java.io.*;
import java.nio.charset.StandardCharsets;
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

    // 在 FileParser.java 中添加以下方法

    /**
     * 检查是否为有效的UTF-8编码
     */
    private boolean isValidUTF8(byte[] bytes) {
        try {
            String test = new String(bytes, StandardCharsets.UTF_8);
            // 简单检查：如果转换后没有太多替换字符，认为是有效UTF-8
            int invalidCount = 0;
            for (int i = 0; i < Math.min(test.length(), 100); i++) {
                char c = test.charAt(i);
                if (c == '\ufffd') {
                    invalidCount++;
                }
            }
            return invalidCount < 5;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查文本是否有效（没有太多乱码）
     */
    private boolean isValidText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        int validCount = 0;
        int checkLength = Math.min(text.length(), 200);

        for (int i = 0; i < checkLength; i++) {
            char c = text.charAt(i);
            // 检查是否为常见字符（中文、英文、数字、常用标点）
            if ((c >= 0x4e00 && c <= 0x9fff) || // 中文CJK统一表意文字
                    (c >= 0x3400 && c <= 0x4dbf) || // 中文扩展A
                    (c >= 0x20000 && c <= 0x2a6df) || // 中文扩展B
                    (c >= 'a' && c <= 'z') ||
                    (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') ||
                    (c == ' ' || c == '\n' || c == '\r' || c == '\t') ||
                    (c >= 0x3000 && c <= 0x303f) || // 中文标点（、。！？等）
                    (c >= 0xff00 && c <= 0xffef) || // 全角字符
                    (c >= 0x2000 && c <= 0x206f) || // 通用标点
                    (c >= 0x20 && c <= 0x7e)) { // ASCII可打印字符
                validCount++;
            }
        }

        // 如果有效字符比例大于70%，认为文本有效
        return (double) validCount / checkLength > 0.7;
    }

    /**
     * 判断是否为文本文件
     */
    private boolean isTextFile(String fileName) {
        if (fileName == null) return false;
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".txt") ||
                lowerName.endsWith(".md") ||
                lowerName.endsWith(".markdown") ||
                lowerName.endsWith(".xml") ||
                lowerName.endsWith(".html") ||
                lowerName.endsWith(".htm") ||
                lowerName.endsWith(".csv") ||
                lowerName.endsWith(".json") ||
                lowerName.endsWith(".properties") ||
                lowerName.endsWith(".log") ||
                lowerName.endsWith(".sql") ||
                lowerName.endsWith(".yaml") ||
                lowerName.endsWith(".yml");
    }

    /**
     * 检查文本是否有乱码
     */
    private boolean hasGarbledText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        int garbledCount = 0;
        int checkLength = Math.min(text.length(), 200);

        for (int i = 0; i < checkLength; i++) {
            char c = text.charAt(i);
            // 乱码通常表现为：
            // 1. 替换字符 \ufffd
            // 2. 控制字符（除了换行、回车、制表符）
            // 3. 非常规Unicode区域
            if (c == '\ufffd') {
                garbledCount++;
            } else if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                garbledCount++;
            } else if (c > 0x7f && c < 0xa0) {
                // 这个范围通常是ISO-8859-1的扩展字符，容易产生乱码
                garbledCount++;
            }
        }

        return (double) garbledCount / checkLength > 0.1;
    }

    /**
     * 从资源检测字符编码
     */
    private String detectCharsetFromResource(Resource resource) {
        try {
            byte[] buffer = new byte[4096];
            try (InputStream is = resource.getInputStream()) {
                int bytesRead = is.read(buffer);
                if (bytesRead > 0) {
                    byte[] sample = new byte[bytesRead];
                    System.arraycopy(buffer, 0, sample, 0, bytesRead);

                    // 检查UTF-8 BOM (EF BB BF)
                    if (bytesRead >= 3 &&
                            sample[0] == (byte)0xEF && sample[1] == (byte)0xBB && sample[2] == (byte)0xBF) {
                        return "UTF-8";
                    }

                    // 检查UTF-16BE BOM (FE FF)
                    if (bytesRead >= 2 && sample[0] == (byte)0xFE && sample[1] == (byte)0xFF) {
                        return "UTF-16BE";
                    }

                    // 检查UTF-16LE BOM (FF FE)
                    if (bytesRead >= 2 && sample[0] == (byte)0xFF && sample[1] == (byte)0xFE) {
                        return "UTF-16LE";
                    }

                    // 检查UTF-32BE BOM (00 00 FE FF)
                    if (bytesRead >= 4 &&
                            sample[0] == 0x00 && sample[1] == 0x00 &&
                            sample[2] == (byte)0xFE && sample[3] == (byte)0xFF) {
                        return "UTF-32BE";
                    }

                    // 检查UTF-32LE BOM (FF FE 00 00)
                    if (bytesRead >= 4 &&
                            sample[0] == (byte)0xFF && sample[1] == (byte)0xFE &&
                            sample[2] == 0x00 && sample[3] == 0x00) {
                        return "UTF-32LE";
                    }

                    // 尝试UTF-8
                    if (isValidUTF8(sample)) {
                        return "UTF-8";
                    }

                    // 尝试常见的中文编码
                    String[] encodings = {"GBK", "GB2312", "GB18030", "Big5", "ISO-8859-1", "Windows-1252"};
                    for (String encoding : encodings) {
                        try {
                            String test = new String(sample, encoding);
                            // 检查转换后的文本是否合理
                            if (isValidText(test) && !hasGarbledText(test)) {
                                log.debug("检测到编码: {}, 有效字符率: {}/{}",
                                        encoding, getValidCharCount(test), test.length());
                                return encoding;
                            }
                        } catch (Exception e) {
                            // 编码不适用，继续尝试
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("检测字符编码失败", e);
        }

        // 默认返回UTF-8
        log.info("未检测到明确编码，使用默认UTF-8");
        return "UTF-8";
    }

    /**
     * 获取有效字符数量（辅助方法）
     */
    private int getValidCharCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int validCount = 0;
        int checkLength = Math.min(text.length(), 200);

        for (int i = 0; i < checkLength; i++) {
            char c = text.charAt(i);
            // 常见有效字符的判断
            if ((c >= 0x4e00 && c <= 0x9fff) || // 中文
                    (c >= 'a' && c <= 'z') ||
                    (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') ||
                    (c == ' ' || c == '\n' || c == '\r' || c == '\t') ||
                    (c >= 0x20 && c <= 0x7e)) { // ASCII可打印字符
                validCount++;
            }
        }

        return validCount;
    }

    /**
     * 使用指定编码重新解析文件
     */
    private String reparseWithCharset(Resource resource, String charset) {
        try {
            StringBuilder content = new StringBuilder();
            try (InputStream is = resource.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, charset))) {
                char[] buffer = new char[8192];
                int charsRead;
                while ((charsRead = reader.read(buffer)) != -1) {
                    content.append(buffer, 0, charsRead);
                }
            }
            return content.toString();
        } catch (IOException e) {
            log.warn("使用编码 {} 重新解析失败: {}", charset, e.getMessage());
            return "";
        }
    }

    // 在 FileParser.java 中修改 parseResource 方法，添加编码检测逻辑

    private ParseResult parseResource(Resource resource, String fileName, long fileSize, ParseConfig config)
            throws DocumentParseException {

        ParseResult result = new ParseResult();
        result.setFileName(fileName);
        result.setFileSize(fileSize);
        result.setDocumentType(DocumentType.fromFileName(fileName));

        try {
            // 对于文本文件，需要检测编码
            String charset = "UTF-8";
            if (isTextFile(fileName)) {
                charset = detectCharsetFromResource(resource);
                log.info("检测到文件编码: {}, 文件: {}", charset, fileName);
            }

            // 创建 TikaDocumentParser
            TikaDocumentReader reader = new TikaDocumentReader(resource);

            // 读取文档
            List<Document> documents = reader.get();

            if (documents == null || documents.isEmpty()) {
                throw new DocumentParseException("未提取到文档内容");
            }

            // 合并所有文档内容
            StringBuilder contentBuilder = new StringBuilder();
            for (Document doc : documents) {
                if (StringUtils.isNotBlank(doc.getText())) {
                    String text = doc.getText();
                    contentBuilder.append(text).append("\n");
                }

                // 提取元数据
                if (config.isExtractMetadata() && doc.getMetadata() != null) {
                    result.getMetadata().putAll(doc.getMetadata());
                }
            }

            String content = contentBuilder.toString();

            // 如果是文本文件且内容有乱码，尝试用检测到的编码重新解析
            if (isTextFile(fileName) && hasGarbledText(content)) {
                log.warn("检测到内容可能有乱码，尝试使用编码 {} 重新解析", charset);
                String reparseContent = reparseWithCharset(resource, charset);
                if (StringUtils.isNotBlank(reparseContent) && !hasGarbledText(reparseContent)) {
                    content = reparseContent;
                    log.info("使用编码 {} 重新解析成功", charset);
                }
            }

            // 限制内容长度
            if (config.getMaxContentLength() > 0 && content.length() > config.getMaxContentLength()) {
                content = content.substring(0, config.getMaxContentLength());
                log.info("内容已截断，原长度: {} -> 截断后: {}", contentBuilder.length(), config.getMaxContentLength());
            }

            result.setContent(content);

            // 语言检测
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
            result.getExtraInfo().put("charset", charset);

            log.info("文档解析完成: {}, 内容长度: {} 字符, 编码: {}",
                    fileName, content.length(), charset);

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