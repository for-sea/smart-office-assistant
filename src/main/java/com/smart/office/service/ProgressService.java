package com.smart.office.service;

/**
 * 文档处理进度抽象定义，统一约束状态与进度的更新能力。
 */
public interface ProgressService {

    /**
     * 更新指定文档的当前进度百分比。
     */
     void updateProgress(Long documentId, int progress);

    /**
     * 查询文档的当前进度。
     */
     Integer getProgress(Long documentId);

    /**
     * 更新文档处理状态及错误信息。
     */
     void updateDocumentStatus(Long documentId, Integer status, String errorMessage);

    /**
     * 更新文档处理结果，包括分块数量与耗时。
     */
     void updateProcessResult(Long documentId, Integer status, Integer chunkCount, Integer processTime);
}
