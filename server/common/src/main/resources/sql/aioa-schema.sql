-- ============================================================
-- AIOA · 完整建库建表脚本（一次性执行）
-- 由 io.aioa.common.init.SqlInit 在 JDBC 不指定库的情况下执行
-- ============================================================

CREATE DATABASE IF NOT EXISTS aioa_tenant   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS aioa_session  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS aioa_agent    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS aioa_ledger   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- --------------------- aioa_tenant ---------------------
USE aioa_tenant;

CREATE TABLE IF NOT EXISTS tenants (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  name            VARCHAR(64)  NOT NULL,
  code            VARCHAR(32)  NOT NULL,
  status          TINYINT      NOT NULL DEFAULT 1,
  expired_at      DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at      DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  KEY idx_id_str (id_str)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS users (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  username        VARCHAR(64)  NOT NULL,
  password_hash   VARCHAR(128) NOT NULL,
  display_name    VARCHAR(64)  NULL,
  avatar          VARCHAR(255) NULL,
  email           VARCHAR(128) NULL,
  phone           VARCHAR(32)  NULL,
  status          TINYINT      NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at      DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_username (tenant_id, username),
  KEY idx_id_str (id_str)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS roles (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  code            VARCHAR(32)  NOT NULL,
  name            VARCHAR(64)  NOT NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at      DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_roles (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  role_id      BIGINT UNSIGNED NOT NULL,
  tenant_id    BIGINT UNSIGNED NOT NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO tenants (id_str, name, code, status) VALUES ('tnt-default', '默认租户', 'default', 1);
INSERT IGNORE INTO users (id_str, tenant_id, username, password_hash, display_name, status) VALUES
  ('usr-admin', 1, 'admin', '$2a$10$UFdiURbHCmcfRuvseP4SL.UGuaVvfDT5My.w8p9.QDhhymv3..kju', '管理员', 1);
INSERT IGNORE INTO roles (id_str, tenant_id, code, name) VALUES
  ('rol-admin',   1, 'admin',   '管理员'),
  ('rol-user',     1, 'user',    '普通用户'),
  ('rol-auditor',  1, 'auditor', '审批人');
INSERT IGNORE INTO user_roles (user_id, role_id, tenant_id) VALUES (1, 1, 1);

-- --------------------- aioa_session ---------------------
USE aioa_session;

CREATE TABLE IF NOT EXISTS sessions (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  agent_id        BIGINT UNSIGNED NULL,
  title           VARCHAR(128) NULL,
  status          TINYINT      NOT NULL DEFAULT 1,
  last_message_at DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at      DATETIME(3)  NULL,
  PRIMARY KEY (id),
  KEY idx_user (tenant_id, user_id),
  KEY idx_id_str (id_str)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS messages (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  session_id      BIGINT UNSIGNED NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  role            TINYINT      NOT NULL,
  content         MEDIUMTEXT   NULL,
  content_type    TINYINT      NOT NULL DEFAULT 1,
  tool_call_id    VARCHAR(64)  NULL,
  parent_id       BIGINT UNSIGNED NULL,
  token_input     INT UNSIGNED NOT NULL DEFAULT 0,
  token_output    INT UNSIGNED NOT NULL DEFAULT 0,
  status          TINYINT      NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_session (session_id, created_at),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS citations (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  message_id      BIGINT UNSIGNED NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  source_type     TINYINT      NOT NULL,
  source_id       VARCHAR(64)  NULL,
  title           VARCHAR(255) NULL,
  url             VARCHAR(512) NULL,
  snippet         TEXT         NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_message (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- --------------------- aioa_agent ---------------------
USE aioa_agent;

CREATE TABLE IF NOT EXISTS agents (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  slug            VARCHAR(64)  NOT NULL,
  name            VARCHAR(64)  NOT NULL,
  avatar          VARCHAR(255) NULL,
  description     VARCHAR(512) NULL,
  model           VARCHAR(64)  NOT NULL DEFAULT 'deepseek-chat',
  system_prompt   TEXT         NULL,
  status          TINYINT      NOT NULL DEFAULT 1,
  sort            INT          NOT NULL DEFAULT 0,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at      DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_slug (tenant_id, slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS tools (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  slug            VARCHAR(64)  NOT NULL,
  name            VARCHAR(64)  NOT NULL,
  description     VARCHAR(512) NULL,
  endpoint        VARCHAR(512) NOT NULL,
  method          VARCHAR(8)   NOT NULL DEFAULT 'POST',
  auth_type       TINYINT      NOT NULL DEFAULT 1,
  param_schema    JSON         NULL,
  result_schema   JSON         NULL,
  is_sensitive    TINYINT      NOT NULL DEFAULT 0,
  status          TINYINT      NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at      DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_slug (tenant_id, slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS skill_forms (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tool_id         BIGINT UNSIGNED NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  form_schema     JSON         NOT NULL,
  version         INT          NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tool (tool_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS approval_tickets (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  session_id      BIGINT UNSIGNED NOT NULL,
  message_id      BIGINT UNSIGNED NOT NULL,
  tool_id         BIGINT UNSIGNED NOT NULL,
  tool_call_id    VARCHAR(64)  NOT NULL,
  input_payload   JSON         NULL,
  status          TINYINT      NOT NULL DEFAULT 1,
  approver_id     BIGINT UNSIGNED NULL,
  opinion        VARCHAR(512) NULL,
  approved_at     DATETIME(3)  NULL,
  expired_at      DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_session (session_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sensitive_words (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL DEFAULT 0,
  word            VARCHAR(128) NOT NULL,
  category        VARCHAR(32)  NOT NULL DEFAULT 'default',
  action          TINYINT      NOT NULL DEFAULT 1,
  status          TINYINT      NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant_category (tenant_id, category),
  KEY idx_word (word(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO sensitive_words (id_str, tenant_id, word, category, action, status) VALUES
  ('sw001', 0, '密码',     'security',  2, 1),
  ('sw002', 0, '账号密码', 'security',  2, 1),
  ('sw003', 0, '身份证号', 'security',  2, 1),
  ('sw004', 0, '银行卡号', 'security',  2, 1),
  ('sw005', 0, '删除所有', 'danger',    2, 1),
  ('sw006', 0, '清空数据', 'danger',    2, 1),
  ('sw007', 0, '对外发布', 'publish',   1, 1),
  ('sw008', 0, '报送上级', 'publish',   1, 1),
  ('sw009', 0, '发文',     'publish',   1, 1),
  ('sw010', 0, '通知',     'publish',   1, 1);

INSERT IGNORE INTO agents (id_str, tenant_id, slug, name, description, model, system_prompt, status, sort) VALUES
  ('agt-general',  1, 'general',  '通用助手',  '日常问答与多技能协作',  'deepseek-chat', '你是 AIOA 的通用办公助手，简洁准确地回答用户问题。', 1, 10),
  ('agt-travel',   1, 'travel',   '差旅专家',  '差旅查询与申请',        'deepseek-chat', '你是差旅专家，协助用户查询票务、住宿并起草申请。', 1, 20),
  ('agt-approval', 1, 'approval', '审批专家',  '内部审批流转',          'deepseek-chat', '你是审批专家，根据规则发起审批并回注结果。',     1, 30);

-- --------------------- aioa_ledger ---------------------
USE aioa_ledger;

CREATE TABLE IF NOT EXISTS quotas (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  total_quota     BIGINT       NOT NULL DEFAULT 1000000,
  used_quota      BIGINT       NOT NULL DEFAULT 0,
  member_level    TINYINT      NOT NULL DEFAULT 1,
  preferred_model VARCHAR(64)  NOT NULL DEFAULT 'deepseek-chat',
  expire_at       DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS token_usages (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  session_id      BIGINT UNSIGNED NOT NULL,
  message_id      BIGINT UNSIGNED NULL,
  model           VARCHAR(64)  NOT NULL,
  input_tokens    INT UNSIGNED NOT NULL DEFAULT 0,
  output_tokens   INT UNSIGNED NOT NULL DEFAULT 0,
  total_tokens    INT UNSIGNED NOT NULL DEFAULT 0,
  cost_amount     DECIMAL(12,6) NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_session (session_id),
  KEY idx_user_time (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ledger_entries (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  quota_id        BIGINT UNSIGNED NOT NULL,
  usage_id        BIGINT UNSIGNED NULL,
  delta           BIGINT       NOT NULL,
  balance_after   BIGINT       NOT NULL,
  reason          VARCHAR(32)  NOT NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_quota (quota_id, created_at),
  KEY idx_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO quotas (id_str, tenant_id, user_id, total_quota, used_quota, member_level, preferred_model) VALUES
  ('qta-admin', 1, 1, 1000000, 0, 1, 'deepseek-chat');

CREATE TABLE IF NOT EXISTS recharge_orders (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  type            TINYINT      NOT NULL DEFAULT 1,
  product_code    VARCHAR(64)  NOT NULL,
  product_name    VARCHAR(128) NOT NULL,
  token_amount    BIGINT       NOT NULL DEFAULT 0,
  amount          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  status          TINYINT      NOT NULL DEFAULT 1,
  pay_method      VARCHAR(32)  NULL,
  trade_no        VARCHAR(128) NULL,
  paid_at         DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user (tenant_id, user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FR-H1/H2: 操作留痕表
CREATE TABLE IF NOT EXISTS operation_logs (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  action          VARCHAR(32)  NOT NULL,
  target          VARCHAR(128) NOT NULL DEFAULT '',
  result          TINYINT      NOT NULL DEFAULT 1,
  detail          VARCHAR(512) NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user (tenant_id, user_id),
  KEY idx_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FR-H4: 反馈表
CREATE TABLE IF NOT EXISTS feedbacks (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  message_id      BIGINT       NULL,
  type            TINYINT      NOT NULL DEFAULT 1,
  content         TEXT         NOT NULL,
  status          TINYINT      NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
