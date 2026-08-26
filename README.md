# my-agent

对照 Ragent 的学习项目。当前覆盖：

- **依赖版本对齐 ragent**：Spring Boot **4.1.0**、Sa-Token 1.45、MyBatis-Plus 3.5.17、OkHttp 5.3.2 等
- **认证权限**：Sa-Token + Redis，角色 `admin` / `user`（用户管理需 admin）
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

- `token-style: simple-uuid`（非 JWT）
- Header：`Authorization: <token>`（不加 Bearer）
- 知识库接口路径：`/knowledge-base/**`（需登录）
- 用户管理：`/api/users/**`（需 `admin` 角色）

## 建议学习顺序

1. `KnowledgeDocumentServiceImpl.upload` / `startChunk`（对象存储与 DB 一致性策略）
2. `KnowledgeDocumentChunkConsumer` + RocketMQ 事务回查
3. `DefaultIngestionKernel` 五步管线
4. `RelationalChunkSink` + `VectorChunkSink`（同事务扇出）
5. 对照 ragent，下一阶段再补检索 / Agent
