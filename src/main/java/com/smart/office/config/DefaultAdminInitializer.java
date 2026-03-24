package com.smart.office.config;

import com.smart.office.model.entity.UserPermission;
import com.smart.office.repository.mapper.UserPermissionMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 启动时在用户表为空的情况下初始化默认管理员账号。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultAdminInitializer implements CommandLineRunner {

    private final UserPermissionMapper userPermissionMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.admin.username:admin}")
    private String defaultUsername;

    @Value("${app.auth.admin.password:admin123}")
    private String defaultPassword;

    @Value("${app.auth.admin.user-id:admin}")
    private String defaultUserId;

    @Value("${app.auth.admin.department:ADMIN}")
    private String defaultDepartment;

    @Value("${app.auth.admin.group-code:CORE}")
    private String defaultGroupCode;

    @Override
    public void run(String... args) {
        Long count = userPermissionMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("用户表已有 {} 条记录，跳过默认管理员创建", count);
            return;
        }

        if (!StringUtils.hasText(defaultUsername) || !StringUtils.hasText(defaultPassword)) {
            log.warn("未配置默认管理员账号或密码，跳过初始化");
            return;
        }

        UserPermission admin = new UserPermission();
        admin.setUsername(defaultUsername);
        admin.setPasswordHash(passwordEncoder.encode(defaultPassword));
        admin.setUserId(StringUtils.hasText(defaultUserId) ? defaultUserId : UUID.randomUUID().toString());
        admin.setDepartment(defaultDepartment);
        admin.setGroupCode(defaultGroupCode);
        admin.setRole("ADMIN");
        admin.setEnabled(1);

        userPermissionMapper.insert(admin);
        log.info("默认管理员账号已创建: username={}", defaultUsername);
    }
}
