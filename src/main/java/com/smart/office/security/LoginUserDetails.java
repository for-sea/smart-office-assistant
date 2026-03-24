package com.smart.office.security;

import com.smart.office.model.entity.UserPermission;
import java.util.Collection;
import java.util.Collections;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 登录用户信息封装，实现 Spring Security 的 UserDetails 接口。
 */
@Getter
@Slf4j
public class LoginUserDetails implements UserDetails {

    private final Long id;
    private final String userId;
    private final String username;
    private final String password;
    private final String department;
    private final String groupCode;
    private final String role;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public LoginUserDetails(Long id,
                            String userId,
                            String username,
                            String password,
                            String department,
                            String groupCode,
                            String role,
                            boolean enabled) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.department = department;
        this.groupCode = groupCode;
        this.role = role;
        this.enabled = enabled;
        String roleKey = role != null ? role : "USER";
        this.authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + roleKey.toUpperCase()));
    }

    /**
     * 依据数据库实体快速构建 UserDetails。
     */
    public static LoginUserDetails from(UserPermission entity) {
        if (entity == null) {
            log.warn("尝试从空的 UserPermission 构建 LoginUserDetails");
            return null;
        }
        return new LoginUserDetails(
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getDepartment(),
                entity.getGroupCode(),
                entity.getRole(),
                entity.getEnabled() == null || entity.getEnabled() == 1
        );
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
