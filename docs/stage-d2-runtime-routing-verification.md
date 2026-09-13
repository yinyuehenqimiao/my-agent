# 阶段 D2：聊天运行时路由与固定流程隔离验证

## 阶段目标

让真实聊天入口执行后台路由结果，而不是无条件进入通用 ReAct。运行分支如下：

| 路由结果 | 实际执行管线 | 安全边界 |
| --- | --- | --- |
| `WORKFLOW` | 请假固定流程专用 Agent | 只装载企业请假工具；写操作只生成确认卡 |
| `SYSTEM + KB` | 直接 RAG 检索与证据合成 | 文档权限由知识访问服务校验；不开放任意工具 |
| `SYSTEM` | 确定性系统回答 | 不调用模型和工具 |
| `REACT` | 通用 ReAct | 工具执行仍经过 Tool Gateway 二次鉴权 |
| `CLARIFY` | 确定性追问 | 不调用模型和工具 |
| `DENY` | 确定性拒绝 | 发送策略拒绝事件，不调用模型和工具 |

## 本阶段实现

- 新增 `ChatRoutePlanner`，将意图分类结果与 `RoutingService` 组合为不可变路由快照。
- 聊天服务在产生任何 Agent 调用前发送 `route_decision`，并按路由结果选择真实执行管线。
- 待确认动作从当前登录用户和当前会话查询，客户端不能传入或伪造身份。
- 待确认流程绕过意图分类器，分类服务不可用时仍能继续既有确认流程。
- 请假固定流程使用独立 Agent 与最小工具集，不包含知识库工具和 MCP 工具。
- 企业工具向模型暴露真实 JSON Schema，不再使用“任意字段均可”的空 Schema。
- 固定流程工具调用标记为 `WORKFLOW` 来源，继续通过 Tool Gateway 做来源、参数、权限、脱敏和审计校验。
- 知识库直答异步执行，保留用户上下文，来源以工具块形式实时展示并随消息持久化。
- SSE 增加路由、流程进度、策略拒绝的前端处理和中文提示。
- 用 `Disposable.Swap` 消除“用户取消早于上游订阅完成”时无法中断的竞态。
- 直接回答即使落库失败，也会发送 `finish` 和 `done`，避免前端永久停在生成状态。

## 测试流程与结果

### 红灯复现

1. 新增规划器测试后，编译因 `ChatRoutePlanner` 不存在而失败，证明聊天层缺少实际路由编排。
2. 新增待确认动作测试后，结果为 `CLARIFY` 而不是 `WORKFLOW`，证明既有动作错误依赖意图分类。
3. 接入分支后的首次聊天回归出现 6 个失败，定位到同一任务被重复注册两次，取消回调存在覆盖和竞态风险。

### 定向回归

```powershell
mvn -pl agent -am "-Dtest=RoutingServiceTest,ChatRoutePlannerTest,EnterprisePendingActionResolverTest,AgentChatServiceImplTest,EnterpriseToolsTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
npx tsc --noEmit
npm test -- --run src/hooks/useAgentStream.test.ts
```

结果：后端 41 个定向测试通过；TypeScript 检查通过；SSE 路由协议测试通过。

### 全量回归

```powershell
mvn test
npm test -- --run
npm run build
```

结果：

- Maven 九模块 `BUILD SUCCESS`；本次全量执行中 agent 模块 106 个测试通过。
- 前端 3 个测试文件、7 个测试通过。
- Vite 生产构建通过，共转换 3971 个模块。

## 已知非阻断项

- Maven 在 Windows 控制台仍有中文日志乱码。
- `KnowledgeAccessTest` 的测试表缺少审计表 ID 默认值，导致一条被服务吞并记录的审计写入警告；列入阶段 E 修复。
- Vite 主包和表格预览包仍有大体积警告，列入后续前端性能优化。
- 路由绑定的后台增删改查界面尚未实现；D2 只完成真实运行时消费，后台配置闭环属于下一子阶段。
- 当前固定流程工具白名单只支持 `leave-approval`。新增流程必须先显式定义专用工具集，未知流程会拒绝启动。
