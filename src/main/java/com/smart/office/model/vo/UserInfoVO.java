package com.smart.office.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 返回给前端的用户信息 VO。
 */
@Data
@Builder
public class UserInfoVO {

    /**
     * 业务用户 ID。
     */
    private String userId;

    /**
     * 登录用户名。
     */
    private String username;

    /**
     * 所属部门编码。
     */
    private String department;

    /**
     * 所属用户组编码。
     */
    private String groupCode;

    /**
     * 当前角色。
     */
    private String role;

    /**
     * 账号是否启用。
     */
    private Boolean enabled;
}
