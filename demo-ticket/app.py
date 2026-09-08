"""
demo-ticket — 模拟企业工单管理系统
作为 AIOA 连接器引擎的接入示例业务系统
端口：8092

功能：
- 工单 CRUD（创建/查询/更新/删除）
- 标准 RESTful API
- Bearer Token 鉴权
- 自动生成 OpenAPI 文档（供连接器解析）
"""
import os
import time
from typing import Optional

from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel

app = FastAPI(
    title="demo-ticket 工单系统",
    description="模拟企业内部工单管理系统，供 AIOA 连接器引擎接入验证",
    version="1.0.0",
)

# ─── 模拟 Token 鉴权 ───
VALID_TOKEN = os.getenv("DEMO_TICKET_TOKEN", "demo-token-123")


def auth(authorization: str = Header(None)):
    """Bearer Token 校验"""
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "未授权：缺少 Bearer Token")
    token = authorization[7:]
    if token != VALID_TOKEN:
        raise HTTPException(401, "未授权：Token 无效")
    return token


# ─── 数据模型 ───
class Ticket(BaseModel):
    id: int
    title: str
    description: str
    status: str  # open / in_progress / resolved / closed
    priority: str  # low / medium / high / urgent
    creator: str
    assignee: str | None = None
    created_at: float
    updated_at: float


class CreateTicketRequest(BaseModel):
    title: str
    description: str
    priority: str = "medium"


class UpdateTicketRequest(BaseModel):
    title: str | None = None
    description: str | None = None
    status: str | None = None
    priority: str | None = None
    assignee: str | None = None


# ─── 内存存储 ───
_tickets: dict[int, Ticket] = {}
_next_id = 1


def _seed():
    """预置测试数据"""
    global _next_id
    now = time.time()
    for i, (title, desc, status, prio, creator, assignee) in enumerate([
        ("网络故障报修", "三楼办公区网络中断，影响 20 人办公", "open", "high", "张三", "IT运维组"),
        ("空调维修申请", "会议室空调不制冷，需上门维修", "in_progress", "medium", "李四", "后勤组"),
        ("门禁卡补办", "员工工牌丢失，需补办门禁卡", "resolved", "low", "王五", "安保组"),
    ], start=1):
        t = Ticket(
            id=i, title=title, description=desc, status=status,
            priority=prio, creator=creator, assignee=assignee,
            created_at=now - i * 3600, updated_at=now - i * 1800,
        )
        _tickets[i] = t
        _next_id = i + 1


_seed()


# ─── API 接口 ───

@app.get("/health")
async def health():
    return {"status": "UP", "service": "demo-ticket", "tickets_count": len(_tickets)}


@app.get("/api/tickets", summary="查询工单列表", description="支持按状态、关键词筛选")
async def list_tickets(
    status: Optional[str] = None,
    keyword: Optional[str] = None,
    authorization: str = Header(None),
):
    auth(authorization)
    result = list(_tickets.values())
    if status:
        result = [t for t in result if t.status == status]
    if keyword:
        kw = keyword.lower()
        result = [t for t in result if kw in t.title.lower() or kw in t.description.lower()]
    return {"code": 0, "data": [t.model_dump() for t in result]}


@app.get("/api/tickets/{ticket_id}", summary="查询工单详情")
async def get_ticket(ticket_id: int, authorization: str = Header(None)):
    auth(authorization)
    if ticket_id not in _tickets:
        raise HTTPException(404, f"工单 #{ticket_id} 不存在")
    return {"code": 0, "data": _tickets[ticket_id].model_dump()}


@app.post("/api/tickets", summary="创建工单")
async def create_ticket(req: CreateTicketRequest, authorization: str = Header(None)):
    auth(authorization)
    global _next_id
    now = time.time()
    ticket = Ticket(
        id=_next_id, title=req.title, description=req.description,
        status="open", priority=req.priority, creator="当前用户",
        created_at=now, updated_at=now,
    )
    _tickets[_next_id] = ticket
    _next_id += 1
    return {"code": 0, "data": ticket.model_dump()}


@app.put("/api/tickets/{ticket_id}", summary="更新工单")
async def update_ticket(ticket_id: int, req: UpdateTicketRequest, authorization: str = Header(None)):
    auth(authorization)
    if ticket_id not in _tickets:
        raise HTTPException(404, f"工单 #{ticket_id} 不存在")
    t = _tickets[ticket_id]
    data = req.model_dump(exclude_unset=True)
    for k, v in data.items():
        setattr(t, k, v)
    t.updated_at = time.time()
    return {"code": 0, "data": t.model_dump()}


@app.delete("/api/tickets/{ticket_id}", summary="删除工单")
async def delete_ticket(ticket_id: int, authorization: str = Header(None)):
    auth(authorization)
    if ticket_id not in _tickets:
        raise HTTPException(404, f"工单 #{ticket_id} 不存在")
    del _tickets[ticket_id]
    return {"code": 0, "data": {"deleted": ticket_id}}


if __name__ == "__main__":
    import uvicorn
    print(f"demo-ticket starting on port 8092")
    uvicorn.run(app, host="0.0.0.0", port=8092)
