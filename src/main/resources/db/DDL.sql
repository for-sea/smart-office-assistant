CREATE TABLE `document`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `file_name`     VARCHAR(255) NOT NULL COMMENT 'Original file name',
    `file_path`     VARCHAR(500)          COMMENT 'Stored file path',
    `file_size`     BIGINT                COMMENT 'File size in bytes',
    `file_type`     VARCHAR(50)           COMMENT 'File type, e.g. pdf/docx/txt',
    `uploader`      VARCHAR(100) NOT NULL COMMENT 'Uploader user id',
    `upload_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Upload time',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '0 processing, 1 done, 2 failed, 3 disabled',
    `chunk_count`   INT          NOT NULL DEFAULT 0 COMMENT 'Chunk count',
    `process_time`  INT                   COMMENT 'Processing time in seconds',
    `error_message` TEXT                  COMMENT 'Error message',
    `department`    VARCHAR(100)          COMMENT 'Department code',
    `description`   VARCHAR(500)          COMMENT 'Document description',
    `version`       INT          NOT NULL DEFAULT 1 COMMENT 'Optimistic lock version',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0 no, 1 yes',
    PRIMARY KEY (`id`),
    INDEX `idx_uploader` (`uploader`),
    INDEX `idx_status` (`status`),
    INDEX `idx_department` (`department`),
    INDEX `idx_upload_time` (`upload_time`),
    INDEX `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Document metadata table';

CREATE TABLE `doc_chunk`
(
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `doc_id`         BIGINT      NOT NULL COMMENT 'Document id',
    `chunk_index`    INT         NOT NULL COMMENT 'Chunk index from 0',
    `milvus_id`      VARCHAR(64) NOT NULL COMMENT 'Vector id in Milvus',
    `content`        TEXT        NOT NULL COMMENT 'Chunk content',
    `content_length` INT         NOT NULL COMMENT 'Content length',
    `start_position` INT                  COMMENT 'Start position in original text',
    `end_position`   INT                  COMMENT 'End position in original text',
    `page_number`    INT                  COMMENT 'Page number for paged docs',
    `heading`        VARCHAR(500)         COMMENT 'Section heading',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_doc_chunk` (`doc_id`, `chunk_index`),
    UNIQUE INDEX `uk_milvus_id` (`milvus_id`),
    INDEX `idx_doc_id` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Document chunk mapping table';

CREATE TABLE `chat_history`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `session_id`         VARCHAR(64)  NOT NULL COMMENT 'Session id',
    `user_id`            VARCHAR(100) NOT NULL COMMENT 'User id',
    `question`           TEXT         NOT NULL COMMENT 'User question',
    `question_embedding` JSON                  COMMENT 'Optional question embedding',
    `answer`             TEXT         NOT NULL COMMENT 'Model answer',
    `sources`            JSON                  COMMENT 'Answer sources JSON',
    `prompt_tokens`      INT                   COMMENT 'Prompt token count',
    `completion_tokens`  INT                   COMMENT 'Completion token count',
    `total_tokens`       INT                   COMMENT 'Total token count',
    `latency_ms`         INT                   COMMENT 'Latency in ms',
    `feedback`           TINYINT               COMMENT 'Feedback: 1 up, -1 down, 0 none',
    `feedback_comment`   VARCHAR(500)          COMMENT 'Feedback comment',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Chat history table';

CREATE TABLE `user_permission`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `user_id`       VARCHAR(100) NOT NULL COMMENT 'User id',
    `username`      VARCHAR(100) NOT NULL COMMENT 'Login username',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt password hash',
    `department`    VARCHAR(100)          COMMENT 'Department code',
    `group_code`    VARCHAR(100)          COMMENT 'User group code',
    `role`          VARCHAR(50)           COMMENT 'Role ADMIN or USER',
    `enabled`       TINYINT      NOT NULL DEFAULT 1 COMMENT 'Account enabled: 1 yes, 0 no',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_user_id` (`user_id`),
    UNIQUE INDEX `uk_username` (`username`),
    INDEX `idx_department` (`department`),
    INDEX `idx_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User permission table';

CREATE TABLE `document_dept`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `doc_id`      BIGINT       NOT NULL COMMENT 'Document id',
    `department`  VARCHAR(100) NOT NULL COMMENT 'Department code',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_doc_dept` (`doc_id`, `department`),
    INDEX `idx_department` (`department`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Document-department relation table';

CREATE TABLE `document_group`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `doc_id`      BIGINT       NOT NULL COMMENT 'Document id',
    `group_code`  VARCHAR(100) NOT NULL COMMENT 'User group code',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_doc_group` (`doc_id`, `group_code`),
    INDEX `idx_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Document-group relation table';

-- Incremental migration for existing database
ALTER TABLE `user_permission`
    ADD COLUMN `username` VARCHAR(100) NULL COMMENT 'Login username' AFTER `user_id`,
    ADD COLUMN `password_hash` VARCHAR(255) NULL COMMENT 'BCrypt password hash' AFTER `username`,
    ADD COLUMN `group_code` VARCHAR(100) NULL COMMENT 'User group code' AFTER `department`,
    ADD COLUMN `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT 'Account enabled: 1 yes, 0 no' AFTER `role`;

ALTER TABLE `user_permission`
    ADD UNIQUE INDEX `uk_username` (`username`),
    ADD INDEX `idx_department` (`department`),
    ADD INDEX `idx_group_code` (`group_code`);

CREATE TABLE IF NOT EXISTS `document_group`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    `doc_id`      BIGINT       NOT NULL COMMENT 'Document id',
    `group_code`  VARCHAR(100) NOT NULL COMMENT 'User group code',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_doc_group` (`doc_id`, `group_code`),
    INDEX `idx_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Document-group relation table';
