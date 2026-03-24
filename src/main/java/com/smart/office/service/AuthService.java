package com.smart.office.service;

import com.smart.office.model.dto.AuthResponseDTO;
import com.smart.office.model.dto.DocumentPermissionDTO;
import com.smart.office.model.dto.LoginRequestDTO;
import com.smart.office.model.dto.RegisterRequestDTO;
import com.smart.office.model.vo.UserInfoVO;

/**
 * 认证与权限相关的服务接口，占位定义，后续可由具体实现类完成。
 */
public interface AuthService {

    /**
     * 处理登录请求，返回令牌与用户信息。
     *
     * @param request 登录参数
     * @return 认证结果
     */
    AuthResponseDTO login(LoginRequestDTO request);

    /**
     * 注册新用户。
     *
     * @param request 注册参数
     * @return 新用户信息
     */
    UserInfoVO register(RegisterRequestDTO request);

    /**
     * 查询当前登录用户信息。
     *
     * @return 用户信息
     */
    UserInfoVO currentUser();

}
