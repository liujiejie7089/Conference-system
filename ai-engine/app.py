"""
DeepSeek Harness — AI 推理 Sidecar 微服务
职责：大模型推理、意图识别、Function Calling 路由
与 Java 主业务系统技术栈解耦，通过 HTTP 通信
"""
import os
import json
import asyncio
from typing import Any

from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse, JSONResponse
from pydantic import BaseModel

# ─── 配置 ───
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
MOCK_MODE = os.getenv("MOCK_MODE", "true").lower() == "true"
SIDECAR_PORT = int(os.getenv("SIDECAR_PORT", "8091"))

# ─── DeepSeek 客户端（惰性初始化）───
_client = None


def get_client():
    """获取 OpenAI 兼容客户端（用于调用 DeepSeek API）"""
    global _client
    if _client is None and DEEPSEEK_API_KEY:
        from openai import OpenAI
        _client = OpenAI(api_key=DEEPSEEK_API_KEY, base_url=DEEPSEEK_BASE_URL)
    return _client


# ─── FastAPI 应用 ───
app = FastAPI(title="AIOA DeepSeek Harness", version="1.0.0")


# ─── 请求/响应模型 ───
class ChatMessage(BaseModel):
    role: str  # system / user / assistant / tool
    content: str
    tool_call_id: str | None = None
    tool_calls: list[dict] | None = None


class ChatRequest(BaseModel):
    messages: list[ChatMessage]
    tools: list[dict] | None = None  # OpenAI Function Calling 格式
    session_id: str | None = None
    agent_id: str | None = None
    stream: bool = False
    temperature: float = 0.7
    max_tokens: int | None = None


# ─── 健康检查 ───
@app.get("/health")
async def health():
    return {
        "status": "UP",
        "service": "deepseek-harness",
        "model": DEEPSEEK_MODEL,
        "mock_mode": MOCK_MODE or not DEEPSEEK_API_KEY,
        "api_key_configured": bool(DEEPSEEK_API_KEY),
    }


# ─── Mock 响应（无 API Key 时用于开发调试）───
MOCK_ANSWER = (
    "根据你知识库中《2026年产业扶持政策汇编》：\n\n"
    "针对你的问题：我市对开展人工智能应用改造的企业给予最高 30% 的算力使用补贴，"
    "单户企业年度补贴上限 50 万元。申报需通过市经信局线上平台提交，"
    "截止时间为每年 6 月与 12 月两批。"
)


def estimate_input_tokens(messages: list[ChatMessage]) -> int:
    """粗略估算输入词元数（约 4 字符 ≈ 1 token），用于 mock 模式分列计量"""
    total = 0
    for m in messages:
        total += len(m.content or "") + 4
    return max(total // 4, 1)


def mock_chat_response(messages: list[ChatMessage], tools: list[dict] | None = None):
    """Mock 模式：模拟 DeepSeek 返回"""
    user_msg = ""
    for m in reversed(messages):
        if m.role == "user":
            user_msg = m.content
            break

    # 检测是否为第二轮（Tool 结果回传后）
    has_tool_result = any(m.role == "tool" for m in messages)
    if has_tool_result:
        # 汇总 Tool 结果
        tool_results = [m.content for m in messages if m.role == "tool"]
        summary = "根据查询结果，我找到了以下信息：\n\n"
        for tr in tool_results:
            try:
                data = json.loads(tr)
                if "data" in data and isinstance(data["data"], list):
                    items = data["data"]
                    summary += f"共找到 {len(items)} 条记录：\n"
                    for item in items[:5]:
                        title = item.get("title", "未知")
                        status = item.get("status", "未知")
                        summary += f"  - {title}（状态：{status}）\n"
                else:
                    summary += f"工具返回：{tr[:200]}\n"
            except (json.JSONDecodeError, TypeError):
                summary += f"工具返回：{tr[:200]}\n"
        return {"content": summary, "tool_calls": None, "tokens": 150}

    # 模拟 Function Calling：如果用户问工单相关，触发 tool_call
    if tools and ("工单" in user_msg or "ticket" in user_msg.lower()):
        # 根据用户意图选择合适的工具
        target_tool = None
        target_args = {}

        for tool in tools:
            func = tool.get("function", {})
            name = func.get("name", "")

            if "查询" in user_msg or "列表" in user_msg or "查看" in user_msg or "list" in user_msg.lower():
                if "get" in name and "ticket_id" not in name:
                    target_tool = name
                    target_args = {}
                    break
            elif "创建" in user_msg or "新建" in user_msg or "提交" in user_msg or "create" in user_msg.lower():
                if "post" in name:
                    target_tool = name
                    target_args = {"title": "新工单", "description": "用户创建的工单", "priority": "medium"}
                    break
            elif "删除" in user_msg or "delete" in user_msg.lower():
                if "delete" in name:
                    target_tool = name
                    target_args = {"ticket_id": 1}
                    break
            elif "更新" in user_msg or "修改" in user_msg or "update" in user_msg.lower():
                if "put" in name:
                    target_tool = name
                    target_args = {"ticket_id": 1, "title": "更新后的工单"}
                    break

        # 默认：如果用户提到工单但没有明确意图，选查询列表
        if not target_tool:
            for tool in tools:
                func = tool.get("function", {})
                name = func.get("name", "")
                if "get" in name and "ticket_id" not in name:
                    target_tool = name
                    target_args = {}
                    break

        if target_tool:
            return {
                "content": "",
                "tool_calls": [{
                    "id": "call_mock_001",
                    "type": "function",
                    "function": {
                        "name": target_tool,
                        "arguments": json.dumps(target_args, ensure_ascii=False),
                    },
                }],
                "tokens": 128,
            }

    return {"content": MOCK_ANSWER, "tool_calls": None, "tokens": 200}


# ─── 聊天接口（非流式）───
@app.post("/api/v1/chat")
async def chat(req: ChatRequest):
    """非流式聊天，返回完整响应"""
    use_mock = MOCK_MODE or not DEEPSEEK_API_KEY

    if use_mock:
        result = mock_chat_response(req.messages, req.tools)
        return JSONResponse({
            "code": 0,
            "data": {
                "content": result["content"],
                "tool_calls": result["tool_calls"],
                "tokens_used": result["tokens"],
                "model": "mock" if use_mock else DEEPSEEK_MODEL,
            },
        })

    # 真实调用 DeepSeek API
    client = get_client()
    api_messages = [{"role": m.role, "content": m.content} for m in req.messages]
    if any(m.tool_calls for m in req.messages):
        # 包含 tool_calls 和 tool 结果的消息
        api_messages = []
        for m in req.messages:
            msg = {"role": m.role, "content": m.content}
            if m.tool_call_id:
                msg["tool_call_id"] = m.tool_call_id
            if m.tool_calls:
                msg["tool_calls"] = m.tool_calls
            api_messages.append(msg)

    kwargs: dict[str, Any] = {
        "model": DEEPSEEK_MODEL,
        "messages": api_messages,
        "temperature": req.temperature,
    }
    if req.tools:
        kwargs["tools"] = req.tools
    if req.max_tokens:
        kwargs["max_tokens"] = req.max_tokens

    resp = client.chat.completions.create(**kwargs)
    choice = resp.choices[0].message
    return JSONResponse({
        "code": 0,
        "data": {
            "content": choice.content or "",
            "tool_calls": [tc.to_dict() for tc in (choice.tool_calls or [])] or None,
            "tokens_used": resp.usage.total_tokens if resp.usage else 0,
            "model": DEEPSEEK_MODEL,
        },
    })


# ─── 流式聊天接口（SSE）───
@app.post("/api/v1/chat/stream")
async def chat_stream(req: ChatRequest):
    """SSE 流式输出，逐 token 返回"""
    use_mock = MOCK_MODE or not DEEPSEEK_API_KEY

    async def event_generator():
        if use_mock:
            # Mock 流式输出
            result = mock_chat_response(req.messages, req.tools)
            input_tokens = estimate_input_tokens(req.messages)
            output_tokens = result["tokens"]
            done_event = {
                "type": "done",
                "tokens_used": input_tokens + output_tokens,
                "input_tokens": input_tokens,
                "output_tokens": output_tokens,
                "model": "mock",
            }
            if result["tool_calls"]:
                # 先返回 tool_call
                yield f"data: {json.dumps({'type': 'tool_call', 'tool_calls': result['tool_calls']}, ensure_ascii=False)}\n\n"
                yield f"data: {json.dumps(done_event, ensure_ascii=False)}\n\n"
                return
            # 逐字输出文本
            text = result["content"]
            for i in range(0, len(text), 3):
                chunk = text[i:i + 3]
                yield f"data: {json.dumps({'type': 'text', 'content': chunk}, ensure_ascii=False)}\n\n"
                await asyncio.sleep(0.05)
            yield f"data: {json.dumps(done_event, ensure_ascii=False)}\n\n"
            return

        # 真实 DeepSeek 流式调用
        client = get_client()
        api_messages = [{"role": m.role, "content": m.content} for m in req.messages]
        kwargs: dict[str, Any] = {
            "model": DEEPSEEK_MODEL,
            "messages": api_messages,
            "temperature": req.temperature,
            "stream": True,
            "stream_options": {"include_usage": True},
        }
        if req.tools:
            kwargs["tools"] = req.tools

        stream = client.chat.completions.create(**kwargs)
        input_tokens = 0
        output_tokens = 0
        for chunk in stream:
            if chunk.choices and chunk.choices[0].delta:
                delta = chunk.choices[0].delta
                if delta.content:
                    yield f"data: {json.dumps({'type': 'text', 'content': delta.content}, ensure_ascii=False)}\n\n"
                if delta.tool_calls:
                    for tc in delta.tool_calls:
                        yield f"data: {json.dumps({'type': 'tool_call', 'tool_calls': [tc.to_dict()]}, ensure_ascii=False)}\n\n"
            if chunk.usage:
                input_tokens = chunk.usage.prompt_tokens
                output_tokens = chunk.usage.completion_tokens
        yield f"data: {json.dumps({'type': 'done', 'tokens_used': input_tokens + output_tokens, 'input_tokens': input_tokens, 'output_tokens': output_tokens, 'model': DEEPSEEK_MODEL}, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


# ─── 启动 ───
if __name__ == "__main__":
    import uvicorn
    print(f"DeepSeek Harness starting on port {SIDECAR_PORT}")
    print(f"  Mock mode: {MOCK_MODE or not DEEPSEEK_API_KEY}")
    print(f"  API key configured: {bool(DEEPSEEK_API_KEY)}")
    uvicorn.run(app, host="0.0.0.0", port=SIDECAR_PORT)
