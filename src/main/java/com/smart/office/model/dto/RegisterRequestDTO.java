package com.smart.office.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求参数 DTO，支持快速创建内部账号。
 */
@Data
public class RegisterRequestDTO {

    /**
     * 登录用户名。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 100, message = "用户名长度不能超过100个字符")
    private String username;

    /**
     * 密码明文，后续会由服务端加密存储。
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 业务用户 ID，可用于绑定外部系统。
     */
    private String userId;

    /**
     * 所属部门编码。
     */
    private String department;

    /**
     * 所属用户组编码。
     */
    private String groupCode;

    /**
     * 指定角色，默认可为 USER。
     */
    private String role;

    /**
     * 是否启用账号，不传则默认为 true。
     */
    private Boolean enabled;
}
