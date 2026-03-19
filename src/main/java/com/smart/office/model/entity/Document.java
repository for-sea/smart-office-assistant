package com.smart.office.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档元数据表
 */
@Data
@TableName("document")
public class Document {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 原始文件名
     */
    @TableField("file_name")
    private String fileName;
    
    /**
     * 存储路径（本地或OSS路径）
     */
    @TableField("file_path")
    private String filePath;
    
    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;
    
    /**
     * 文件类型（pdf/docx/txt等）
     */
    @TableField("file_type")
    private String fileType;
    
    /**
     * 上传者/创建人
     */
    private String uploader;
    
    /**
     * 上传时间
     */
    @TableField("upload_time")
    private LocalDateTime uploadTime;
    
    /**
     * 最后更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
    
    /**
     * 状态：0-处理中，1-已完成，2-处理失败，3-已禁用
     */
    private Integer status;
    
    /**
     * 分块数量
     */
    @TableField("chunk_count")
    private Integer chunkCount;
    
    /**
     * 处理耗时（秒）
     */
    @TableField("process_time")
    private Integer processTime;
    
    /**
     * 处理失败时的错误信息
     */
    @TableField("error_message")
    private String errorMessage;
    
    /**
     * 所属部门（用于权限控制）
     */
    private String department;
    
    /**
     * 文档描述
     */
    private String description;
    
    /**
     * 版本号（乐观锁）
     */
    private Integer version;
}