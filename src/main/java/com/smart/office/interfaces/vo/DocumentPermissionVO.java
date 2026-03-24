package com.smart.office.interfaces.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 文档权限展示 VO，返回可访问的部门与用户组。
 */
@Data
@Builder
public class DocumentPermissionVO {

    private Long documentId;

    private List<String> departments;

    private List<String> groupCodes;
}
