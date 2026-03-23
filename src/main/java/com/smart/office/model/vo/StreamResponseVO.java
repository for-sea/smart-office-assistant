package com.smart.office.model.vo;

import lombok.Data;

/**
 * 流式响应 VO
 */
@Data
public class StreamResponseVO {

    /**
     * 内容片段
     */
    private String content;

    /**
     * 是否完成
     */
    private boolean finish;

    /**
     * 会话ID
     */
    private String sessionId;

    public static StreamResponseVO finish() {
        StreamResponseVO vo = new StreamResponseVO();
        vo.setFinish(true);
        return vo;
    }

    public static StreamResponseVO of(String content) {
        StreamResponseVO vo = new StreamResponseVO();
        vo.setContent(content);
        vo.setFinish(false);
        return vo;
    }
}