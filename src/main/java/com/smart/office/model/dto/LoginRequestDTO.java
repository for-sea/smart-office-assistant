package com.smart.office.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求参数 DTO。
 */
@Data
public class LoginRequestDTO {

    /**
     * 登录用户名。
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 登录密码（明文，由服务端加密校验）。
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
