CREATE TABLE `document`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `file_name`     VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path`     VARCHAR(500) COMMENT '存储路径（本地或OSS路径）',
    `file_size`     BIGINT COMMENT '文件大小（字节）',
    `file_type`     VARCHAR(50) COMMENT '文件类型（pdf/docx/txt等）',
    `uploader`      VARCHAR(100) NOT NULL COMMENT '上传者/创建人',
    `upload_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-处理中，1-已完成，2-处理失败，3-已禁用',
    `chunk_count`   INT          NOT NULL DEFAULT 0 COMMENT '分块数量',
    `process_time`  INT COMMENT '处理耗时（秒）',
    `error_message` TEXT COMMENT '处理失败时的错误信息',
    `department`    VARCHAR(100) COMMENT '所属部门（用于权限控制）',
    `description`   VARCHAR(500) COMMENT '文档描述',
    `version`       INT          NOT NULL DEFAULT 1 COMMENT '版本号（乐观锁）',
    PRIMARY KEY (`id`),
    INDEX           `idx_uploader` (`uploader`),
    INDEX           `idx_status` (`status`),
    INDEX           `idx_department` (`department`),
    INDEX           `idx_upload_time` (`upload_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档元数据表';

CREATE TABLE `doc_chunk`
(
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `doc_id`         BIGINT      NOT NULL COMMENT '所属文档ID',
    `chunk_index`    INT         NOT NULL COMMENT '块序号（从0开始）',
    `milvus_id`      VARCHAR(64) NOT NULL COMMENT 'Milvus中的向量ID',
    `content`        TEXT        NOT NULL COMMENT '文本块内容',
    `content_length` INT         NOT NULL COMMENT '内容长度（字符数）',
    `start_position` INT COMMENT '在原始文档中的起始位置（可选）',
    `end_position`   INT COMMENT '在原始文档中的结束位置（可选）',
    `page_number`    INT COMMENT '所在页码（适用于PDF等）',
    `heading`        VARCHAR(500) COMMENT '所在章节标题（可选）',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_doc_chunk` (`doc_id`, `chunk_index`),
    UNIQUE INDEX `uk_milvus_id` (`milvus_id`),
    INDEX            `idx_doc_id` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档块映射表';

CREATE TABLE `chat_history`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`         VARCHAR(64)  NOT NULL COMMENT '会话ID',
    `user_id`            VARCHAR(100) NOT NULL COMMENT '用户标识',
    `question`           TEXT         NOT NULL COMMENT '用户问题',
    `question_embedding` JSON COMMENT '问题向量（可选，用于分析）',
    `answer`             TEXT         NOT NULL COMMENT '模型回答',
    `sources`            JSON COMMENT '答案来源（引用的文档块信息，JSON格式）',
    `prompt_tokens`      INT COMMENT 'Prompt令牌数',
    `completion_tokens`  INT COMMENT '回答令牌数',
    `total_tokens`       INT COMMENT '总令牌数',
    `latency_ms`         INT COMMENT '响应耗时（毫秒）',
    `feedback`           TINYINT COMMENT '用户反馈：1-点赞，-1-点踩，0-无反馈',
    `feedback_comment`   VARCHAR(500) COMMENT '反馈意见',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
    PRIMARY KEY (`id`),
    INDEX                `idx_session_id` (`session_id`),
    INDEX                `idx_user_id` (`user_id`),
    INDEX                `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话历史表';

CREATE TABLE `user_permission`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     VARCHAR(100) NOT NULL COMMENT '用户ID',
    `department`  VARCHAR(100) COMMENT '所属部门',
    `role`        VARCHAR(50) COMMENT '角色（admin/user）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户权限表';

CREATE TABLE `document_dept`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `doc_id`      BIGINT       NOT NULL COMMENT '文档ID',
    `department`  VARCHAR(100) NOT NULL COMMENT '可访问的部门',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_doc_dept` (`doc_id`, `department`),
    INDEX         `idx_department` (`department`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档-部门关联表（支持多部门共享）';