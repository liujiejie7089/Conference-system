"""初始化 M3 敏感词表"""
import subprocess, sys

try:
    import pymysql
except ImportError:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pymysql", "-q"])
    import pymysql

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root", password="", charset="utf8mb4")
cur = conn.cursor()

# 创建表
cur.execute("USE aioa_agent")
cur.execute("""
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
""")

# 插入默认敏感词
words = [
    ('sw001', 0, '密码',     'security',  2, 1),
    ('sw002', 0, '账号密码', 'security',  2, 1),
    ('sw003', 0, '身份证号', 'security',  2, 1),
    ('sw004', 0, '银行卡号', 'security',  2, 1),
    ('sw005', 0, '删除所有', 'danger',    2, 1),
    ('sw006', 0, '清空数据', 'danger',    2, 1),
    ('sw007', 0, '对外发布', 'publish',   1, 1),
    ('sw008', 0, '报送上级', 'publish',   1, 1),
    ('sw009', 0, '发文',     'publish',   1, 1),
    ('sw010', 0, '通知',     'publish',   1, 1),
]

for w in words:
    cur.execute("INSERT IGNORE INTO sensitive_words (id_str, tenant_id, word, category, action, status) VALUES (%s,%s,%s,%s,%s,%s)", w)

conn.commit()
cur.execute("SELECT COUNT(*) FROM sensitive_words")
count = cur.fetchone()[0]
print(f"sensitive_words 表创建完成，共 {count} 条记录")
conn.close()
