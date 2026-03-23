package com.smart.office.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.office.common.exception.DocumentParseException;
import com.smart.office.model.dto.DocumentStatusDTO;
import com.smart.office.model.dto.DocumentUploadResponseDTO;
import com.smart.office.model.entity.DocChunk;
import com.smart.office.model.entity.Document;
import com.smart.office.model.vo.BatchDeleteResultVO;
import com.smart.office.model.vo.DocumentStatisticsVO;
import com.smart.office.repository.mapper.DocChunkMapper;
import com.smart.office.repository.mapper.DocumentMapper;
import com.smart.office.repository.milvus.MilvusSearchRepository;
import com.smart.office.task.DocumentProcessTask;
import com.smart.office.util.FileParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 文档服务 - 使用 MyBatis-Plus 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService extends ServiceImpl<DocumentMapper, Document> {

    private final FileParser fileParser;
    private final ThreadPoolTaskExecutor documentTaskExecutor;
    private final DocumentProcessTask documentProcessTask;
    private final ProgressService progressService;
    private final DocumentMapper documentMapper;
    private final DocChunkMapper docChunkMapper;
    private final MilvusSearchRepository milvusSearchRepository;

    @Value("${app.document.storage.path:./data/docs}")
    private String storagePath;

    @Value("${app.document.storage.allowed-types:pdf,doc,docx,txt,md}")
    private String[] allowedTypes;

    // 用于存储处理进度
    private final Map<Long, Integer> progressMap = new ConcurrentHashMap<>();

    /**
     * 上传文档
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponseDTO uploadDocument(MultipartFile file, String uploader,
                                                    String department, String description)
            throws DocumentParseException, IOException {

        // 1. 验证文件
        validateFile(file);

        // 2. 保存文件到本地
        String filePath = saveFileToLocal(file);

        // 3. 创建文档记录
        Document document = new Document();
        document.setFileName(file.getOriginalFilename());
        document.setFilePath(filePath);
        document.setFileSize(file.getSize());
        document.setFileType(getFileExtension(file.getOriginalFilename()));
        document.setUploader(uploader);
        document.setDepartment(department);
        document.setDescription(description);
        document.setStatus(0); // 处理中
        document.setChunkCount(0);

        this.save(document);

        log.info("文档记录已创建: id={}, fileName={}", document.getId(), file.getOriginalFilename());

        // 4. 异步处理文档（只触发任务，不调用进度更新）
        CompletableFuture.runAsync(() -> {
            try {
                documentProcessTask.processDocument(document.getId(), file);
            } catch (Exception e) {
                log.error("异步处理文档失败: {}", document.getId(), e);
                progressService.updateDocumentStatus(document.getId(), 2, "异步处理异常: " + e.getMessage());
            }
        }, documentTaskExecutor);

        // 5. 构建响应
        return buildUploadResponse(document);
    }

    // 在 DocumentService.java 中添加以下方法

    /**
     * 流式上传文档（内存优化版本）
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponseDTO uploadDocumentStreaming(MultipartFile file, String uploader,
                                                             String department, String description)
            throws DocumentParseException, IOException {

        // 1. 验证文件
        validateFile(file);

        // 2. 保存文件到本地（临时存储）
        String filePath = saveFileToLocal(file);

        // 3. 创建文档记录
        Document document = new Document();
        document.setFileName(file.getOriginalFilename());
        document.setFilePath(filePath);
        document.setFileSize(file.getSize());
        document.setFileType(getFileExtension(file.getOriginalFilename()));
        document.setUploader(uploader);
        document.setDepartment(department);
        document.setDescription(description);
        document.setStatus(0); // 处理中
        document.setChunkCount(0);

        this.save(document);

        log.info("文档记录已创建: id={}, fileName={}, size={}MB",
                document.getId(), file.getOriginalFilename(), file.getSize() / 1024 / 1024);

        // 4. 异步流式处理文档 - 传递文件路径而不是 MultipartFile
        CompletableFuture.runAsync(() -> {
            try {
                documentProcessTask.processDocumentStreaming(document.getId(), filePath);
            } catch (Exception e) {
                log.error("异步流式处理文档失败: {}", document.getId(), e);
                progressService.updateDocumentStatus(document.getId(), 2, "异步处理异常: " + e.getMessage());
            }
        }, documentTaskExecutor);

        // 5. 构建响应
        return buildUploadResponse(document);
    }

    /**
     * 验证文件（通用版本）
     */
    private void validateFile(String fileName, long fileSize) throws DocumentParseException {
        if (fileName == null || fileName.isBlank()) {
            throw new DocumentParseException("文件名无效");
        }

        // 检查文件类型
        String extension = getFileExtension(fileName);
        boolean allowed = false;
        for (String type : allowedTypes) {
            if (type.equalsIgnoreCase(extension)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            throw new DocumentParseException("不支持的文件类型: " + extension +
                    "，支持的类型: " + String.join(", ", allowedTypes));
        }

        // 检查文件大小（限制100MB）
        if (fileSize > 100 * 1024 * 1024) {
            throw new DocumentParseException("文件大小超过限制（最大100MB）");
        }
    }


    /**
     * 获取文档状态
     */
    public DocumentStatusDTO getDocumentStatus(Long documentId, String uploader) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getId, documentId)
                .eq(Document::getUploader, uploader);

        Document document = this.getOne(queryWrapper);

        if (document == null) {
            throw new RuntimeException("文档不存在或无权限访问");
        }

        return buildStatusDTO(document);
    }

    /**
     * 分页查询用户文档
     */
    public Page<Document> getUserDocumentsPage(String uploader, int current, int size) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getUploader, uploader)
                .orderByDesc(Document::getUploadTime);

        Page<Document> page = new Page<>(current, size);
        return this.page(page, queryWrapper);
    }

    /**
     * 查询用户所有文档
     */
    public List<Document> getUserDocuments(String uploader) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getUploader, uploader)
                .orderByDesc(Document::getUploadTime);

        return this.list(queryWrapper);
    }

    /**
     * 删除文档（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDocument(Long documentId, String uploader) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getId, documentId)
                .eq(Document::getUploader, uploader);

        Document document = this.getOne(queryWrapper);

        if (document == null) {
            throw new RuntimeException("文档不存在或无权限访问");
        }

        // 逻辑删除（MyBatis-Plus 会自动填充 deleted=1）
        boolean success = this.removeById(documentId);

        // 可选：删除物理文件
        if (success && document.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(document.getFilePath()));
            } catch (IOException e) {
                log.warn("删除物理文件失败: {}", document.getFilePath(), e);
            }
        }

        log.info("文档已删除: id={}", documentId);
        return success;
    }

    /**
     * 更新文档状态
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateDocumentStatus(Long id, Integer status, String errorMessage) {
        Document document = new Document();
        document.setId(id);
        document.setStatus(status);
        document.setErrorMessage(errorMessage);

        return this.updateById(document);
    }

    /**
     * 更新文档处理结果
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProcessResult(Long id, Integer status, Integer chunkCount, Integer processTime) {
        Document document = new Document();
        document.setId(id);
        document.setStatus(status);
        document.setChunkCount(chunkCount);
        document.setProcessTime(processTime);

        return this.updateById(document);
    }

    /**
     * 更新处理进度（委托给 ProgressService）
     */
    public void updateProgress(Long documentId, int progress) {
        progressService.updateProgress(documentId, progress);
    }

    /**
     * 统计用户文档数量
     */
    public long countByUser(String uploader) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getUploader, uploader);

        return this.count(queryWrapper);
    }

    /**
     * 获取所有文档（管理员分页）
     */
    public Page<Document> getAllDocumentsPage(Integer page, Integer size, Integer status, String uploader) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Document::getStatus, status);
        }
        if (uploader != null && !uploader.isEmpty()) {
            wrapper.eq(Document::getUploader, uploader);
        }
        wrapper.orderByDesc(Document::getUploadTime);

        Page<Document> pageParam = new Page<>(page, size);
        return this.page(pageParam, wrapper);
    }

    /**
     * 获取文档详情
     */
    public Document getDocumentDetail(Long documentId, String uploader) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getId, documentId)
                .eq(Document::getUploader, uploader);
        Document document = this.getOne(wrapper);
        if (document == null) {
            throw new RuntimeException("文档不存在或无权限访问");
        }
        return document;
    }

    /**
     * 获取文档预览内容
     */
    public List<String> getDocumentPreview(Long documentId, int maxLength) {
        // 从 doc_chunk 表获取前几个块的内容
        List<DocChunk> chunks = docChunkMapper.selectByDocId(documentId);
        return chunks.stream()
                .limit(5) // 预览前5个块
                .map(chunk -> {
                    String content = chunk.getContent();
                    if (content.length() > maxLength) {
                        return content.substring(0, maxLength) + "...";
                    }
                    return content;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取文档统计信息
     * @param uploader 上传者/用户ID
     * @return 文档统计信息
     */
    public DocumentStatisticsVO getDocumentStatistics(String uploader) {
        log.info("获取文档统计信息: uploader={}", uploader);

        // 1. 统计各状态文档数量
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getUploader, uploader);

        List<Document> allDocuments = this.list(wrapper);

        long totalCount = allDocuments.size();
        long processingCount = allDocuments.stream()
                .filter(doc -> doc.getStatus() != null && doc.getStatus() == 0)
                .count();
        long completedCount = allDocuments.stream()
                .filter(doc -> doc.getStatus() != null && doc.getStatus() == 1)
                .count();
        long failedCount = allDocuments.stream()
                .filter(doc -> doc.getStatus() != null && doc.getStatus() == 2)
                .count();
        long deletedCount = allDocuments.stream()
                .filter(doc -> doc.getStatus() != null && doc.getStatus() == 3)
                .count();

        // 2. 统计总块数
        long totalChunks = allDocuments.stream()
                .filter(doc -> doc.getStatus() == 1) // 只统计已完成文档的块数
                .mapToLong(Document::getChunkCount)
                .sum();

        // 3. 统计总文件大小
        long totalSize = allDocuments.stream()
                .mapToLong(doc -> doc.getFileSize() != null ? doc.getFileSize() : 0)
                .sum();

        // 4. 格式化文件大小
        String totalSizeFormatted = formatFileSize(totalSize);

        // 5. 构建返回对象
        DocumentStatisticsVO statistics = DocumentStatisticsVO.builder()
                .totalCount(totalCount)
                .processingCount(processingCount)
                .completedCount(completedCount)
                .failedCount(failedCount)
                .deletedCount(deletedCount)
                .totalChunks(totalChunks)
                .totalSize(totalSize)
                .totalSizeFormatted(totalSizeFormatted)
                .build();

        log.info("文档统计完成: uploader={}, 总数={}, 完成={}, 处理中={}, 失败={}, 总块数={}, 总大小={}",
                uploader, totalCount, completedCount, processingCount, failedCount, totalChunks, totalSizeFormatted);

        return statistics;
    }

    /**
     * 格式化文件大小
     * @param size 文件大小（字节）
     * @return 格式化后的字符串
     */
    private String formatFileSize(long size) {
        if (size <= 0) {
            return "0 B";
        }

        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

        DecimalFormat df = new DecimalFormat("#.##");
        double value = size / Math.pow(1024, digitGroups);

        return df.format(value) + " " + units[digitGroups];
    }

    /**
     * 获取更详细的统计信息（包括按部门统计）
     * @param uploader 上传者/用户ID
     * @return 详细统计信息
     */
    public DocumentStatisticsVO getDetailedStatistics(String uploader) {
        log.info("获取详细文档统计信息: uploader={}", uploader);

        DocumentStatisticsVO basicStats = getDocumentStatistics(uploader);

        // 1. 按部门统计
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getUploader, uploader)
                .eq(Document::getStatus, 1); // 只统计已完成的文档

        List<Document> completedDocs = this.list(wrapper);

        long hrCount = completedDocs.stream()
                .filter(doc -> "HR".equals(doc.getDepartment()))
                .count();
        long itCount = completedDocs.stream()
                .filter(doc -> "IT".equals(doc.getDepartment()))
                .count();
        long rdCount = completedDocs.stream()
                .filter(doc -> "R&D".equals(doc.getDepartment()))
                .count();
        long otherCount = completedDocs.stream()
                .filter(doc -> doc.getDepartment() == null ||
                        (!"HR".equals(doc.getDepartment()) &&
                                !"IT".equals(doc.getDepartment()) &&
                                !"R&D".equals(doc.getDepartment())))
                .count();

        // 2. 按月份统计上传数量
        // 这里可以根据需要添加更多统计维度

        // 构建详细统计信息
        DocumentStatisticsVO.DetailedStatistics detailed = DocumentStatisticsVO.DetailedStatistics.builder()
                .byDepartment(new DocumentStatisticsVO.DepartmentStatistics(hrCount, itCount, rdCount, otherCount))
                .build();

        basicStats.setDetailed(detailed);

        return basicStats;
    }

    /**
     * 获取用户所有文档ID列表（用于权限过滤）
     * @param uploader 上传者/用户ID
     * @return 文档ID列表
     */
    public List<Long> getUserDocumentIds(String uploader) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getUploader, uploader)
                .eq(Document::getStatus, 1) // 只返回已完成的文档
                .select(Document::getId);

        List<Document> documents = this.list(wrapper);
        return documents.stream()
                .map(Document::getId)
                .collect(Collectors.toList());
    }

    /**
     * 批量删除文档
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchDeleteResultVO batchDeleteDocuments(List<Long> documentIds, String uploader) {
        int successCount = 0;
        int failCount = 0;
        List<Long> failedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Long documentId : documentIds) {
            try {
                if (deleteDocument(documentId, uploader)) {
                    successCount++;
                } else {
                    failCount++;
                    failedIds.add(documentId);
                    errors.add("文档ID " + documentId + " 删除失败");
                }
            } catch (Exception e) {
                failCount++;
                failedIds.add(documentId);
                errors.add("文档ID " + documentId + ": " + e.getMessage());
            }
        }

        return BatchDeleteResultVO.builder()
                .successCount(successCount)
                .failCount(failCount)
                .failedIds(failedIds)
                .errors(errors)
                .build();
    }

    /**
     * 恢复文档
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean restoreDocument(Long documentId, String uploader) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getId, documentId)
                .eq(Document::getUploader, uploader)
                .eq(Document::getDeleted, 1); // 已删除的文档
        Document document = this.getOne(wrapper);

        if (document == null) {
            throw new RuntimeException("文档不存在或无权限恢复");
        }

        document.setDeleted(0);
        document.setStatus(1); // 恢复后状态为已完成
        return this.updateById(document);
    }

    /**
     * 重新处理文档
     */
    @Transactional(rollbackFor = Exception.class)
    public void reprocessDocument(Long documentId, String uploader) {
        Document document = getDocumentDetail(documentId, uploader);

        if (document.getStatus() != 2) {
            throw new RuntimeException("只有处理失败的文档才能重新处理");
        }

        // 清除原有数据
        docChunkMapper.deleteByDocId(documentId);
        milvusSearchRepository.deleteByDocId(documentId);

        // 重置状态
        document.setStatus(0);
        document.setChunkCount(0);
        document.setErrorMessage(null);
        this.updateById(document);

        // 重新加入处理队列
        // 需要从文件路径读取文件并重新处理
        // 可以调用异步任务
    }

    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) throws DocumentParseException {
        if (file.isEmpty()) {
            throw new DocumentParseException("文件为空");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new DocumentParseException("文件名无效");
        }

        // 检查文件类型
        String extension = getFileExtension(fileName);
        boolean allowed = false;
        for (String type : allowedTypes) {
            if (type.equalsIgnoreCase(extension)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            throw new DocumentParseException("不支持的文件类型: " + extension +
                    "，支持的类型: " + String.join(", ", allowedTypes));
        }

        // 检查文件大小（限制100MB）
        if (file.getSize() > 100 * 1024 * 1024) {
            throw new DocumentParseException("文件大小超过限制（最大100MB）");
        }
    }

    /**
     * 保存文件到本地
     */
    private String saveFileToLocal(MultipartFile file) throws IOException {
        // 创建存储目录
        Path uploadPath = Paths.get(storagePath);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成唯一文件名
        String originalFileName = file.getOriginalFilename();
        String extension = getFileExtension(originalFileName);
        String uniqueFileName = UUID.randomUUID().toString() + "_" +
                System.currentTimeMillis() + "." + extension;

        // 保存文件
        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        log.info("文件已保存: {}", filePath.toString());
        return filePath.toString();
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 构建上传响应
     */
    private DocumentUploadResponseDTO buildUploadResponse(Document document) {
        DocumentUploadResponseDTO response = new DocumentUploadResponseDTO();
        response.setTaskId(document.getId());
        response.setFileName(document.getFileName());
        response.setFileSize(document.getFileSize());
        response.setFileType(document.getFileType());
        response.setStatus(getStatusDesc(document.getStatus()));
        response.setMessage("文档上传成功，正在处理中");
        response.setUploadTime(document.getUploadTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return response;
    }

    /**
     * 构建状态DTO
     */
    private DocumentStatusDTO buildStatusDTO(Document document) {
        DocumentStatusDTO statusDTO = new DocumentStatusDTO();
        statusDTO.setDocumentId(document.getId());
        statusDTO.setFileName(document.getFileName());
        statusDTO.setStatus(document.getStatus());
        statusDTO.setStatusDesc(getStatusDesc(document.getStatus()));
        statusDTO.setChunkCount(document.getChunkCount());
        statusDTO.setErrorMessage(document.getErrorMessage());
        statusDTO.setUploadTime(document.getUploadTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        if (document.getUpdateTime() != null) {
            statusDTO.setCompleteTime(document.getUpdateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        // 从 ProgressService 获取进度
        Integer progress = progressService.getProgress(document.getId());
        if (progress == 0 && document.getStatus() == 1) {
            progress = 100;
        } else if (progress == 0 && document.getStatus() == 2) {
            progress = 0;
        }
        statusDTO.setProgress(progress);

        statusDTO.setTotalChunks(document.getChunkCount());

        return statusDTO;
    }

    /**
     * 获取状态描述
     */
    private String getStatusDesc(Integer status) {
        return switch (status) {
            case 0 -> "处理中";
            case 1 -> "已完成";
            case 2 -> "处理失败";
            case 3 -> "已禁用";
            default -> "未知";
        };
    }
}
