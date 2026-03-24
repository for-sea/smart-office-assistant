package com.smart.office.model.dto;

import java.util.List;
import lombok.Data;

/**
 * 文档权限更新 DTO，用于批量配置部门/用户组/个人访问权。
 */
@Data
public class DocumentPermissionDTO {

    /**
     * 允许访问的部门编码列表。
     */
    private List<String> departmentCodes;

    /**
     * 允许访问的用户组编码列表。
     */
    private List<String> groupCodes;

    /**
     * 指定额外授权的用户 ID 列表。
     */
    private List<String> userIds;

    /**
     * 是否继承上传者所属部门，默认 true。
     */
    private Boolean inheritUploaderDepartment = Boolean.TRUE;
}
