-- ============================================================
-- AIOA · ledger schema（额度、词元使用记录、账本条目）
-- ============================================================

CREATE DATABASE IF NOT EXISTS aioa_ledger
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE aioa_ledger;

-- -----------------------------------------------------------
-- 额度（按 租户×用户 维度）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS quotas (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  total_quota     BIGINT       NOT NULL DEFAULT 1000000 COMMENT '总额度（Token）',
  used_quota      BIGINT       NOT NULL DEFAULT 0,
  expire_at       DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='额度';

-- -----------------------------------------------------------
-- 词元使用记录（每条消息粒度）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS token_usages (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  session_id      BIGINT UNSIGNED NOT NULL,
  message_id     BIGINT UNSIGNED NULL,
  model           VARCHAR(64)  NOT NULL,
  input_tokens    INT UNSIGNED NOT NULL DEFAULT 0,
  output_tokens   INT UNSIGNED NOT NULL DEFAULT 0,
  total_tokens    INT UNSIGNED NOT NULL DEFAULT 0,
  cost_amount     DECIMAL(12,6) NULL COMMENT '预估成本（元）',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_session (session_id),
  KEY idx_user_time (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='词元使用记录';

-- -----------------------------------------------------------
-- 账本条目（额度变更流水）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS ledger_entries (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  user_id         BIGINT UNSIGNED NOT NULL,
  quota_id        BIGINT UNSIGNED NOT NULL,
  usage_id        BIGINT UNSIGNED NULL COMMENT '关联 token_usages',
  delta           BIGINT       NOT NULL COMMENT '变更值（扣减为负）',
  balance_after   BIGINT       NOT NULL COMMENT '变更后余额',
  reason          VARCHAR(32)  NOT NULL COMMENT 'consume/recharge/adjust',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_quota (quota_id, created_at),
  KEY idx_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账本条目';

-- -----------------------------------------------------------
-- 初始数据：默认 admin 用户的额度
-- -----------------------------------------------------------
INSERT INTO quotas (id_str, tenant_id, user_id, total_quota, used_quota) VALUES
  ('qta-admin', 1, 1, 1000000, 0);
