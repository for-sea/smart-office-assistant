package com.smart.office.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.office.model.entity.UserPermission;
import com.smart.office.repository.mapper.UserPermissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 自定义用户信息服务，从数据库加载登录用户。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserPermissionMapper userPermissionMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("加载登录用户信息: username={}", username);
        LambdaQueryWrapper<UserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPermission::getUsername, username).last("LIMIT 1");
        UserPermission entity = userPermissionMapper.selectOne(wrapper);
        if (entity == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        if (entity.getEnabled() != null && entity.getEnabled() == 0) {
            throw new UsernameNotFoundException("账号已被禁用: " + username);
        }
        LoginUserDetails details = LoginUserDetails.from(entity);
        if (details == null) {
            throw new UsernameNotFoundException("无法构建用户信息: " + username);
        }
        return details;
    }
}
