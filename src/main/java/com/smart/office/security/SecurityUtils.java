package com.smart.office.security;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文辅助方法。
 */
@UtilityClass
public class SecurityUtils {

    /**
     * 获取当前登录用户信息。
     */
    public static Optional<LoginUserDetails> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUserDetails details) {
            return Optional.of(details);
        }
        return Optional.empty();
    }
}
