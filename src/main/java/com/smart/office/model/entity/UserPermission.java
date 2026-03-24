package com.smart.office.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户权限实体，对应 user_permission 表。
 */
@Data
@TableName("user_permission")
public class UserPermission {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务系统中的用户标识，可与外部账号映射。
     */
    @TableField("user_id")
    private String userId;

    /**
     * 登录用户名，用于认证。
     */
    private String username;

    /**
     * BCrypt 加密后的密码。
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 所属部门编码。
     */
    private String department;

    /**
     * 所属用户组编码。
     */
    @TableField("group_code")
    private String groupCode;

    /**
     * 角色标识，例如 ADMIN / USER。
     */
    private String role;

    /**
     * 账号是否启用，1 表示启用，0 表示禁用。
     */
    private Integer enabled;

    /**
     * 创建时间。
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
