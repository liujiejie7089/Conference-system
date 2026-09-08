-- ============================================================
-- AIOA · session schema（会话、消息、引用溯源）
-- ============================================================

CREATE DATABASE IF NOT EXISTS aioa_session
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE aioa_session;

-- -----------------------------------------------------------
-- 会话
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS sessions (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL COMMENT '发起用户',
  agent_id        BIGINT UNSIGNED NULL COMMENT '关联专家（agent）',
  title           VARCHAR(128) NULL COMMENT '会话标题',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=活跃 2=归档 3=已删',
  last_message_at DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at      DATETIME(3)  NULL,
  PRIMARY KEY (id),
  KEY idx_user (tenant_id, user_id),
  KEY idx_id_str (id_str)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话';

-- -----------------------------------------------------------
-- 消息（含 user/assistant/tool/approval 多类型）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS messages (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  session_id      BIGINT UNSIGNED NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  role            TINYINT      NOT NULL COMMENT '1=user 2=assistant 3=tool 4=system 5=approval',
  content         MEDIUMTEXT   NULL COMMENT '消息文本',
  content_type    TINYINT      NOT NULL DEFAULT 1 COMMENT '1=text 2=tool_call 3=approval 4=citation',
  tool_call_id    VARCHAR(64)  NULL,
  parent_id       BIGINT UNSIGNED NULL COMMENT '上一条消息',
  token_input     INT UNSIGNED NOT NULL DEFAULT 0,
  token_output    INT UNSIGNED NOT NULL DEFAULT 0,
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=已生成 2=流式中 3=中断 4=失败',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_session (session_id, created_at),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息';

-- -----------------------------------------------------------
-- 引用溯源
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS citations (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  message_id      BIGINT UNSIGNED NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  source_type     TINYINT      NOT NULL COMMENT '1=知识库 2=工具 3=外部链接',
  source_id       VARCHAR(64)  NULL,
  title           VARCHAR(255) NULL,
  url             VARCHAR(512) NULL,
  snippet         TEXT         NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_message (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='引用溯源';
