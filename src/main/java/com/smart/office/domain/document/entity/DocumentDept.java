package com.smart.office.domain.document.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文档-部门关联表（支持多部门共享）
 */
@Data
@TableName("document_dept")
public class DocumentDept {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 文档ID
     */
    @TableField("doc_id")
    private Long docId;
    
    /**
     * 可访问的部门
     */
    private String department;
    
    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}