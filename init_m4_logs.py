"""初始化 M4 操作留痕 + 反馈表"""
import pymysql

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root", password="", charset="utf8mb4")
cur = conn.cursor()
cur.execute("USE aioa_ledger")

# operation_logs
cur.execute("""
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
""")
print("operation_logs 表就绪")

# feedbacks
cur.execute("""
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
""")
print("feedbacks 表就绪")

conn.commit()
conn.close()
print("Done")
