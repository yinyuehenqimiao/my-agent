# 阶段 G3：ERP 写操作链路标识透传

HTTP ERP 写请求现在将上游 `LeaveRequestCommand.traceId` 原样写入 `X-Trace-Id`，并保留同一 `Idempotency-Key`。这使聊天、工具网关、工作流、ERP 请假单和 ERP 审计能够按同一链路标识对账。

测试：`HttpEnterpriseSystemAdapterTest` 新增写请求断言；专项 3 项通过，根项目 Maven 九模块全量 `BUILD SUCCESS`。
