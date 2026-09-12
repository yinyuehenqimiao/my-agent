# 企业知识与请假审批演示

## 启动

项目使用 `D:\maven\apache-maven-3.6.3\conf\settings.xml` 和本地仓库 `D:\maven\apache-maven-3.6.3\repository`，已写入 `.mvn/maven.config`。其他机器请调整该本地配置。

中间件容器、端口和检查命令见 [MIDDLEWARE.md](MIDDLEWARE.md)。

1. 启动项目现有 PostgreSQL/pgvector、Redis、MinIO/S3、RocketMQ，并按 `application.yaml` 配置连接和模型密钥。已有库只执行增量 SQL `resources/database/upgrades/v0.4.0/01_enterprise.sql`；新库执行 `resources/database/init.ps1`。脚本必须指向项目自己的 `myagent` 库，不要指向其他项目库。
2. 在项目根目录运行 `mvn package -DskipTests`，然后运行 `java -jar bootstrap/target/bootstrap-0.0.1-SNAPSHOT.jar --spring.profiles.active=enterprise`。该 profile 启用企业模块及演示账号，关闭公网检索。原默认 profile 不自动启用企业功能。
3. 在 `frontend` 下运行 `npm run dev`，打开前端登录页。聊天侧栏进入“请假与审批工作台”，管理员在“企业配置”维护关系、授权及工作日历。

演示账号 `employee`、`manager`、`hr` 的初始密码均为 `Enterprise123!`，分别为员工、主管、人事。已有同名账号不会重置密码；原管理员账号沿用项目配置。管理员不自动拥有业务审批权。

### 演示账号速查

| 账号 | 初始密码 | 身份与用途 |
| --- | --- | --- |
| `employee` | `Enterprise123!` | 员工：提交、撤回和查看自己的请假申请与进度。 |
| `manager` | `Enterprise123!` | 直属主管：处理第一阶段主管审批。 |
| `hr` | `Enterprise123!` | 人事：处理第二阶段人事审批。 |
| `admin` | `admin` | 系统管理员：维护知识库、企业配置、审批关系和工作日历。 |

管理员账号不自动获得主管或人事审批权限；请使用对应业务账号完成审批演示。

## 导入制度和演示

运行 `node resources/enterprise/seed-knowledge.cjs`，通过现有上传和分块链路导入 `请假制度-v1.0.md`，设置全员可读、版本 v1.0、生效日期 2026-09-01。可通过 `ENTERPRISE_BASE_URL`、`ENTERPRISE_ADMIN`、`ENTERPRISE_ADMIN_PASSWORD`、`ENTERPRISE_EMBEDDING_MODEL` 指定环境；脚本不打印凭据。状态保存在本地忽略文件，重复执行不会重复上传已经记录的文档。

分块是异步操作，需要在管理后台确认完成。模型需支持工具调用；向量模型必须与知识库配置匹配。

员工可输入：“请假要经过谁审批？”、“我今年剩几天年假？”、“帮我准备下周一的事假，原因是搬家”。缺少参数时先补充，生成卡片后点击确认。切换主管账号确认审批，再切换人事账号确认审批，员工查看时间线。

`node resources/enterprise/smoke-workflow.cjs` 会在三个演示账号下真实提交并通过一笔一天事假，用于验证 API 闭环，不消耗年假。它会留下演示申请与审计记录。

## 测试与模型评测

- `mvn test`：原有回归、企业事务/权限/并发测试和 Agent 工具测试。
- `npm run build` 与 `npx tsc --noEmit`：在 frontend 下验证前端。
- `node resources/enterprise/evaluate-agent.cjs`：40 条固定模型评测，覆盖制度、工具、多轮补参和安全行为。使用 `EVAL_BASE_URL`、`EVAL_USERNAME`、`EVAL_PASSWORD`；默认是 employee 演示账号。仅生成草稿，不确认办理。
- 输出 `evaluation-results.local.json` 记录模型、工具事件、耗时及逐条审阅要求。工具路由自动检查通过不等于回答语义正确；需人工审阅 `semanticReview` 才能核算 90% 目标成功率。未运行时不声称达标。
- `frontend-smoke.cjs` 是使用模拟接口的浏览器测试，需要 Playwright 和本机 Edge。先在 frontend 运行 `npm run dev -- --host 127.0.0.1 --port 5175`，再运行该脚本；可用 `PLAYWRIGHT_MODULE_PATH` 指定已有 Playwright 模块，用 `PLAYWRIGHT_CHANNEL` 指定浏览器。

## 接口约定

全部位于 `/api/enterprise`，需要现有登录 token：`me`、`leave-balances?year=2026`、`leave-requests`、`leave-requests/{id}`、`approval-tasks`、`actions?conversationId=...`。

`POST leave-requests/prepare` 接收 conversationId、requestKey、leaveType（ANNUAL/PERSONAL）、startDate、endDate、reason；`POST approval-tasks/{id}/decision/prepare` 中 id 为**请假申请 ID**，接收 conversationId、requestKey、decision（APPROVE/REJECT）、comment。`POST leave-requests/{id}/withdrawal/prepare` 接收 conversationId、requestKey。

`POST actions/{id}/confirm` 或 `cancel` 不接收新业务参数。操作绑定原用户、会话和参数；确认15分钟有效，重试返回原结果。确认和申请更新同事务提交。创建卡片的 requestKey 由调用端唯一生成，同一请求重试应复用原 key。

原 SSE `tool` 事件及历史工具块包含结构化结果，前端据此恢复卡片；另提供 `action_card`、`action_result`、`sources` 事件。确认发生在独立 REST 请求中，卡片直接使用该响应更新，不伪造旧聊天 SSE 仍在线。

知识源预览：`GET knowledge/sources/{docId}/{chunkId}`，验证知识库权限、文档启用状态及片段归属。知识库未授权时仅管理员可读。当前员工角色与原系统 admin/user 角色独立。

## 限制与恢复

本地演示采用固定10天年度年假、整天申请；不覆盖真实公司的法定假期、薪酬、销假或考勤政策。工作台列表最多显示最近200项，确认卡最多读取100项。阶段一仅请假闭环，会议和外部企业平台尚未接入。

申请、审批、确认与审计全部在 PostgreSQL 中。重启不丢失；刷新卡片读取服务器状态。停用企业 profile 即关闭新增入口；回退应用不删除新增表和记录。

本次实现的实际验证结果见 `VERIFICATION.md`。
