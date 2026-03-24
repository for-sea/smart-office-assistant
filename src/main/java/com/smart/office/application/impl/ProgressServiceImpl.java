package com.smart.office.application.impl;

import com.smart.office.domain.document.entity.Document;
import com.smart.office.base.data.mapper.DocumentMapper;
import com.smart.office.application.ProgressService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 进度服务默认实现，负责维护内存态进度并回写数据库状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressServiceImpl implements ProgressService {

    private final DocumentMapper documentMapper;
    private final Map<Long, Integer> progressMap = new ConcurrentHashMap<>();

    @Override
    public void updateProgress(Long documentId, int progress) {
        progressMap.put(documentId, progress);
        if (progress >= 100) {
            progressMap.remove(documentId);
        }
        log.debug("更新文档进度: documentId={}, progress={}", documentId, progress);
    }

    @Override
    public Integer getProgress(Long documentId) {
        return progressMap.getOrDefault(documentId, 0);
    }

    @Override
    public void updateDocumentStatus(Long documentId, Integer status, String errorMessage) {
        Document document = new Document();
        document.setId(documentId);
        document.setStatus(status);
        document.setErrorMessage(errorMessage);
        documentMapper.updateById(document);

        if (status != null && (status == 1 || status == 2)) {
            progressMap.remove(documentId);
        }
        log.info("更新文档状态: documentId={}, status={}, error={}", documentId, status, errorMessage);
    }

    @Override
    public void updateProcessResult(Long documentId, Integer status, Integer chunkCount, Integer processTime) {
        Document document = new Document();
        document.setId(documentId);
        document.setStatus(status);
        document.setChunkCount(chunkCount);
        document.setProcessTime(processTime);
        documentMapper.updateById(document);

        log.info("更新文档处理结果: documentId={}, status={}, chunkCount={}, processTime={}s",
                documentId, status, chunkCount, processTime);
    }
}
