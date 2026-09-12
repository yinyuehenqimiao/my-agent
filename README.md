# my-agent

## 企业知识与请假审批

新增 `enterprise` 模块：企业身份、年假账户、主管/人事两级审批、确认卡、审批进度与审计；现有 Agent 可调用企业工具，知识问答提供来源并按知识库授权过滤。

完整启动、迁移、演示账号与评测说明见 [企业演示指南](resources/enterprise/README.md)。先应用 v0.4.0 增量 SQL，再以 `enterprise` profile 启动。会议与外部企业平台不在本版范围内。

Maven 固定使用 `D:\maven\apache-maven-3.6.3\conf\settings.xml` 和 `D:\maven\apache-maven-3.6.3\repository`，配置位于 `.mvn/maven.config`。

对照 Ragent 的学习项目。当前覆盖：

- **依赖版本对齐 ragent**：Spring Boot **4.1.0**、Sa-Token 1.45、MyBatis-Plus 3.5.17、OkHttp 5.3.2 等
- **认证权限**：Sa-Token JWT（无状态验签）+ Redis（业务缓存与分布式能力），角色 `admin` / `user`（用户管理需 admin）
- **知识库上传链路**：对象存储 → DB → RocketMQ 事务消息触发 → 解析 / 分片 / 向量化 / 双落点存储（CHUNK 模式）
- **后台**：`frontend/` 管理端（登录 + 知识库上传）
- **infra-ai**：OpenAI 兼容 Chat / Embedding

## 模块

| 模块 | 职责 |
|---|---|
| `framework` | Result、异常、UserContext(TTL)、MQ、Snowflake、OpenAPI |
| `system` | Sa-Token、用户/角色、用户 CRUD（admin） |
| `infra-ai` | Chat / Embedding / LLMService |
| `rag` | 知识库上传、解析、分块、向量化、对象存储 |
| `agent` | AgentScope ReAct、工具目录、流式事件与会话状态 |
| `enterprise` | 企业身份、请假与两级审批、确认操作、知识授权 |
| `bootstrap` | **仅启动入口**（无业务代码） |
| `frontend` | 后台管理页 |

## 依赖服务

| 服务 | 用途 | 备注 |
|---|---|---|
| PostgreSQL | 业务库 `myagent` + pgvector | 勿与 ragent 库混用 |
| Redis | Sa-Token / Redisson 信号量 / Snowflake | Key 前缀 `myagent:` |
| MinIO/S3 | 知识库对象存储 | 桶：`myagent-sources` / `myagent-assets` |
| RocketMQ | 文档分块事务消息 | NameServer `127.0.0.1:9876` |

## 1. 初始化数据库

```powershell
cd D:\Project\AI\myAgent\my-agent
.\resources\database\init.ps1
```

已有库大版本升级时，执行对应脚本，例如：

```powershell
Get-Content .\resources\database\upgrades\v0.2.0\01_knowledge.sql -Raw |
  docker exec -i postgres psql -U postgres -d myagent -v ON_ERROR_STOP=1
```

## 2. 启动后端

```powershell
cd D:\Project\AI\myAgent\my-agent
$env:SILICONFLOW_API_KEY="sk-xxx"   # 向量化需要
mvn -pl bootstrap -am spring-boot:run
```

端口：`18080`  
Swagger：http://localhost:18080/swagger-ui.html

默认账号（启动时初始化/同步）：

| 用户 | 密码 | 角色 |
|---|---|---|
| admin | admin | admin |
| demo | user123 | user |

## 3. 启动后台前端

```powershell
cd D:\Project\AI\myAgent\my-agent\frontend
npm install
npm run dev
```

打开 http://127.0.0.1:5173 ，使用 **admin / admin** 登录。

## 4. 知识库上传闭环

1. 后台创建知识库（指定 embedding 模型，如 `BAAI/bge-m3`）
2. 进入文档页上传文件（先对象存储，再写 `t_knowledge_document`，status=`pending`）
3. 点击「开始分块」→ RocketMQ 事务消息 → Consumer → `DefaultIngestionKernel`：parse → chunk → embed → 关系库 + pgvector
4. Redis / 桶名均带 `myagent` 前缀，与 ragent 隔离

## 认证说明

- 认证依赖：`cn.dev33:sa-token-jwt`；使用无状态 JWT 登录逻辑。
- Header：`Authorization: Bearer <JWT>`
- 本地开发使用配置中的默认 JWT 密钥；部署环境请设置 `SA_TOKEN_JWT_SECRET_KEY` 为独立的高强度随机字符串后再启动服务。
- 知识库接口路径：`/knowledge-base/**`（需登录）
- 用户管理：`/api/users/**`（需 `admin` 角色）

## 本地环境与启动

1. 编辑根目录 `.env`，填写 `SILICONFLOW_API_KEY`，或切换为百炼并填写 `BAILIAN_API_KEY`。
2. 执行 `powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1`。

`.env` 包含本机数据库、Redis、RocketMQ、MinIO 与模型平台参数，已被 Git 忽略；可提交的配置模板为 `.env.example`。

## 建议学习顺序

1. `KnowledgeDocumentServiceImpl.upload` / `startChunk`（对象存储与 DB 一致性策略）
2. `KnowledgeDocumentChunkConsumer` + RocketMQ 事务回查
3. `DefaultIngestionKernel` 五步管线
4. `RelationalChunkSink` + `VectorChunkSink`（同事务扇出）
5. 阅读 `agent` 与 `enterprise`，理解工具准备、用户确认与业务事务的边界
