package com.smart.office.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.office.model.entity.Document;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档元数据表 Mapper
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}