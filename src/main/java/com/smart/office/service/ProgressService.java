package com.smart.office.service;

import com.smart.office.model.entity.Document;
import com.smart.office.repository.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度管理服务
 * 处理文档进度相关的逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressService {

    private final DocumentMapper documentMapper;

    // 用于存储处理进度
    private final Map<Long, Integer> progressMap = new ConcurrentHashMap<>();

    /**
     * 更新处理进度
     */
    public void updateProgress(Long documentId, int progress) {
        progressMap.put(documentId, progress);
        if (progress >= 100) {
            progressMap.remove(documentId);
        }
        log.debug("文档进度更新: documentId={}, progress={}", documentId, progress);
    }

    /**
     * 获取处理进度
     */
    public Integer getProgress(Long documentId) {
        return progressMap.getOrDefault(documentId, 0);
    }

    /**
     * 更新文档状态
     */
    public void updateDocumentStatus(Long documentId, Integer status, String errorMessage) {
        Document document = new Document();
        document.setId(documentId);
        document.setStatus(status);
        document.setErrorMessage(errorMessage);
        documentMapper.updateById(document);

        // 如果完成或失败，清除进度
        if (status == 1 || status == 2) {
            progressMap.remove(documentId);
        }

        log.info("文档状态更新: documentId={}, status={}, error={}", documentId, status, errorMessage);
    }

    /**
     * 更新文档处理结果
     */
    public void updateProcessResult(Long documentId, Integer status, Integer chunkCount, Integer processTime) {
        Document document = new Document();
        document.setId(documentId);
        document.setStatus(status);
        document.setChunkCount(chunkCount);
        document.setProcessTime(processTime);
        documentMapper.updateById(document);

        log.info("文档处理结果更新: documentId={}, status={}, chunkCount={}, processTime={}s",
                documentId, status, chunkCount, processTime);
    }
}