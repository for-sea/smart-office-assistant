package com.smart.office.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.office.model.entity.DocumentDept;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档-部门关联表 Mapper
 */
@Mapper
public interface DocumentDeptMapper extends BaseMapper<DocumentDept> {
}