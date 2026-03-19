package com.smart.office.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.office.model.entity.DocChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档块映射表 Mapper
 */
@Mapper
public interface DocChunkMapper extends BaseMapper<DocChunk> {
}