package com.smart.office.controller;

import com.smart.office.common.response.R;
import com.smart.office.model.dto.AuthResponseDTO;
import com.smart.office.model.dto.LoginRequestDTO;
import com.smart.office.model.dto.RegisterRequestDTO;
import com.smart.office.model.vo.UserInfoVO;
import com.smart.office.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录、注册、查询当前用户。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * 登录获取 Token。
     */
    @PostMapping("/login")
    public R<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        log.info("处理登录请求: username={}", request.getUsername());
        AuthResponseDTO response = authService.login(request);
        return R.success(response);
    }

    /**
     * 管理员注册新用户。
     */
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public R<UserInfoVO> register(@Valid @RequestBody RegisterRequestDTO request) {
        log.info("管理员注册用户: username={}", request.getUsername());
        UserInfoVO user = authService.register(request);
        return R.success("注册成功", user);
    }

    /**
     * 查询当前登录用户信息。
     */
    @GetMapping("/me")
    public R<UserInfoVO> currentUser() {
        UserInfoVO current = authService.currentUser();
        return R.success(current);
    }
}
