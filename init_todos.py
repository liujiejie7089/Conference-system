"""
init_todos.py — 给 aioa_agent.skill_forms 写入待办测试数据
3 条「待我处理」(status=1, 无 applicant) + 2 条「我的申请」(status=2/4, applicant=admin)
"""
import pymysql, time, json, sys
from datetime import datetime, timedelta

# 用 Snowflake 风格的 id_str (18 位,模拟 Snowflake.nextIdStr())
def fake_snowflake():
    return str(int(time.time() * 1000)) + str(int(time.time() % 1000)).zfill(3) + str(int(time.time() % 100)).zfill(2)

NOW = datetime.now()
TODAY_09 = NOW.replace(hour=9, minute=12, second=0, microsecond=0)
TODAY_10 = NOW.replace(hour=10, minute=2,  second=0, microsecond=0)

# 5 条测试数据
rows = [
    # ===== 待我处理 (3) =====
    {
        "tool_id": 1, "tenant_id": 1, "session_id": None,
        "applicant_user_id": None, "applicant_name": "李明",
        "status": 1, "version": 1,
        "form_schema": json.dumps({"type":"leave","days":1,"leave_type":"年假"}),
        "title": '数字员工"请假助手"发起审批 · 李明 年假 1天',
        "summary": "申请时间 09-12 08:30｜年假余额 5 天｜来源: 会话审批卡点",
        "source_label": "请假助手", "icon": "🔔", "icon_bg": "#fff4e6",
        "submitted_at": NOW.replace(hour=8, minute=30, second=0, microsecond=0),
    },
    {
        "tool_id": 2, "tenant_id": 1, "session_id": None,
        "applicant_user_id": None, "applicant_name": "王芳",
        "status": 1, "version": 1,
        "form_schema": json.dumps({"type":"doc","doc_type":"通知","word_count":800}),
        "title": '办文助手生成文稿 · 王芳提交《关于组织企业参加供需对接会的通知》',
        "summary": "09-12 09:10｜公文写作技能｜已标注引用来源",
        "source_label": "办文助手", "icon": "📄", "icon_bg": "#eaf1ff",
        "submitted_at": TODAY_09,
    },
    {
        "tool_id": 3, "tenant_id": 1, "session_id": None,
        "applicant_user_id": None, "applicant_name": "张伟",
        "status": 1, "version": 1,
        "form_schema": json.dumps({"type":"kb_upload","filename":"2026年度产业报告.pdf","scope":"部门知识库"}),
        "title": '知识库入库申请 · 张伟上传《2026年度产业报告.pdf》',
        "summary": "09-12 10:02｜部门知识库｜需确认归属与可见范围",
        "source_label": "知识库入库", "icon": "📚", "icon_bg": "#e8f7ee",
        "submitted_at": TODAY_10,
    },
    # ===== 我的申请 (2) =====
    {
        "tool_id": 1, "tenant_id": 1, "session_id": 20,
        "applicant_user_id": 1, "applicant_name": "管理员",
        "status": 2, "version": 1,
        "form_schema": json.dumps({"type":"leave","days":0.5,"leave_type":"事假"}),
        "title": "我的请假申请 (09-09 事假 0.5 天)",
        "summary": "已流转至直属领导·通过后自动扣除假并通知",
        "source_label": "请假助手", "icon": "📝", "icon_bg": "#fff4e6",
        "submitted_at": NOW.replace(hour=9, minute=12, second=0, microsecond=0),
    },
    {
        "tool_id": 4, "tenant_id": 1, "session_id": 20,
        "applicant_user_id": 1, "applicant_name": "管理员",
        "status": 4, "version": 1,
        "form_schema": json.dumps({"type":"contract_review","filename":"采购合同.pdf","risks":2}),
        "title": "已办 · 采购合同初审 (09-08)",
        "summary": "风险条款 2 处已标注·意见已回传发起人",
        "source_label": "法律咨询", "icon": "✅", "icon_bg": "#e8f7ee",
        "submitted_at": NOW - timedelta(days=1),
    },
]

conn = pymysql.connect(host="127.0.0.1", port=3306, user="root", password="", database="aioa_agent", charset="utf8mb4")
try:
    with conn.cursor() as cur:
        # 清空旧测试数据
        cur.execute("DELETE FROM skill_forms WHERE title LIKE '%请假%' OR title LIKE '%办文%' OR title LIKE '%知识库入库%' OR title LIKE '%采购合同%' OR title LIKE '我的请假%'")
        print(f"已清理旧测试数据 {cur.rowcount} 行")
        for r in rows:
            cur.execute("""
                INSERT INTO skill_forms
                  (id_str, tool_id, session_id, applicant_user_id, applicant_name,
                   tenant_id, form_schema, version, status, title, summary,
                   source_label, icon, icon_bg, submitted_at)
                VALUES (%s,%s,%s,%s,%s, %s,%s,%s,%s,%s, %s,%s,%s,%s, %s)
            """, (
                fake_snowflake(), r["tool_id"], r["session_id"], r["applicant_user_id"], r["applicant_name"],
                r["tenant_id"], r["form_schema"], r["version"], r["status"], r["title"], r["summary"],
                r["source_label"], r["icon"], r["icon_bg"], r["submitted_at"]
            ))
            print(f"  + {r['title'][:50]}...")
        conn.commit()
        cur.execute("SELECT COUNT(*) FROM skill_forms")
        print(f"\n总 skill_forms 数: {cur.fetchone()[0]}")
        cur.execute("SELECT title, status, applicant_name FROM skill_forms ORDER BY id DESC LIMIT 5")
        print("最新数据:")
        for row in cur.fetchall():
            print(f"  · status={row[1]} {row[0][:60]}")
finally:
    conn.close()