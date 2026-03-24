package com.smart.office.domain.document.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smart.office.shared.common.DocumentParseException;
import com.smart.office.interfaces.dto.DocumentPermissionDTO;
import com.smart.office.interfaces.dto.DocumentStatusDTO;
import com.smart.office.interfaces.dto.DocumentUploadResponseDTO;
import com.smart.office.domain.document.entity.Document;
import com.smart.office.shared.security.LoginUserDetails;
import java.io.IOException;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档服务抽象定义，统一对上传、查询与权限控制相关的核心能力。
 */
public interface DocumentService extends IService<Document> {

    /**
     * 依据登录用户信息上传文档并触发权限策略。
     */
    public abstract DocumentUploadResponseDTO uploadDocumentStreaming(MultipartFile file,
                                                                      LoginUserDetails currentUser,
                                                                      String department,
                                                                      String description,
                                                                      DocumentPermissionDTO permissionDTO)
            throws DocumentParseException, IOException;

    /**
     * 查询文档最新处理状态。
     */
    public abstract DocumentStatusDTO getDocumentStatus(Long documentId, String uploader);

    /**
     * 根据可访问文档 ID 进行分页查询。
     */
    public abstract Page<Document> getDocumentsPageByIds(List<Long> docIds, int current, int size);

    /**
     * 批量拉取文档列表。
     */
    public abstract List<Document> listDocumentsByIds(List<Long> docIds);

    /**
     * 按上传者校验后删除文档。
     */
    public abstract boolean deleteDocument(Long documentId, String uploader);

    /**
     * 管理员视角分页查询所有文档。
     */
    public abstract Page<Document> getAllDocumentsPage(Integer page, Integer size, Integer status, String uploader);

    /**
     * 查询文档详情。
     */
    public abstract Document getDocumentDetail(Long documentId, String uploader);

    /**
     * 获取文档预览内容。
     */
    public abstract List<String> getDocumentPreview(Long documentId, int maxLength);

    /**
     * 恢复被逻辑删除的文档。
     */
    public abstract boolean restoreDocument(Long documentId, String uploader);

    /**
     * 重新处理失败文档。
     */
    public abstract void reprocessDocument(Long documentId, String uploader);
}
