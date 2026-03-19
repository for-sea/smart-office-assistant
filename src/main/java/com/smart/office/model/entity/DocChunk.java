package com.smart.office.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档块映射表
 */
@Data
@TableName("doc_chunk")
public class DocChunk {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 所属文档ID
     */
    @TableField("doc_id")
    private Long docId;
    
    /**
     * 块序号（从0开始）
     */
    @TableField("chunk_index")
    private Integer chunkIndex;
    
    /**
     * Milvus中的向量ID
     */
    @TableField("milvus_id")
    private String milvusId;
    
    /**
     * 文本块内容
     */
    private String content;
    
    /**
     * 内容长度（字符数）
     */
    @TableField("content_length")
    private Integer contentLength;
    
    /**
     * 在原始文档中的起始位置（可选）
     */
    @TableField("start_position")
    private Integer startPosition;
    
    /**
     * 在原始文档中的结束位置（可选）
     */
    @TableField("end_position")
    private Integer endPosition;
    
    /**
     * 所在页码（适用于PDF等）
     */
    @TableField("page_number")
    private Integer pageNumber;
    
    /**
     * 所在章节标题（可选）
     */
    private String heading;
    
    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}