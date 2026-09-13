# 阶段 G2：ERP 中文连接状态

- 企业配置页新增只读“ERP 连接状态”，用中文说明当前是本地数据还是独立 ERP、配置是否完整及下一步操作。
- 页面不提供任意地址、服务令牌或写操作输入，避免管理员误将 ERP 凭证和请求发往非受控地址。
- 后端仅向具有企业管理权限的用户返回固定部署配置摘要，服务令牌不会返回给浏览器。

验证：`mvn -pl enterprise -am -Dtest=EnterpriseWorkflowTest test` 的 30 项测试通过；`frontend npm run build` 成功。
