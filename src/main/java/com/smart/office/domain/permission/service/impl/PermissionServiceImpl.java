package com.smart.office.domain.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.office.interfaces.dto.DocumentPermissionDTO;
import com.smart.office.domain.document.entity.Document;
import com.smart.office.domain.document.entity.DocumentDept;
import com.smart.office.domain.document.entity.DocumentGroup;
import com.smart.office.interfaces.vo.DocumentPermissionVO;
import com.smart.office.base.data.mapper.DocumentDeptMapper;
import com.smart.office.base.data.mapper.DocumentGroupMapper;
import com.smart.office.base.data.mapper.DocumentMapper;
import com.smart.office.shared.security.LoginUserDetails;
import com.smart.office.domain.permission.service.PermissionService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 文档权限计算与维护实现。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    private final DocumentMapper documentMapper;
    private final DocumentDeptMapper documentDeptMapper;
    private final DocumentGroupMapper documentGroupMapper;

    @Override
    public List<Long> getReadableDocumentIds(LoginUserDetails user) {
        if (user == null) {
            return List.of();
        }
        if (isAdmin(user)) {
            LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(Document::getId);
            return documentMapper.selectList(wrapper).stream()
                    .map(Document::getId)
                    .collect(Collectors.toList());
        }

        Set<Long> docIds = new HashSet<>();
        String userId = resolveUserId(user);
        if (StringUtils.hasText(userId)) {
            LambdaQueryWrapper<Document> ownerWrapper = new LambdaQueryWrapper<>();
            ownerWrapper.eq(Document::getUploader, userId)
                    .select(Document::getId);
            documentMapper.selectList(ownerWrapper)
                    .forEach(doc -> docIds.add(doc.getId()));
        }

        if (StringUtils.hasText(user.getDepartment())) {
            LambdaQueryWrapper<Document> deptWrapper = new LambdaQueryWrapper<>();
            deptWrapper.eq(Document::getDepartment, user.getDepartment())
                    .select(Document::getId);
            documentMapper.selectList(deptWrapper)
                    .forEach(doc -> docIds.add(doc.getId()));

            docIds.addAll(documentDeptMapper.selectDocIdsByDepartments(
                    List.of(user.getDepartment())));
        }

        if (StringUtils.hasText(user.getGroupCode())) {
            docIds.addAll(documentGroupMapper.selectDocIdsByGroupCodes(
                    List.of(user.getGroupCode())));
        }

        return new ArrayList<>(docIds);
    }

    @Override
    public boolean canReadDocument(Long documentId, LoginUserDetails user) {
        if (documentId == null || user == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            return false;
        }
        String userId = resolveUserId(user);
        if (StringUtils.hasText(userId) && userId.equals(document.getUploader())) {
            return true;
        }

        if (StringUtils.hasText(user.getDepartment())) {
            if (user.getDepartment().equals(document.getDepartment())) {
                return true;
            }
            List<String> departments = documentDeptMapper.selectDepartmentsByDocId(documentId);
            if (departments.contains(user.getDepartment())) {
                return true;
            }
        }

        if (StringUtils.hasText(user.getGroupCode())) {
            List<String> groupCodes = documentGroupMapper.selectGroupCodesByDocId(documentId);
            if (groupCodes.contains(user.getGroupCode())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canManageDocument(Long documentId, LoginUserDetails user) {
        if (documentId == null || user == null) {
            return false;
        }
        if (isAdmin(user)) {
            return true;
        }
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            return false;
        }
        String userId = resolveUserId(user);
        return StringUtils.hasText(userId) && userId.equals(document.getUploader());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyDocumentPermissions(Long documentId,
                                         DocumentPermissionDTO permissionDTO,
                                         LoginUserDetails operator) {
        if (documentId == null) {
            return;
        }

        DocumentPermissionDTO effectiveDto = permissionDTO != null
                ? permissionDTO
                : new DocumentPermissionDTO();

        List<String> departments = new ArrayList<>();
        if (Boolean.TRUE.equals(effectiveDto.getInheritUploaderDepartment())
                && operator != null && StringUtils.hasText(operator.getDepartment())) {
            departments.add(operator.getDepartment());
        }
        if (!CollectionUtils.isEmpty(effectiveDto.getDepartmentCodes())) {
            departments.addAll(
                    effectiveDto.getDepartmentCodes().stream()
                            .filter(StringUtils::hasText)
                            .collect(Collectors.toList()));
        }

        List<String> groupCodes = new ArrayList<>();
        if (!CollectionUtils.isEmpty(effectiveDto.getGroupCodes())) {
            groupCodes.addAll(
                    effectiveDto.getGroupCodes().stream()
                            .filter(StringUtils::hasText)
                            .collect(Collectors.toList()));
        }

        // 去重
        departments = departments.stream().filter(StringUtils::hasText).distinct().collect(Collectors.toList());
        groupCodes = groupCodes.stream().filter(StringUtils::hasText).distinct().collect(Collectors.toList());

        documentDeptMapper.deleteByDocId(documentId);
        for (String dept : departments) {
            DocumentDept entity = new DocumentDept();
            entity.setDocId(documentId);
            entity.setDepartment(dept);
            documentDeptMapper.insert(entity);
        }

        documentGroupMapper.deleteByDocId(documentId);
        for (String group : groupCodes) {
            DocumentGroup entity = new DocumentGroup();
            entity.setDocId(documentId);
            entity.setGroupCode(group);
            documentGroupMapper.insert(entity);
        }

        log.info("文档权限已更新: docId={}, departments={}, groups={}",
                documentId, departments, groupCodes);
    }

    @Override
    public DocumentPermissionVO getDocumentPermissions(Long documentId) {
        List<String> departments = documentDeptMapper.selectDepartmentsByDocId(documentId);
        List<String> groupCodes = documentGroupMapper.selectGroupCodesByDocId(documentId);
        return DocumentPermissionVO.builder()
                .documentId(documentId)
                .departments(departments)
                .groupCodes(groupCodes)
                .build();
    }

    @Override
    public boolean isAdmin(LoginUserDetails user) {
        return user != null
                && StringUtils.hasText(user.getRole())
                && "ADMIN".equalsIgnoreCase(user.getRole());
    }

    private String resolveUserId(LoginUserDetails user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getUserId())) {
            return user.getUserId();
        }
        return user.getUsername();
    }
}
