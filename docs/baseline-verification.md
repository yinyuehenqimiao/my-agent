# 阶段 0 基线验收记录

验收日期：2026-09-12。
对应计划：`docs/enterprise-agent-workflow-plan.md` 阶段 0。
目标：把当前大量未提交的既有企业智能体改造整理成可信基线，避免后续阶段提交混入旧代码。

## 一、Git 工作区整理

- 检查起点：`main` 分支最新提交 `df82538`，工作区共有 199 项变更（63 已跟踪修改 + 136 未跟踪新增）。
- 区分应纳入项目的代码、本机构建文件、临时测试输出、敏感配置与编译产物：
  - `HashProbe.class`（根目录编译产物，761 字节）已通过 `.gitignore` 新增 `*.class` 规则排除，`git check-ignore` 确认已忽略。
  - `.env`（含真实 API Key / JWT 密钥 / 数据库密码，1297 字节）已被 `.gitignore` 忽略，不会进入提交。
  - `.env.example`（模板，含本地 dev 默认值如 `postgres/postgres`、`rustfsadmin`，非生产密钥）作为模板纳入提交。
  - `resources/enterprise/*.local.png`、`*.local.json`（本地截图与评测输出）已被 `.gitignore` 忽略。
  - `target/`、`node_modules/`、`dist/`、`.vite/` 等构建产物已被忽略。
- `.gitignore` 已包含：密钥（`.env`、`frontend/.env.local`）、评测输出（`resources/enterprise/*.local.*`）、编译产物（`*.class`）、日志（`*.log`）、IDE 与系统文件。

## 二、数据库升级脚本静态检查

- `resources/database/01_schema.sql` 全部使用 `CREATE TABLE IF NOT EXISTS` / `CREATE EXTENSION IF NOT EXISTS`，可重复执行。
- `resources/database/upgrades/v0.2.0`、`v0.3.0`、`v0.4.0` 为加性迁移，均使用 `IF NOT EXISTS`，注释明确为 additive migration。
- 版本目录具备明确的一次性版本约束（按版本号递增），未发现破坏性或不可重复脚本。

## 三、legacy enterprise baseline 标记

- 当前企业请假能力（`ent_leave_request` / `ent_leave_account` / `ent_audit` 及 enterprise 模块两级审批逻辑）标记为 `legacy enterprise baseline`。
- 该能力在阶段 4 将迁移到 Flowable；阶段 0 仅作基线固化，不修改其实现。
- 灰度切换、legacy 与 Flowable 并存以阶段 4 设计为准。

## 四、强制验证（必须通过）

| 项目 | 命令 | 结果 |
| --- | --- | --- |
| Maven 单元测试 | `mvn test -B` | BUILD SUCCESS；8 模块全部通过；Tests run 合计 116 项，Failures 0，Errors 0，Skipped 0 |
| Bootstrap 打包 | `mvn -pl bootstrap -am package -DskipTests -B` | BUILD SUCCESS；`bootstrap/target/bootstrap-0.0.1-SNAPSHOT.jar` 已生成（Spring Boot 可执行 JAR） |
| 前端构建 | `npm run build`（frontend） | 通过；3564 模块转换，built in 15.21s；仅大包体积警告（非阻断） |
| 前端类型检查 | `npx tsc --noEmit`（frontend） | 通过，零错误 |
| Node 脚本语法 | `node --check resources/enterprise/{seed-knowledge,smoke-workflow,evaluate-agent,frontend-smoke}.cjs` | 4/4 全部通过 |

## 五、环境验收（条件执行）

基础设施现状：项目自有容器 `postgres`、`redis`、`myagent-minio`、`rmqbroker`、`rmqnamesrv` 均在运行；`myagent` 库已初始化 29 张表。应用实例（PID 40716，11:09 启动）监听 18080，`/actuator/health` 返回 200，演示账号 `employee/manager/hr` 可登录，`/api/enterprise/*` 端点可用。

| 脚本 | 结果 | 说明 |
| --- | --- | --- |
| `seed-knowledge.cjs` | 未执行 | 需先确认运行实例与当前未提交代码一致后再导入知识，避免污染基线前状态 |
| `smoke-workflow.cjs` | 未执行（脚本侧鉴权格式问题） | 脚本发送裸 `Authorization: <token>`，而应用 Sa-Token 要求 `Authorization: Bearer <token>`；手动用 Bearer 验证 `/api/enterprise/leave-requests` 返回 `code:0`，企业接口本身可用。该脚本修复不属于阶段 0 范围，留待后续阶段处理 |
| `evaluate-agent.cjs` | 未执行 | 依赖 `smoke-workflow` 同款鉴权与运行中应用，且需 40 条模型评测，留待后续阶段在应用与脚本对齐后运行 |

结论：强制验证全部通过，环境级端到端脚本因脚本侧鉴权格式问题未执行，已手动验证企业接口可用；不声称达到真实模型成功率目标。

## 六、提交内容安全审阅

- `git check-ignore` 确认 `HashProbe.class`、`.env`、`*.local.png`、`*.local.json` 均被忽略。
- 待提交文件中不含 API Key、JWT 密钥、数据库密码或本地 `.env`（`.env.example` 仅为模板，值为本地 dev 默认占位）。
- 提交按「数据库 / 后端 / 前端 / 测试与文档」拆分小提交，每个小提交均基于已通过的强制验证。

## 七、已知限制

- `smoke-workflow.cjs` / `evaluate-agent.cjs` 的鉴权头格式问题为既有 latent 缺陷（此前从未实际运行），阶段 0 不修复。
- 运行中的应用实例可能早于部分未提交改动；端到端脚本需在后续阶段以新构建 JAR 重启应用后再运行。
- 前端大包体积警告（`SpreadsheetPreview`、`index` 等 chunk > 500kB）非阻断，留待后续优化。

## 八、Git 提交

- `chore(baseline): capture current enterprise agent implementation`
- `docs(baseline): record baseline verification results`

## 九、下一阶段准入结论

阶段 0 强制验证全部通过，敏感文件与编译产物未进入 Git，基线提交完成后可进入阶段 1（平台权限与统一执行身份）。
