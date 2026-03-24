package com.smart.office.base.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.office.domain.chat.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 对话历史 Mapper 接口
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

    /**
     * 查询用户最近对话
     */
    @Select("SELECT * FROM chat_history WHERE user_id = #{userId} " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<ChatHistory> selectRecentByUser(@Param("userId") String userId,
                                         @Param("limit") int limit);

    /**
     * 查询会话历史
     */
    @Select("SELECT * FROM chat_history WHERE session_id = #{sessionId} " +
            "ORDER BY create_time")
    List<ChatHistory> selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 统计用户反馈
     */
    @Select("SELECT feedback, COUNT(*) as count FROM chat_history " +
            "WHERE user_id = #{userId} GROUP BY feedback")
    List<Object> countFeedbackByUser(@Param("userId") String userId);
}