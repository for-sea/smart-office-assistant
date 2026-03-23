package com.smart.office.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.office.common.response.PageResult;
import com.smart.office.common.response.R;
import com.smart.office.model.dto.*;
import com.smart.office.model.entity.Document;
import com.smart.office.model.vo.*;
import com.smart.office.service.DocumentService;
import com.smart.office.util.FileParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档控制器
 * 提供文档上传、查询、删除、下载等接口
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "文档上传、查询、删除、下载等接口")
public class DocumentController {

        private final DocumentService documentService;
    private final FileParser fileParser;

    @Value("${app.document.storage.path:./data/docs}")
    private String storagePath;

    // 在 DocumentController.java 中修改 uploadDocument 方法

    @PostMapping("/upload")
    @Operation(summary = "上传文档", description = "支持PDF、Word、TXT、Markdown等格式")
    public ResponseEntity<R<DocumentUploadResponseDTO>> uploadDocument(
            @RequestParam("file") @NotNull(message = "文件不能为空") MultipartFile file,
            @RequestParam("uploader") @NotBlank(message = "上传者不能为空") String uploader,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "description", required = false) String description) {

        log.info("收到文档上传请求: fileName={}, uploader={}, size={}",
                file.getOriginalFilename(), uploader, file.getSize());

        try {
            // 验证文件大小
            if (file.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(R.fail("文件大小不能超过50MB"));
            }

            // 验证文件
            if (!fileParser.isSupported(file.getOriginalFilename())) {
                return ResponseEntity.badRequest()
                        .body(R.fail("不支持的文件类型，支持的类型: " + fileParser.getSupportedFormats()));
            }

            // 使用流式上传方法
            DocumentUploadResponseDTO result = documentService.uploadDocumentStreaming(
                    file, uploader, department, description);

            return ResponseEntity.ok(R.success("文档上传成功，正在处理中", result));

        } catch (Exception e) {
            log.error("文档上传失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(R.fail("文档上传失败: " + e.getMessage()));
        }
    }

    /**
     * 获取文档处理状态
     */
    @GetMapping("/{documentId}/status")
    @Operation(summary = "获取文档状态", description = "查询文档处理进度和状态")
    public ResponseEntity<R<DocumentStatusDTO>> getDocumentStatus(
            @PathVariable @Min(1) Long documentId,
            @RequestParam @NotBlank String uploader) {

        try {
            DocumentStatusDTO status = documentService.getDocumentStatus(documentId, uploader);
            return ResponseEntity.ok(R.success(status));
        } catch (RuntimeException e) {
            log.warn("获取文档状态失败: documentId={}, uploader={}", documentId, uploader, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail(e.getMessage()));
        }
    }

    /**
     * 获取用户文档列表（分页）
     */
    @GetMapping("/list")
    @Operation(summary = "获取文档列表", description = "分页获取用户的文档列表")
    public ResponseEntity<R<PageResult<DocumentVO>>> getUserDocuments(
            @RequestParam @NotBlank String uploader,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) Integer size) {

        Page<Document> documentPage = documentService.getUserDocumentsPage(uploader, page, size);

        List<DocumentVO> voList = documentPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult<DocumentVO> pageResult = PageResult.<DocumentVO>builder()
                .records(voList)
                .total(documentPage.getTotal())
                .page(page)
                .size(size)
                .pages(documentPage.getPages())
                .build();

        return ResponseEntity.ok(R.success(pageResult));
    }

    /**
     * 获取所有文档列表（管理员）
     */
    @GetMapping("/admin/list")
    @Operation(summary = "获取所有文档", description = "管理员获取所有文档列表")
    public ResponseEntity<R<PageResult<DocumentVO>>> getAllDocuments(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String uploader) {

        Page<Document> documentPage = documentService.getAllDocumentsPage(page, size, status, uploader);

        List<DocumentVO> voList = documentPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult<DocumentVO> pageResult = PageResult.<DocumentVO>builder()
                .records(voList)
                .total(documentPage.getTotal())
                .page(page)
                .size(size)
                .pages(documentPage.getPages())
                .build();

        return ResponseEntity.ok(R.success(pageResult));
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/{documentId}")
    @Operation(summary = "获取文档详情", description = "获取文档的详细信息")
    public ResponseEntity<R<DocumentDetailVO>> getDocumentDetail(
            @PathVariable @Min(1) Long documentId,
            @RequestParam @NotBlank String uploader) {

        try {
            Document document = documentService.getDocumentDetail(documentId, uploader);
            DocumentDetailVO detailVO = convertToDetailVO(document);
            return ResponseEntity.ok(R.success(detailVO));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail(e.getMessage()));
        }
    }

    /**
     * 下载文档
     */
    @GetMapping("/{documentId}/download")
    @Operation(summary = "下载文档", description = "下载原始文档文件")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable @Min(1) Long documentId,
            @RequestParam @NotBlank String uploader,
            HttpServletRequest request) {

        try {
            Document document = documentService.getDocumentDetail(documentId, uploader);

            // 检查文档是否已处理完成
            if (document.getStatus() != 1) {
                return ResponseEntity.badRequest()
                        .body(null);
            }

            Path filePath = Paths.get(document.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath);
            String fileName = document.getFileName();

            // 处理中文文件名
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            String contentDisposition = "attachment; filename*=UTF-8''" + encodedFileName;

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .body(resource);

        } catch (RuntimeException e) {
            log.error("下载文档失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("下载文档异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 预览文档（返回文本内容）
     */
    @GetMapping("/{documentId}/preview")
    @Operation(summary = "预览文档", description = "预览文档的文本内容")
    public ResponseEntity<R<DocumentPreviewVO>> previewDocument(
            @PathVariable @Min(1) Long documentId,
            @RequestParam @NotBlank String uploader,
            @RequestParam(defaultValue = "500") Integer maxLength) {

        try {
            Document document = documentService.getDocumentDetail(documentId, uploader);

            if (document.getStatus() != 1) {
                return ResponseEntity.badRequest()
                        .body(R.fail("文档尚未处理完成，当前状态: " + getStatusDesc(document.getStatus())));
            }

            // 获取文档的文本块内容
            List<String> previewContent = documentService.getDocumentPreview(documentId, maxLength);

            DocumentPreviewVO previewVO = DocumentPreviewVO.builder()
                    .documentId(documentId)
                    .fileName(document.getFileName())
                    .fileType(document.getFileType())
                    .fileSize(document.getFileSize())
                    .status(document.getStatus())
                    .statusDesc(getStatusDesc(document.getStatus()))
                    .chunkCount(document.getChunkCount())
                    .previewContent(previewContent)
                    .uploadTime(document.getUploadTime())
                    .build();

            return ResponseEntity.ok(R.success(previewVO));

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail(e.getMessage()));
        }
    }

    /**
     * 删除文档（逻辑删除）
     */
    @DeleteMapping("/{documentId}")
    @Operation(summary = "删除文档", description = "逻辑删除文档，可从回收站恢复")
    public ResponseEntity<R<Void>> deleteDocument(
            @PathVariable @Min(1) Long documentId,
            @RequestParam @NotBlank String uploader) {

        try {
            boolean success = documentService.deleteDocument(documentId, uploader);
            if (success) {
                return ResponseEntity.ok(R.success("文档删除成功", null));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(R.fail("文档删除失败"));
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail(e.getMessage()));
        }
    }

    /**
     * 批量删除文档
     */
    @DeleteMapping("/batch")
    @Operation(summary = "批量删除文档", description = "批量逻辑删除文档")
    public ResponseEntity<R<BatchDeleteResultVO>> batchDeleteDocuments(
            @RequestBody @Valid BatchDeleteRequestDTO request) {

        try {
            BatchDeleteResultVO result = documentService.batchDeleteDocuments(
                    request.getDocumentIds(), request.getUploader());
            return ResponseEntity.ok(R.success(result));
        } catch (Exception e) {
            log.error("批量删除失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(R.fail("批量删除失败: " + e.getMessage()));
        }
    }

    /**
     * 恢复文档
     */
    @PutMapping("/{documentId}/restore")
    @Operation(summary = "恢复文档", description = "从回收站恢复文档")
    public ResponseEntity<R<Void>> restoreDocument(
            @PathVariable @Min(1) Long documentId,
            @RequestParam @NotBlank String uploader) {

        try {
            boolean success = documentService.restoreDocument(documentId, uploader);
            if (success) {
                return ResponseEntity.ok(R.success("文档恢复成功", null));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(R.fail("文档恢复失败"));
            }
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail(e.getMessage()));
        }
    }

    /**
     * 重新处理文档
     */
    @PostMapping("/{documentId}/reprocess")
    @Operation(summary = "重新处理文档", description = "对失败的文档重新进行ETL处理")
    public ResponseEntity<R<Void>> reprocessDocument(
            @PathVariable @Min(1) Long documentId,
            @RequestParam @NotBlank String uploader) {

        try {
            documentService.reprocessDocument(documentId, uploader);
            return ResponseEntity.ok(R.success("文档已重新加入处理队列", null));
        } catch (Exception e) {
            log.error("重新处理文档失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(R.fail("重新处理失败: " + e.getMessage()));
        }
    }

    /**
     * 获取文档统计信息
     */
    @GetMapping("/statistics")
    @Operation(summary = "获取文档统计", description = "获取用户的文档统计信息")
    public ResponseEntity<R<DocumentStatisticsVO>> getDocumentStatistics(
            @RequestParam @NotBlank String uploader) {

        try {
            DocumentStatisticsVO statistics = documentService.getDocumentStatistics(uploader);
            return ResponseEntity.ok(R.success(statistics));
        } catch (Exception e) {
            log.error("获取文档统计失败: uploader={}", uploader, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(R.fail("获取统计信息失败: " + e.getMessage()));
        }
    }

    /**
     * 获取支持的文件格式
     */
    @GetMapping("/supported-formats")
    @Operation(summary = "支持的文件格式", description = "获取系统支持的所有文件格式")
    public ResponseEntity<R<Map<String, Object>>> getSupportedFormats() {
        Map<String, Object> result = new HashMap<>();
        result.put("formats", fileParser.getSupportedFormats());
        result.put("extensions", List.of("pdf", "doc", "docx", "txt", "md", "html", "xml", "rtf"));
        return ResponseEntity.ok(R.success(result));
    }

    /**
     * 转换Document实体为VO
     */
    private DocumentVO convertToVO(Document document) {
        return DocumentVO.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .fileType(document.getFileType())
                .status(document.getStatus())
                .statusDesc(getStatusDesc(document.getStatus()))
                .chunkCount(document.getChunkCount())
                .uploadTime(document.getUploadTime())
                .description(document.getDescription())
                .build();
    }

    /**
     * 转换Document实体为详情VO
     */
    private DocumentDetailVO convertToDetailVO(Document document) {
        return DocumentDetailVO.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .filePath(document.getFilePath())
                .fileSize(document.getFileSize())
                .fileType(document.getFileType())
                .uploader(document.getUploader())
                .status(document.getStatus())
                .statusDesc(getStatusDesc(document.getStatus()))
                .chunkCount(document.getChunkCount())
                .processTime(document.getProcessTime())
                .errorMessage(document.getErrorMessage())
                .department(document.getDepartment())
                .description(document.getDescription())
                .uploadTime(document.getUploadTime())
                .updateTime(document.getUpdateTime())
                .build();
    }

    /**
     * 获取状态描述
     */
    private String getStatusDesc(Integer status) {
        switch (status) {
            case 0: return "处理中";
            case 1: return "已完成";
            case 2: return "处理失败";
            case 3: return "已删除";
            default: return "未知";
        }
    }
}