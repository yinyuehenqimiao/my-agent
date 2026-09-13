# 阶段 E4：中文日志 UTF-8 一致性

## 阶段目标

统一 Maven、测试子进程和 Spring Boot 日志输出编码，避免 Windows 开发环境中中文日志、异常与编译提示出现乱码。

## 实现内容

- 增加 `.mvn/jvm.config`，让 Maven 启动时即使用 UTF-8 的默认、标准输出和标准错误编码。
- 在 Spring Boot 配置中显式设置控制台与文件日志字符集为 UTF-8。
- 增加配置测试，防止构建 JVM 或运行时日志编码被后续修改回平台默认值。

## 测试流程

1. 修复前执行包含中文意图日志的测试，输出出现 `��ǰ���⣺` 等乱码。
2. 执行 `mvn -pl rag -am -Dtest=DefaultIntentClassifierWorkflowTest test`，人工确认中文问题、意图树和越界告警可读。
3. 执行 `mvn -pl bootstrap -am -Dtest=Utf8ConfigurationTest test`，验证 JVM 与 Spring Boot 两层编码配置。
4. 最后执行根项目 `mvn test` 全量回归，通过后再提交本阶段。

验证结果：中文意图日志、异常堆栈与 javac 中文诊断均可正常读取；专项测试 3 项通过，根项目九个 Maven 模块全部 `BUILD SUCCESS`。

## 阶段边界

本阶段只修正字符编码，不处理既存的 Maven 镜像元数据与 Mockito 动态代理告警。
