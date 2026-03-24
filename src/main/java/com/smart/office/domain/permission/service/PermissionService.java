package com.smart.office.domain.permission.service;

import com.smart.office.interfaces.dto.DocumentPermissionDTO;
import com.smart.office.interfaces.vo.DocumentPermissionVO;
import com.smart.office.shared.security.LoginUserDetails;
import java.util.List;

/**
 * 文档访问权限服务。
 */
public interface PermissionService {

    /**
     * 计算当前用户可访问的文档 ID 列表。
     */
    List<Long> getReadableDocumentIds(LoginUserDetails user);

    /**
     * 判断用户是否有权读取指定文档。
     */
    boolean canReadDocument(Long documentId, LoginUserDetails user);

    /**
     * 判断用户是否有权管理（删除/修改权限）文档。
     */
    boolean canManageDocument(Long documentId, LoginUserDetails user);

    /**
     * 更新文档的部门/用户组权限，空配置时默认继承上传者所属部门。
     */
    void applyDocumentPermissions(Long documentId,
                                  DocumentPermissionDTO permissionDTO,
                                  LoginUserDetails operator);

    /**
     * 查询指定文档的权限信息。
     */
    DocumentPermissionVO getDocumentPermissions(Long documentId);

    /**
     * 判断当前用户是否为管理员。
     */
    boolean isAdmin(LoginUserDetails user);
}
