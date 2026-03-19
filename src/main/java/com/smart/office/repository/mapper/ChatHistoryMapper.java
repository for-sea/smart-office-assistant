package com.smart.office.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.office.model.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话历史表 Mapper
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {
}