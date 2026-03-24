package com.smart.office.model.dto;

import com.smart.office.model.vo.UserInfoVO;
import lombok.Data;

/**
 * 登录成功后返回的认证信息。
 */
@Data
public class AuthResponseDTO {

    /**
     * JWT 或其他令牌字符串。
     */
    private String token;

    /**
     * 令牌过期的时间戳（毫秒）。
     */
    private Long expireAt;

    /**
     * 当前登录用户信息。
     */
    private UserInfoVO user;
}
