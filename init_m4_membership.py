"""初始化 M4 会员/支付相关表结构"""
import subprocess, sys

try:
    import pymysql
except ImportError:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pymysql", "-q"])
    import pymysql

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root", password="", charset="utf8mb4")
cur = conn.cursor()
cur.execute("USE aioa_ledger")

# 1. quotas 表新增列（已存在则跳过）
try:
    cur.execute("ALTER TABLE quotas ADD COLUMN member_level TINYINT NOT NULL DEFAULT 1 AFTER used_quota")
    print("已添加 member_level 列")
except pymysql.err.OperationalError:
    print("member_level 列已存在，跳过")

try:
    cur.execute("ALTER TABLE quotas ADD COLUMN preferred_model VARCHAR(64) NOT NULL DEFAULT 'deepseek-chat' AFTER member_level")
    print("已添加 preferred_model 列")
except pymysql.err.OperationalError:
    print("preferred_model 列已存在，跳过")

# 修复已有记录
cur.execute("UPDATE quotas SET member_level=1 WHERE member_level IS NULL")
cur.execute("UPDATE quotas SET preferred_model='deepseek-chat' WHERE preferred_model IS NULL")

# 2. 创建 recharge_orders 表
cur.execute("""
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
""")

conn.commit()

# 验证
cur.execute("DESCRIBE quotas")
print("\nquotas 表结构:")
for row in cur.fetchall():
    print(f"  {row[0]:20s} {row[1]}")

cur.execute("SELECT COUNT(*) FROM recharge_orders")
print(f"\nrecharge_orders 表创建完成，当前 {cur.fetchone()[0]} 条记录")

conn.close()
