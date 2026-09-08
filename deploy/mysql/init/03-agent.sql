-- ============================================================
-- AIOA · agent schema（专家、技能/工具、审批卡点、技能表单）
-- ============================================================

CREATE DATABASE IF NOT EXISTS aioa_agent
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE aioa_agent;

-- -----------------------------------------------------------
-- 专家（Agent）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS agents (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  slug            VARCHAR(64)  NOT NULL COMMENT '编码',
  name            VARCHAR(64)  NOT NULL COMMENT '展示名',
  avatar          VARCHAR(255) NULL,
  description     VARCHAR(512) NULL,
  model           VARCHAR(64)  NOT NULL DEFAULT 'deepseek-chat',
  system_prompt   TEXT         NULL,
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=上架 0=下架',
  sort            INT          NOT NULL DEFAULT 0,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at      DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_slug (tenant_id, slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专家';

-- -----------------------------------------------------------
-- 技能 / 工具（业务系统接入产物，由连接器引擎解析后注册）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS tools (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  slug            VARCHAR(64)  NOT NULL,
  name            VARCHAR(64)  NOT NULL,
  description     VARCHAR(512) NULL,
  endpoint        VARCHAR(512) NOT NULL COMMENT '实际请求地址',
  method          VARCHAR(8)   NOT NULL DEFAULT 'POST',
  auth_type       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=bearer 2=apikey 3=none',
  param_schema    JSON         NULL COMMENT 'OpenAPI 参数 Schema',
  result_schema   JSON         NULL,
  is_sensitive    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否触发审批 1=是',
  status          TINYINT      NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at      DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_slug (tenant_id, slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技能/工具';

-- -----------------------------------------------------------
-- 技能表单 Schema（前端 SkillForm 动态渲染）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS skill_forms (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tool_id         BIGINT UNSIGNED NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  form_schema     JSON         NOT NULL COMMENT '动态表单 Schema',
  version         INT          NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tool (tool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技能表单 Schema';

-- -----------------------------------------------------------
-- 审批卡点（HITL）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_tickets (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  session_id      BIGINT UNSIGNED NOT NULL,
  message_id     BIGINT UNSIGNED NOT NULL COMMENT '触发消息',
  tool_id        BIGINT UNSIGNED NOT NULL,
  tool_call_id   VARCHAR(64)  NOT NULL,
  input_payload  JSON         NULL COMMENT '拟执行的请求参数',
  status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1=待审批 2=通过 3=驳回 4=超时',
  approver_id    BIGINT UNSIGNED NULL,
  opinion        VARCHAR(512) NULL COMMENT '审批意见',
  approved_at    DATETIME(3)  NULL,
  expired_at     DATETIME(3)  NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_session (session_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审批卡点';

-- -----------------------------------------------------------
-- 初始专家
-- -----------------------------------------------------------
INSERT INTO agents (id_str, tenant_id, slug, name, description, model, system_prompt, status, sort) VALUES
  ('agt-general',  1, 'general',  '通用助手',  '日常问答与多技能协作',  'deepseek-chat', '你是 AIOA 的通用办公助手，简洁准确地回答用户问题。', 1, 10),
  ('agt-travel',   1, 'travel',   '差旅专家',  '差旅查询与申请',        'deepseek-chat', '你是差旅专家，协助用户查询票务、住宿并起草申请。', 1, 20),
  ('agt-approval', 1, 'approval', '审批专家',  '内部审批流转',          'deepseek-chat', '你是审批专家，根据规则发起审批并回注结果。',     1, 30);
