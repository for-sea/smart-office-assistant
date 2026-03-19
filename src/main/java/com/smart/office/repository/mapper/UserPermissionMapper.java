package com.smart.office.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.office.model.entity.UserPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户权限表 Mapper
 */
@Mapper
public interface UserPermissionMapper extends BaseMapper<UserPermission> {
}