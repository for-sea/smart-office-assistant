package com.smart.office.domain.document.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 文档与用户组关联实体，对应 document_group 表。
 */
@Data
@TableName("document_group")
public class DocumentGroup {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文档 ID。
     */
    @TableField("doc_id")
    private Long docId;

    /**
     * 用户组编码。
     */
    @TableField("group_code")
    private String groupCode;

    /**
     * 记录创建时间。
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
