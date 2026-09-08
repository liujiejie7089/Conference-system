"""
init_admin_data.py — 注入 admin 端闭环演示数据
生成多用户、多会话、多工单(含敏感词触发)、多表单、多账单、多操作留痕,
使管理端登录后即可看到完整业务运转状态,并能通过「待审批→通过」验证
"用户提交 → 审批通过 → inject 回写会话 → 用户可见" 的闭环。
"""
import pymysql, time, json, uuid, sys, subprocess
from datetime import datetime, timedelta

try:
    import pymysql
except ImportError:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "pymysql", "-q"])
    import pymysql

CONN_KW = dict(host="127.0.0.1", port=3306, user="root", password="", charset="utf8mb4")
TODAY = datetime.now().replace(hour=9, minute=0, second=0, microsecond=0)

def fake_snowflake():
    """生成 18 位 id_str(时间戳+随机位)"""
    base = int(time.time() * 1000)
    return str(base) + str(int((base >> 10) % 1000)).zfill(3) + str(int(base % 100)).zfill(2)


# ============================================================
# 1) 租户 & 用户
# ============================================================
ten = pymysql.connect(**CONN_KW, database="aioa_tenant")
tcur = ten.cursor()
tcur.execute("SELECT id FROM tenants WHERE code='default' LIMIT 1")
row = tcur.fetchone()
TENANT_ID = row[0] if row else 1
print(f"租户 default → id={TENANT_ID}")

tcur.execute("SELECT id, username FROM users WHERE tenant_id=%s ORDER BY id", (TENANT_ID,))
existing = tcur.fetchall()
existing_ids = {r[1]: r[0] for r in existing}
print(f"已有用户 {len(existing)} 个: {existing_ids}")

# 创建 5 个测试用户(若已存在则跳过)
USERS = [
    ("admin",   "管理员",   "admin@aioa.cn", "secret"),
    ("lihua",   "李华",     "lihua@aioa.cn", "secret"),
    ("zhangwei","张伟",     "zhangwei@aioa.cn", "secret"),
    ("wangfang","王芳",     "wangfang@aioa.cn", "secret"),
    ("liming",  "李明",     "liming@aioa.cn", "secret"),
]
for uname, dname, email, pwd in USERS:
    if uname in existing_ids: continue
    tcur.execute(
        "INSERT INTO users (id_str, tenant_id, username, display_name, email, password_hash, status) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s)",
        (fake_snowflake(), TENANT_ID, uname, dname, email, pwd, 1),
    )
ten.commit()

tcur.execute("SELECT id, username, display_name FROM users WHERE tenant_id=%s ORDER BY id", (TENANT_ID,))
user_rows = tcur.fetchall()
USERS_BY_NAME = {r[1]: (r[0], r[2]) for r in user_rows}
print("用户清单:", USERS_BY_NAME)
ADMIN_UID = USERS_BY_NAME["admin"][0]
ten.close()


# ============================================================
# 2) 会话 (sessions + messages)
# ============================================================
ses = pymysql.connect(**CONN_KW, database="aioa_session")
scur = ses.cursor()
scur.execute("DELETE FROM messages WHERE session_id IN (SELECT id FROM sessions WHERE title LIKE '[演示]%')")
scur.execute("DELETE FROM sessions WHERE title LIKE '[演示]%'")
ses.commit()

DEMO_SESSIONS = [
    ("lihua",    "[演示] 请假审批闭环",  1, ["帮我请年假 1 天,明天开始","触发审批,请稍候","审批结果将自动注入此会话"]),
    ("zhangwei", "[演示] 办文助手-通知", 2, ["写一份《关于节后开展培训的通知》","正在生成…","审批后通知将通过 inject 注入"]),
    ("wangfang", "[演示] 知识库上传",    3, ["把季度报告上传到知识库","已生成上传申请,等待审批","审批结果可在本页直接看到"]),
    ("liming",   "[演示] 敏感词拦截",    4, ["帮我查一下那个机密的目录","⚠️ 触发敏感词:机密","请修改输入或等待审批"]),
    ("admin",    "[演示] 通用问答",      1, ["平台如何接入外部系统?","正在调用 RAG + 工具…","回答已生成,留痕至账单"]),
]
SESSION_IDS = {}
for uname, title, agent_id, msgs in DEMO_SESSIONS:
    uid = USERS_BY_NAME[uname][0]
    sid_str = fake_snowflake()
    scur.execute(
        "INSERT INTO sessions (id_str, tenant_id, user_id, agent_id, title, status, last_message_at) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s)",
        (sid_str, TENANT_ID, uid, agent_id, title, 1, datetime.now()),
    )
    session_pk = scur.lastrowid
    SESSION_IDS[uname] = session_pk
    for i, content in enumerate(msgs):
        scur.execute(
            "INSERT INTO messages (id_str, session_id, tenant_id, role, content, created_at) "
            "VALUES (%s,%s,%s,%s,%s,%s)",
            (fake_snowflake(), session_pk, TENANT_ID,
             1 if i % 2 == 0 else 2,
             content, datetime.now() - timedelta(minutes=10 - i * 2)),
        )
ses.commit()
print(f"创建演示会话 {len(SESSION_IDS)} 个: {SESSION_IDS}")


# ============================================================
# 3) 审批工单 (approval_tickets · status=1)
#    包含 1 条敏感词触发,用于演示高亮
# ============================================================
agt = pymysql.connect(**CONN_KW, database="aioa_agent")
acur = agt.cursor()
acur.execute("DELETE FROM approval_tickets WHERE tenant_id=%s AND input_payload LIKE '%%[演示]%%'", (TENANT_ID,))
agt.commit()

TICKETS = [
    ("lihua",    "leave",  '{"type":"leave","days":1,"reason":"[演示] 家中有事"}',                                            "普通请假申请"),
    ("zhangwei", "doc",    '{"type":"doc","title":"[演示] 关于节后开展培训的通知","word_count":780}',                          "公文拟稿审批"),
    ("wangfang", "kb",     '{"type":"kb_upload","filename":"[演示] 季度报告.pdf","scope":"部门知识库"}',                       "知识库入库"),
    ("liming",   "secret", '{"type":"query","text":"[演示] 查询机密文件列表","sensitive_hit":["机密","保密"]}',                "[敏感词拦截] 查询请求"),
    ("admin",    "config", '{"type":"config","change":"[演示] 调整知识库权限","scope":"全租户"}',                             "系统配置变更"),
]
TICKET_IDS = []
for uname, tool, payload, label in TICKETS:
    sid = SESSION_IDS[uname]
    acur.execute(
        "INSERT INTO approval_tickets "
        "(id_str, tenant_id, session_id, message_id, tool_id, tool_call_id, input_payload, status, created_at) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)",
        (fake_snowflake(), TENANT_ID, sid, 1, 1, "tc_" + uuid.uuid4().hex[:8], payload, 1, datetime.now()),
    )
    TICKET_IDS.append((acur.lastrowid, label, uname))
agt.commit()
print(f"创建待审批工单 {len(TICKET_IDS)} 条")
for tid, label, who in TICKET_IDS:
    print(f"   #{tid} {label}  发起人:{who}")


# ============================================================
# 4) 技能表单 (skill_forms) - 用于「我的申请」tab
# ============================================================
acur.execute("DELETE FROM skill_forms WHERE tenant_id=%s AND title LIKE '[演示]%%'", (TENANT_ID,))
agt.commit()

FORMS = [
    # 待我处理
    ("[演示] 王芳的办文申请",     "办文助手",      "📄", "#eaf1ff",  1, None,    2),
    ("[演示] 李明的请假申请",     "请假助手",      "🔔", "#fff4e6",  1, None,    1),
    ("[演示] 张伟的知识库入库",   "知识库入库",    "📚", "#e8f7ee",  1, None,    3),
    # 我的申请 (applicant=admin)
    ("[演示] 我的请假 0.5 天",    "请假助手",      "📝", "#fff4e6",  2, "admin", 1),
    ("[演示] 我的办文-会议纪要",   "办文助手",      "📝", "#eaf1ff",  2, "admin", 2),
]
for title, src, icon, icon_bg, status, applicant, tool_id in FORMS:
    uid, uname = (USERS_BY_NAME[applicant] if applicant else (None, ""))
    acur.execute(
        "INSERT INTO skill_forms "
        "(id_str, tool_id, tenant_id, session_id, applicant_user_id, applicant_name, status, version, "
        " form_schema, title, summary, source_label, icon, icon_bg, submitted_at) "
        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
        (fake_snowflake(), tool_id, TENANT_ID, None, uid, uname, status, 1, "{}", title,
         "由 init_admin_data.py 生成,供管理端演示", src, icon, icon_bg, datetime.now()),
    )
agt.commit()
print(f"创建技能表单 {len(FORMS)} 条")


# ============================================================
# 5) 词元账单 (token_usages) - 用于仪表盘/账单页
# ============================================================
led = pymysql.connect(**CONN_KW, database="aioa_ledger")
lcur = led.cursor()
lcur.execute("DELETE FROM token_usages WHERE tenant_id=%s AND session_id IN %s",
             (TENANT_ID, tuple(SESSION_IDS.values())))
led.commit()

MODEL = "gpt-4o-mini"
COST_PER_K = 0.0003
for uname, sid in SESSION_IDS.items():
    for round_no in range(1, 4):
        in_t = 80 + round_no * 24
        out_t = 180 + round_no * 50
        total = in_t + out_t
        cost = round(total / 1000 * COST_PER_K, 6)
        lcur.execute(
            "INSERT INTO token_usages "
            "(id_str, tenant_id, user_id, session_id, message_id, model, "
            " input_tokens, output_tokens, total_tokens, cost_amount, created_at) "
            "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (fake_snowflake(), TENANT_ID, USERS_BY_NAME[uname][0], sid, round_no, MODEL,
             in_t, out_t, total, cost, datetime.now() - timedelta(minutes=20 - round_no * 3)),
        )
led.commit()
total_tokens = sum((80 + r * 24) + (180 + r * 50) for _ in range(len(SESSION_IDS)) for r in range(1, 4))
print(f"创建账单条目 {len(SESSION_IDS) * 3} 条,合计 {total_tokens} tokens")


# ============================================================
# 6) 操作留痕 (operation_logs)
# ============================================================
LOGS = [
    ("admin",    "login",            "登录管理端"),
    ("lihua",    "login",            "用户端登录"),
    ("lihua",    "create_session",   "新建会话"),
    ("lihua",    "send_message",     "发送消息"),
    ("lihua",    "approval_submit",  "提交请假申请"),
    ("zhangwei", "login",            "用户端登录"),
    ("zhangwei", "create_session",   "新建会话"),
    ("zhangwei", "approval_submit",  "提交办文申请"),
    ("wangfang", "approval_submit",  "提交知识库申请"),
    ("liming",   "sensitive_hit",    "触发敏感词拦截"),
    ("liming",   "approval_submit",  "提交查询请求"),
    ("admin",    "create_session",   "新建管理会话"),
    ("admin",    "send_message",     "管理端消息"),
]
# operation_logs 表字段不同: status 字段名可能为 result
lcur.execute("DESCRIBE operation_logs")
cols = {r[0] for r in lcur.fetchall()}
result_col = "result" if "result" in cols else "status"
lcur.execute(f"DELETE FROM operation_logs WHERE tenant_id=%s AND detail LIKE '[演示]%%'", (TENANT_ID,))
led.commit()
for uname, action, detail in LOGS:
    uid = USERS_BY_NAME[uname][0]
    lcur.execute(
        f"INSERT INTO operation_logs (id_str, tenant_id, user_id, action, target, {result_col}, detail, created_at) "
        f"VALUES (%s,%s,%s,%s,%s,%s,%s,%s)",
        (fake_snowflake(), TENANT_ID, uid, action, "console", 1, "[演示] " + detail, datetime.now()),
    )
led.commit()
print(f"创建操作留痕 {len(LOGS)} 条")


# ============================================================
# 7) 输出闭环演示步骤
# ============================================================
print("\n" + "=" * 60)
print("✅ 闭环演示数据已注入,接下来:")
print("=" * 60)
print("1) 启动管理端:")
print("   cd /d/AIOA/admin && npm install && npm run dev")
print("   访问 http://localhost:5180  (admin/admin123/default)")
print()
print("2) 「待审批」tab 看到 5 条工单(其中 ⚠️ 敏感词 一条)")
print("3) 点击「通过」或「驳回」→ 后端 inject 消息回写到对应会话")
print()
print("4) 用户端登录对应账号 (lihua/zhangwei/wangfang/liming,密码 secret)")
print("   进入对应会话即可看到「审批结果:已通过/已驳回」系统消息")
print()
print("5) 「仪表盘」/「账单」/「操作记录」页可验证数据齐全")
print("=" * 60)