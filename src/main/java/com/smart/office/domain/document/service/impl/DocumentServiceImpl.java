package com.smart.office.domain.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smart.office.shared.common.DocumentParseException;
import com.smart.office.interfaces.dto.DocumentPermissionDTO;
import com.smart.office.interfaces.dto.DocumentStatusDTO;
import com.smart.office.interfaces.dto.DocumentUploadResponseDTO;
import com.smart.office.domain.document.entity.DocChunk;
import com.smart.office.domain.document.entity.Document;
import com.smart.office.base.data.mapper.DocChunkMapper;
import com.smart.office.base.data.mapper.DocumentMapper;
import com.smart.office.base.data.repository.MilvusSearchRepository;
import com.smart.office.shared.security.LoginUserDetails;
import com.smart.office.domain.document.service.DocumentService;
import com.smart.office.domain.permission.service.PermissionService;
import com.smart.office.application.ProgressService;
import com.smart.office.shared.task.DocumentProcessTask;
import com.smart.office.util.FileParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档服务默认实现，涵盖上传、查询与权限处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    private final FileParser fileParser;
    private final ThreadPoolTaskExecutor documentTaskExecutor;
    private final DocumentProcessTask documentProcessTask;
    private final ProgressService progressService;
    private final DocumentMapper documentMapper;
    private final DocChunkMapper docChunkMapper;
    private final MilvusSearchRepository milvusSearchRepository;
    private final PermissionService permissionService;

    @Value("${app.document.storage.path:./data/docs}")
    private String storagePath;

    @Value("${app.document.storage.allowed-types:pdf,doc,docx,txt,md}")
    private String[] allowedTypes;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentUploadResponseDTO uploadDocumentStreaming(MultipartFile file,
                                                             LoginUserDetails currentUser,
                                                             String department,
                                                             String description,
                                                             DocumentPermissionDTO permissionDTO)
            throws DocumentParseException, IOException {
        String uploader = resolveUserId(currentUser);
        String targetDepartment = resolveDepartment(currentUser, department);

        DocumentUploadResponseDTO response = uploadDocumentStreamingInternal(
                file, uploader, targetDepartment, description);

        DocumentPermissionDTO effectiveDto = permissionDTO != null
                ? permissionDTO
                : new DocumentPermissionDTO();
        permissionService.applyDocumentPermissions(response.getTaskId(), effectiveDto, currentUser);
        return response;
    }

    private DocumentUploadResponseDTO uploadDocumentStreamingInternal(MultipartFile file,
                                                                      String uploader,
                                                                      String department,
                                                                      String description)
            throws DocumentParseException, IOException {

        validateFile(file);
        String filePath = saveFileToLocal(file);

        Document document = new Document();
        document.setFileName(file.getOriginalFilename());
        document.setFilePath(filePath);
        document.setFileSize(file.getSize());
        document.setFileType(getFileExtension(file.getOriginalFilename()));
        document.setUploader(uploader);
        document.setDepartment(department);
        document.setDescription(description);
        document.setStatus(0);
        document.setChunkCount(0);
        this.save(document);

        log.info("文档已入库: id={}, fileName={}", document.getId(), document.getFileName());

        CompletableFuture.runAsync(() -> {
            try {
                documentProcessTask.processDocumentStreaming(document.getId(), filePath);
            } catch (Exception e) {
                log.error("异步处理文档失败: {}", document.getId(), e);
                progressService.updateDocumentStatus(
                        document.getId(), 2, "异步处理异常: " + e.getMessage());
            }
        }, documentTaskExecutor);

        return buildUploadResponse(document);
    }

    @Override
    public DocumentStatusDTO getDocumentStatus(Long documentId, String uploader) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getId, documentId)
                .eq(Document::getUploader, uploader);

        Document document = this.getOne(queryWrapper);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在或无访问权限");
        }
        return buildStatusDTO(document);
    }

    @Override
    public Page<Document> getDocumentsPageByIds(List<Long> docIds, int current, int size) {
        Page<Document> page = new Page<>(current, size);
        if (CollectionUtils.isEmpty(docIds)) {
            page.setRecords(Collections.emptyList());
            page.setTotal(0);
            return page;
        }
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Document::getId, docIds)
                .orderByDesc(Document::getUploadTime);
        return this.page(page, wrapper);
    }

    @Override
    public List<Document> listDocumentsByIds(List<Long> docIds) {
        if (CollectionUtils.isEmpty(docIds)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Document::getId, docIds);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDocument(Long documentId, String uploader) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getId, documentId)
                .eq(Document::getUploader, uploader);
        Document document = this.getOne(wrapper);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在或无删除权限");
        }
        boolean success = this.removeById(documentId);
        if (success && StringUtils.hasText(document.getFilePath())) {
            try {
                Files.deleteIfExists(Paths.get(document.getFilePath()));
            } catch (IOException e) {
                log.warn("删除物理文件失败: {}", document.getFilePath(), e);
            }
        }
        log.info("文档已删除: id={}", documentId);
        return success;
    }

    @Override
    public Page<Document> getAllDocumentsPage(Integer page, Integer size, Integer status, String uploader) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Document::getStatus, status);
        }
        if (StringUtils.hasText(uploader)) {
            wrapper.eq(Document::getUploader, uploader);
        }
        wrapper.orderByDesc(Document::getUploadTime);
        return this.page(new Page<>(page, size), wrapper);
    }

    @Override
    public Document getDocumentDetail(Long documentId, String uploader) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getId, documentId)
                .eq(Document::getUploader, uploader);
        Document document = this.getOne(wrapper);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在或无访问权限");
        }
        return document;
    }

    @Override
    public List<String> getDocumentPreview(Long documentId, int maxLength) {
        List<DocChunk> chunks = docChunkMapper.selectByDocId(documentId);
        return chunks.stream()
                .limit(5)
                .map(chunk -> {
                    String content = chunk.getContent();
                    if (content.length() > maxLength) {
                        return content.substring(0, maxLength) + "...";
                    }
                    return content;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean restoreDocument(Long documentId, String uploader) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getId, documentId)
                .eq(Document::getUploader, uploader)
                .eq(Document::getDeleted, 1);
        Document document = this.getOne(wrapper);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在或无恢复权限");
        }
        document.setDeleted(0);
        document.setStatus(1);
        return this.updateById(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reprocessDocument(Long documentId, String uploader) {
        Document document = getDocumentDetail(documentId, uploader);
        if (!Objects.equals(document.getStatus(), 2)) {
            throw new IllegalStateException("仅处理失败的文档支持重新处理");
        }
        docChunkMapper.deleteByDocId(documentId);
        milvusSearchRepository.deleteByDocId(documentId);

        document.setStatus(0);
        document.setChunkCount(0);
        document.setErrorMessage(null);
        this.updateById(document);

        CompletableFuture.runAsync(() -> {
            try {
                documentProcessTask.processDocumentStreaming(documentId, document.getFilePath());
            } catch (Exception e) {
                log.error("重新处理文档失败: {}", documentId, e);
                progressService.updateDocumentStatus(
                        documentId, 2, "重新处理异常: " + e.getMessage());
            }
        }, documentTaskExecutor);
    }

    private void validateFile(MultipartFile file) throws DocumentParseException {
        if (file == null || file.isEmpty()) {
            throw new DocumentParseException("文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            throw new DocumentParseException("文件名无效");
        }
        String extension = getFileExtension(fileName);
        boolean allowed = false;
        for (String type : allowedTypes) {
            if (type.equalsIgnoreCase(extension)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new DocumentParseException("不支持的文件类型: " + extension);
        }
        if (file.getSize() > 100 * 1024 * 1024) {
            throw new DocumentParseException("文件大小不得超过100MB");
        }
    }

    private String saveFileToLocal(MultipartFile file) throws IOException {
        Path uploadPath = Paths.get(storagePath);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        String extension = getFileExtension(file.getOriginalFilename());
        String uniqueFileName = UUID.randomUUID() + "_" + System.currentTimeMillis() + "." + extension;
        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        return filePath.toString();
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName != null ? fileName.lastIndexOf('.') : -1;
        if (lastDot >= 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    private DocumentUploadResponseDTO buildUploadResponse(Document document) {
        DocumentUploadResponseDTO response = new DocumentUploadResponseDTO();
        response.setTaskId(document.getId());
        response.setFileName(document.getFileName());
        response.setFileSize(document.getFileSize());
        response.setFileType(document.getFileType());
        response.setStatus(getStatusDesc(document.getStatus()));
        response.setMessage("文档上传成功，正在后台处理中");
        if (document.getUploadTime() != null) {
            response.setUploadTime(document.getUploadTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        return response;
    }

    private DocumentStatusDTO buildStatusDTO(Document document) {
        DocumentStatusDTO dto = new DocumentStatusDTO();
        dto.setDocumentId(document.getId());
        dto.setFileName(document.getFileName());
        dto.setStatus(document.getStatus());
        dto.setStatusDesc(getStatusDesc(document.getStatus()));
        dto.setChunkCount(document.getChunkCount());
        dto.setErrorMessage(document.getErrorMessage());
        if (document.getUploadTime() != null) {
            dto.setUploadTime(document.getUploadTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (document.getUpdateTime() != null) {
            dto.setCompleteTime(document.getUpdateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        Integer progress = progressService.getProgress(document.getId());
        if ((progress == null || progress == 0) && Objects.equals(document.getStatus(), 1)) {
            dto.setProgress(100);
        } else {
            dto.setProgress(progress);
        }
        dto.setTotalChunks(document.getChunkCount());
        return dto;
    }

    private String getStatusDesc(Integer status) {
        return switch (Objects.requireNonNullElse(status, -1)) {
            case 0 -> "处理中";
            case 1 -> "已完成";
            case 2 -> "处理失败";
            case 3 -> "已删除";
            default -> "未知";
        };
    }

    private String resolveUserId(LoginUserDetails user) {
        if (user == null) {
            return "anonymous";
        }
        if (StringUtils.hasText(user.getUserId())) {
            return user.getUserId();
        }
        return user.getUsername();
    }

    private String resolveDepartment(LoginUserDetails user, String requestDepartment) {
        if (StringUtils.hasText(requestDepartment)) {
            return requestDepartment;
        }
        return user != null ? user.getDepartment() : null;
    }
}
