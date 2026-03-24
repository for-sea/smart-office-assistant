package com.smart.office.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smart.office.common.response.PageResult;
import com.smart.office.common.response.R;
import com.smart.office.model.dto.BatchDeleteRequestDTO;
import com.smart.office.model.dto.DocumentPermissionDTO;
import com.smart.office.model.dto.DocumentStatusDTO;
import com.smart.office.model.dto.DocumentUploadResponseDTO;
import com.smart.office.model.entity.Document;
import com.smart.office.model.vo.BatchDeleteResultVO;
import com.smart.office.model.vo.DocumentDetailVO;
import com.smart.office.model.vo.DocumentPermissionVO;
import com.smart.office.model.vo.DocumentPreviewVO;
import com.smart.office.model.vo.DocumentStatisticsVO;
import com.smart.office.model.vo.DocumentVO;
import com.smart.office.security.LoginUserDetails;
import com.smart.office.service.DocumentService;
import com.smart.office.service.PermissionService;
import com.smart.office.util.FileParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档管理接口：上传、查询、下载、权限维护等。
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "文档上传、查询、删除、权限管理等接口")
@Slf4j
public class DocumentController {

    private final DocumentService documentService;
    private final FileParser fileParser;
    private final PermissionService permissionService;

    @PostMapping("/upload")
    @Operation(summary = "上传文档", description = "支持 PDF/Word/TXT/Markdown 等格式")
    public ResponseEntity<R<DocumentUploadResponseDTO>> uploadDocument(
            @AuthenticationPrincipal LoginUserDetails currentUser,
            @RequestParam("file") @NotNull(message = "文件不能为空") MultipartFile file,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "description", required = false) String description) {

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(R.fail(401, "未登录，无法上传文档"));
        }

        log.info("收到文档上传请求: fileName={}, user={}, size={}",
                file.getOriginalFilename(),
                currentUser.getUsername(),
                file.getSize());

        try {
            if (file.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity.badRequest()
                        .body(R.fail("文件大小不能超过50MB"));
            }

            if (!fileParser.isSupported(file.getOriginalFilename())) {
                return ResponseEntity.badRequest()
                        .body(R.fail("不支持的文件类型，支持: " + fileParser.getSupportedFormats()));
            }

            DocumentPermissionDTO permissionDTO = new DocumentPermissionDTO();
            if (StringUtils.hasText(department)) {
                permissionDTO.setDepartmentCodes(List.of(department));
            }

            DocumentUploadResponseDTO result = documentService.uploadDocumentStreaming(
                    file, currentUser, department, description, permissionDTO);
            return ResponseEntity.ok(R.success("文档上传成功，正在处理", result));
        } catch (Exception e) {
            log.error("文档上传失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(R.fail("文档上传失败: " + e.getMessage()));
        }
    }

    @GetMapping("/{documentId}/status")
    @Operation(summary = "获取文档状态", description = "查询文档处理进度")
    public ResponseEntity<R<DocumentStatusDTO>> getDocumentStatus(
            @PathVariable @Min(1) Long documentId,
            @AuthenticationPrincipal LoginUserDetails currentUser) {

        Document document = documentService.getById(documentId);
        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail("文档不存在"));
        }
        if (!permissionService.canReadDocument(documentId, currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(R.fail(403, "没有权限访问该文档"));
        }

        DocumentStatusDTO status = documentService.getDocumentStatus(documentId, document.getUploader());
        return ResponseEntity.ok(R.success(status));
    }

    @GetMapping("/list")
    @Operation(summary = "获取可访问文档列表", description = "根据权限返回文档分页列表")
    public ResponseEntity<R<PageResult<DocumentVO>>> getUserDocuments(
            @AuthenticationPrincipal LoginUserDetails currentUser,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) Integer size) {

        List<Long> accessibleDocIds = permissionService.getReadableDocumentIds(currentUser);
        Page<Document> documentPage = documentService.getDocumentsPageByIds(accessibleDocIds, page, size);

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

    @GetMapping("/admin/list")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员获取全部文档", description = "支持按状态、上传者筛选")
    public ResponseEntity<R<PageResult<DocumentVO>>> getAllDocuments(
            @AuthenticationPrincipal LoginUserDetails currentUser,
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

    @GetMapping("/{documentId}")
    @Operation(summary = "获取文档详情")
    public ResponseEntity<R<DocumentDetailVO>> getDocumentDetail(
            @PathVariable @Min(1) Long documentId,
            @AuthenticationPrincipal LoginUserDetails currentUser) {

        Document document = documentService.getById(documentId);
        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail("文档不存在"));
        }
        if (!permissionService.canReadDocument(documentId, currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(R.fail(403, "没有权限访问该文档"));
        }
        return ResponseEntity.ok(R.success(convertToDetailVO(document)));
    }

    @GetMapping("/{documentId}/download")
    @Operation(summary = "下载文档")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable @Min(1) Long documentId,
            @AuthenticationPrincipal LoginUserDetails currentUser) {

        Document document = documentService.getById(documentId);
        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!permissionService.canReadDocument(documentId, currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (document.getStatus() == null || document.getStatus() != 1) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path filePath = Paths.get(document.getFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath);
            String fileName = document.getFileName();
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
            String contentDisposition = "attachment; filename*=UTF-8''" + encodedFileName;

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .body(resource);
        } catch (Exception e) {
            log.error("下载文档异常: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{documentId}/preview")
    @Operation(summary = "预览文档内容")
    public ResponseEntity<R<DocumentPreviewVO>> previewDocument(
            @PathVariable @Min(1) Long documentId,
            @AuthenticationPrincipal LoginUserDetails currentUser,
            @RequestParam(defaultValue = "500") Integer maxLength) {

        Document document = documentService.getById(documentId);
        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail("文档不存在"));
        }
        if (!permissionService.canReadDocument(documentId, currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(R.fail(403, "没有权限访问该文档"));
        }
        if (document.getStatus() == null || document.getStatus() != 1) {
            return ResponseEntity.badRequest()
                    .body(R.fail("文档尚未处理完成，当前状态: " + getStatusDesc(document.getStatus())));
        }

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
    }

    @DeleteMapping("/{documentId}")
    @Operation(summary = "删除文档", description = "逻辑删除，可在回收站恢复")
    public ResponseEntity<R<Void>> deleteDocument(
            @PathVariable @Min(1) Long documentId,
            @AuthenticationPrincipal LoginUserDetails currentUser) {

        Document document = documentService.getById(documentId);
        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail("文档不存在"));
        }
        if (!permissionService.canManageDocument(documentId, currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(R.fail(403, "没有删除权限"));
        }

        boolean success = documentService.deleteDocument(documentId, document.getUploader());
        if (success) {
            return ResponseEntity.ok(R.success("文档删除成功", null));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail("文档删除失败"));
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除文档", description = "根据权限删除多个文档")
    public ResponseEntity<R<BatchDeleteResultVO>> batchDeleteDocuments(
            @AuthenticationPrincipal LoginUserDetails currentUser,
            @RequestBody @Valid BatchDeleteRequestDTO request) {

        List<Long> failedIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int successCount = 0;

        for (Long documentId : request.getDocumentIds()) {
            Document document = documentService.getById(documentId);
            if (document == null) {
                failedIds.add(documentId);
                errors.add("文档ID " + documentId + " 不存在");
                continue;
            }
            if (!permissionService.canManageDocument(documentId, currentUser)) {
                failedIds.add(documentId);
                errors.add("文档ID " + documentId + " 没有删除权限");
                continue;
            }
            try {
                if (documentService.deleteDocument(documentId, document.getUploader())) {
                    successCount++;
                } else {
                    failedIds.add(documentId);
                    errors.add("文档ID " + documentId + " 删除失败");
                }
            } catch (Exception e) {
                failedIds.add(documentId);
                errors.add("文档ID " + documentId + " 删除异常: " + e.getMessage());
            }
        }

        BatchDeleteResultVO result = BatchDeleteResultVO.builder()
                .successCount(successCount)
                .failCount(failedIds.size())
                .failedIds(failedIds)
                .errors(errors)
                .build();

        return ResponseEntity.ok(R.success(result));
    }

    @PutMapping("/{documentId}/restore")
    @Operation(summary = "恢复文档")
    public ResponseEntity<R<Void>> restoreDocument(
            @PathVariable @Min(1) Long documentId,
            @AuthenticationPrincipal LoginUserDetails currentUser) {

        Document document = documentService.getById(documentId);
        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail("文档不存在"));
        }
        if (!permissionService.canManageDocument(documentId, currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(R.fail(403, "没有操作权限"));
        }

        boolean success = documentService.restoreDocument(documentId, document.getUploader());
        if (success) {
            return ResponseEntity.ok(R.success("文档恢复成功", null));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail("文档恢复失败"));
    }

    @PostMapping("/{documentId}/reprocess")
    @Operation(summary = "重新处理文档", description = "针对处理失败的文档重新入库")
    public ResponseEntity<R<Void>> reprocessDocument(
            @PathVariable @Min(1) Long documentId,
            @AuthenticationPrincipal LoginUserDetails currentUser) {

        Document document = documentService.getById(documentId);
        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail("文档不存在"));
        }
        if (!permissionService.canManageDocument(documentId, currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(R.fail(403, "没有操作权限"));
        }

        try {
            documentService.reprocessDocument(documentId, document.getUploader());
            return ResponseEntity.ok(R.success("文档已重新加入处理队列", null));
        } catch (Exception e) {
            log.error("重新处理文档失败: documentId={}", documentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(R.fail("重新处理失败: " + e.getMessage()));
        }
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取文档统计")
    public ResponseEntity<R<DocumentStatisticsVO>> getDocumentStatistics(
            @AuthenticationPrincipal LoginUserDetails currentUser) {

        List<Long> docIds = permissionService.getReadableDocumentIds(currentUser);
        List<Document> documents = documentService.listDocumentsByIds(docIds);
        DocumentStatisticsVO statistics = buildStatistics(documents);
        return ResponseEntity.ok(R.success(statistics));
    }

    @PutMapping("/{documentId}/permissions")
    @Operation(summary = "更新文档权限", description = "配置允许访问的部门/用户组")
    public ResponseEntity<R<Void>> updateDocumentPermissions(
            @PathVariable @Min(1) Long documentId,
            @AuthenticationPrincipal LoginUserDetails currentUser,
            @RequestBody DocumentPermissionDTO permissionDTO) {

        Document document = documentService.getById(documentId);
        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail("文档不存在"));
        }
        if (!permissionService.canManageDocument(documentId, currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(R.fail(403, "没有权限更新该文档"));
        }
        permissionService.applyDocumentPermissions(documentId, permissionDTO, currentUser);
        return ResponseEntity.ok(R.success("权限更新成功", null));
    }

    @GetMapping("/{documentId}/permissions")
    @Operation(summary = "获取文档权限详情")
    public ResponseEntity<R<DocumentPermissionVO>> getDocumentPermissions(
            @PathVariable @Min(1) Long documentId,
            @AuthenticationPrincipal LoginUserDetails currentUser) {

        Document document = documentService.getById(documentId);
        if (document == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(R.fail("文档不存在"));
        }
        if (!permissionService.canManageDocument(documentId, currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(R.fail(403, "没有权限查看该文档的权限配置"));
        }
        DocumentPermissionVO vo = permissionService.getDocumentPermissions(documentId);
        return ResponseEntity.ok(R.success(vo));
    }

    @GetMapping("/supported-formats")
    @Operation(summary = "支持的文件格式")
    public ResponseEntity<R<Map<String, Object>>> getSupportedFormats() {
        Map<String, Object> result = new HashMap<>();
        result.put("formats", fileParser.getSupportedFormats());
        result.put("extensions", List.of("pdf", "doc", "docx", "txt", "md", "html", "xml", "rtf"));
        return ResponseEntity.ok(R.success(result));
    }

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

    private String getStatusDesc(Integer status) {
        return switch (Objects.requireNonNullElse(status, -1)) {
            case 0 -> "处理中";
            case 1 -> "已完成";
            case 2 -> "处理失败";
            case 3 -> "已删除";
            default -> "未知";
        };
    }

    private DocumentStatisticsVO buildStatistics(List<Document> documents) {
        long totalCount = documents.size();
        long processingCount = documents.stream().filter(doc -> doc.getStatus() != null && doc.getStatus() == 0).count();
        long completedCount = documents.stream().filter(doc -> doc.getStatus() != null && doc.getStatus() == 1).count();
        long failedCount = documents.stream().filter(doc -> doc.getStatus() != null && doc.getStatus() == 2).count();
        long deletedCount = documents.stream().filter(doc -> doc.getStatus() != null && doc.getStatus() == 3).count();
        long totalChunks = documents.stream()
                .filter(doc -> doc.getStatus() != null && doc.getStatus() == 1)
                .mapToLong(doc -> doc.getChunkCount() != null ? doc.getChunkCount() : 0)
                .sum();
        long totalSize = documents.stream()
                .mapToLong(doc -> doc.getFileSize() != null ? doc.getFileSize() : 0)
                .sum();

        long hrCount = documents.stream()
                .filter(doc -> "HR".equalsIgnoreCase(doc.getDepartment()))
                .count();
        long itCount = documents.stream()
                .filter(doc -> "IT".equalsIgnoreCase(doc.getDepartment()))
                .count();
        long rdCount = documents.stream()
                .filter(doc -> "R&D".equalsIgnoreCase(doc.getDepartment()))
                .count();
        long otherCount = documents.stream()
                .filter(doc -> {
                    String dept = doc.getDepartment();
                    return !StringUtils.hasText(dept)
                            || (!"HR".equalsIgnoreCase(dept)
                            && !"IT".equalsIgnoreCase(dept)
                            && !"R&D".equalsIgnoreCase(dept));
                })
                .count();

        DocumentStatisticsVO.DetailedStatistics detailed = DocumentStatisticsVO.DetailedStatistics.builder()
                .byDepartment(new DocumentStatisticsVO.DepartmentStatistics(hrCount, itCount, rdCount, otherCount))
                .build();

        return DocumentStatisticsVO.builder()
                .totalCount(totalCount)
                .processingCount(processingCount)
                .completedCount(completedCount)
                .failedCount(failedCount)
                .deletedCount(deletedCount)
                .totalChunks(totalChunks)
                .totalSize(totalSize)
                .totalSizeFormatted(formatFileSize(totalSize))
                .detailed(detailed)
                .build();
    }

    private String formatFileSize(long size) {
        if (size <= 0) {
            return "0 B";
        }
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        DecimalFormat df = new DecimalFormat("#.##");
        double value = size / Math.pow(1024, digitGroups);
        return df.format(value) + " " + units[digitGroups];
    }
}
