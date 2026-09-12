# 本地基础中间件状态

最后验证：2026-09-11。

| 服务 | 容器 | 宿主机地址 | 项目用途 | 验证结果 |
| --- | --- | --- | --- | --- |
| PostgreSQL + pgvector | `postgres` | `127.0.0.1:5432` | 业务、知识库向量、Agent 状态、企业审批 | `myagent` 库、`vector` 扩展、企业和知识库表已就绪 |
| Redis | `redis` | `127.0.0.1:6379` | 登录、分布式锁、限流、任务协调 | 使用项目配置密码认证 PING 成功 |
| RocketMQ NameServer | `rmqnamesrv` | `127.0.0.1:9876` | 消息路由 | 已运行，应用生产者和两个消费者均已注册 |
| RocketMQ Broker | `rmqbroker` | `10909`、`10911-10912` | 文档分块、清理异步消息 | 已运行 |
| MinIO | `myagent-minio` | `127.0.0.1:9000`、`127.0.0.1:9001` | 制度文档、资产对象存储 | 两个业务桶已创建 |

MinIO：9000 是 S3 API，9001 是管理控制台。容器使用 Docker 卷 `myagent_minio_data` 持久化，并且设置为 `--restart unless-stopped`。

项目使用桶：`myagent-sources`、`myagent-assets`。对象存储连接参数已经与 `bootstrap/src/main/resources/application.yaml` 对齐，不需要额外改动。

## 常用检查

```powershell
docker ps
docker logs --tail 50 myagent-minio
docker exec redis redis-cli -a 123456 ping
docker exec postgres psql -U postgres -d myagent -c "SELECT extname FROM pg_extension WHERE extname='vector';"
```

## 启动应用

```powershell
cd D:\Project\AI\myAgent\my-agent
mvn -pl bootstrap -am package -DskipTests
java -jar bootstrap\target\bootstrap-0.0.1-SNAPSHOT.jar --spring.profiles.active=enterprise
```

`enterprise` profile 启用企业员工、请假审批和知识库授权能力。模型 Key 使用硅基流动或阿里百炼的环境变量后，再调用对话和向量化功能。

不要使用已停止的 `rustfs` 容器；项目当前使用的是 `myagent-minio`。
