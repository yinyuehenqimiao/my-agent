# 阶段 G1：受控 HTTP ERP 适配器

## 实现内容

- 将原先始终抛出“尚未接入”的 `HttpEnterpriseSystemAdapter` 替换为真实 HTTP 调用。
- 通过 `ENTERPRISE_ADAPTER=LOCAL|HTTP_ERP` 选择本地回退或独立 ERP；默认仍为 `LOCAL`，避免开发环境被意外切换。
- HTTP 模式只从可信 `ExecutionIdentity` 生成 `X-Tenant-Id`、`X-Employee-Id`，不会接受模型指定的查询对象。
- 所有调用附带 ERP 服务令牌和新的链路标识；写请求传递调用方生成的 `Idempotency-Key`。
- 本人档案、年假余额、创建请假三项端口均映射到独立 ERP API；非 2xx、非业务成功码、超时和连接失败统一返回明确的“ERP 服务暂时不可用”，禁止生成虚假成功。
- 修复 Flowable 两个启动类的日志字段生成依赖，改为显式 SLF4J 字段；未改动流程逻辑。

## 测试

```powershell
mvn -pl enterprise -am "-Dtest=HttpEnterpriseSystemAdapterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn test
```

专项测试覆盖服务令牌、租户与员工身份头、中文档案映射、余额映射及 ERP 连接失败安全拒绝。
