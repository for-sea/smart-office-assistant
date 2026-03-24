package com.smart.office.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smart.office.common.exception.BusinessException;
import com.smart.office.model.dto.AuthResponseDTO;
import com.smart.office.model.dto.LoginRequestDTO;
import com.smart.office.model.dto.RegisterRequestDTO;
import com.smart.office.model.entity.UserPermission;
import com.smart.office.model.vo.UserInfoVO;
import com.smart.office.repository.mapper.UserPermissionMapper;
import com.smart.office.security.JwtTokenProvider;
import com.smart.office.security.LoginUserDetails;
import com.smart.office.security.SecurityUtils;
import com.smart.office.service.AuthService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 认证服务实现，封装登录、注册、查询当前用户等逻辑。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserPermissionMapper userPermissionMapper;

    @Override
    public AuthResponseDTO login(LoginRequestDTO request) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(), request.getPassword());
        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        if (!(authentication.getPrincipal() instanceof LoginUserDetails details)) {
            throw new BusinessException("无法获取登录用户信息");
        }

        String token = jwtTokenProvider.generateToken(details);
        long expireAt = System.currentTimeMillis() + jwtTokenProvider.getExpireSeconds() * 1000;

        AuthResponseDTO response = new AuthResponseDTO();
        response.setToken(token);
        response.setExpireAt(expireAt);
        response.setUser(buildUserInfo(details));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO register(RegisterRequestDTO request) {
        if (!StringUtils.hasText(request.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }

        // 校验用户名是否重复
        LambdaQueryWrapper<UserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPermission::getUsername, request.getUsername());
        Long exists = userPermissionMapper.selectCount(wrapper);
        if (exists != null && exists > 0) {
            throw new BusinessException("用户名已存在: " + request.getUsername());
        }

        UserPermission entity = new UserPermission();
        entity.setUsername(request.getUsername());
        entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        entity.setUserId(StringUtils.hasText(request.getUserId())
                ? request.getUserId()
                : UUID.randomUUID().toString());
        entity.setDepartment(request.getDepartment());
        entity.setGroupCode(request.getGroupCode());
        entity.setRole(StringUtils.hasText(request.getRole()) ? request.getRole() : "USER");
        entity.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);

        int inserted = userPermissionMapper.insert(entity);
        log.info("新用户注册完成: username={}, rows={}", request.getUsername(), inserted);

        LoginUserDetails details = LoginUserDetails.from(entity);
        return buildUserInfo(details);
    }

    @Override
    public UserInfoVO currentUser() {
        return SecurityUtils.getCurrentUser()
                .map(this::buildUserInfo)
                .orElseThrow(() -> new BusinessException(401, "未登录或登录已过期"));
    }

    private UserInfoVO buildUserInfo(LoginUserDetails details) {
        if (details == null) {
            return null;
        }
        return UserInfoVO.builder()
                .userId(details.getUserId())
                .username(details.getUsername())
                .department(details.getDepartment())
                .groupCode(details.getGroupCode())
                .role(details.getRole())
                .enabled(details.isEnabled())
                .build();
    }
}
