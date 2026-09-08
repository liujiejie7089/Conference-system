# AIOA — Agent-era Office Automation

> 一期客户端 + 后端 4 最小服务（Spring Cloud）+ AgentScope 运行时
> 数据库：MySQL 8.0（localhost/root/无密码）+ Redis 7
> 详细规格见 `detail/` 目录

## 工程结构

```
AIOA/
├── client/                 # uni-app H5 客户端（一期首发 H5，后期适配小程序）
├── server/                 # Spring Cloud 后端多模块
│   ├── pom.xml             # 父 POM
│   ├── gateway/            # Spring Cloud Gateway（统一入口、JWT 校验）
│   ├── tenant-svc/         # 用户/租户/SSO
│   ├── session-svc/        # 会话生命周期、消息、SSE
│   ├── agent-svc/          # Agent Runtime 对接（HTTP 调 AgentScope Python 服务）
│   └── ledger-svc/        # 词元计量、额度扣减、账本
├── vendor/                 # 第三方仓库直接 clone（不 Fork）
│   └── agentscope/         # git clone https://github.com/modelscope/agentscope
├── deploy/                 # 部署编排
│   ├── docker-compose.yml
│   ├── mysql/init/         # 建表 SQL（容器首启自动执行）
│   └── .env.example        # 环境变量模板（含 DEEPSEEK_API_KEY）
├── docs/                   # 工程内 API 文档
├── detail/                 # 规格文档（已有）
└── 项目架构与开发周期说明.txt  # 已有
```

## 快速启动

```powershell
# 1. 启动基础设施
cd deploy
cp .env.example .env       # 填入 DEEPSEEK_API_KEY
docker compose up -d mysql redis

# 2. 启动后端
cd ../server
mvn clean package -DskipTests
java -jar gateway/target/gateway.jar
java -jar tenant-svc/target/tenant-svc.jar
# ...其余服务

# 3. 启动前端
cd ../client
npm install
npm run dev:h5
```

## 里程碑

| 阶段 | 周期 | 交付 |
|---|---|---|
| M1 骨架 | W1-W3 | 工程骨架 + 4 Tab + 登录 + 原型还原 |
| M2 核心链路 | W4-W8 | 会话→流式→词元记账→扣减全链路 |
| M3 安全闭环 | W9-W11 | 敏感词→审批卡点→回注 + 技能表单 + 引用 |
