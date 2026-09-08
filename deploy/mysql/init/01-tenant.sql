-- ============================================================
-- AIOA · tenant schema（用户、租户、角色）
-- 引擎 InnoDB，字符集 utf8mb4_0900_ai_ci，多租户预留 tenant_id
-- ============================================================

CREATE DATABASE IF NOT EXISTS aioa_tenant
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE aioa_tenant;

-- -----------------------------------------------------------
-- 租户
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenants (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL COMMENT '对外雪花ID',
  name            VARCHAR(64)  NOT NULL COMMENT '租户名称',
  code            VARCHAR(32)  NOT NULL COMMENT '租户编码（唯一）',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
  expired_at      DATETIME(3)  NULL COMMENT '到期时间',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at      DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  KEY idx_id_str (id_str)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户';

-- -----------------------------------------------------------
-- 用户
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL COMMENT '所属租户',
  username        VARCHAR(64)  NOT NULL COMMENT '登录名',
  password_hash   VARCHAR(128) NOT NULL COMMENT 'BCrypt 哈希',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户';

-- -----------------------------------------------------------
-- 角色
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_str          VARCHAR(32)  NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  code           VARCHAR(32)  NOT NULL COMMENT '角色编码：admin/user/auditor',
  name           VARCHAR(64)  NOT NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  deleted_at     DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

-- -----------------------------------------------------------
-- 用户-角色 关联
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_roles (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  role_id      BIGINT UNSIGNED NOT NULL,
  tenant_id    BIGINT UNSIGNED NOT NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联';

-- -----------------------------------------------------------
-- 初始数据：默认租户 + admin 用户 + 三个角色
-- admin 密码 = admin123，BCrypt 哈希：$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68rcZL0lktcye
-- -----------------------------------------------------------
INSERT INTO tenants (id_str, name, code, status)
VALUES ('tnt-default', '默认租户', 'default', 1);

INSERT INTO users (id_str, tenant_id, username, password_hash, display_name, status)
VALUES ('usr-admin', 1, 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68rcZL0lktcye', '管理员', 1);

INSERT INTO roles (id_str, tenant_id, code, name) VALUES
  ('rol-admin',   1, 'admin',   '管理员'),
  ('rol-user',     1, 'user',    '普通用户'),
  ('rol-auditor',  1, 'auditor', '审批人');

INSERT INTO user_roles (user_id, role_id, tenant_id) VALUES
  (1, 1, 1);
