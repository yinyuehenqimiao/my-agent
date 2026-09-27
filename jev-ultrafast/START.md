# Jev Ultrafast（本仓库集成）

源码来自 [browser-use/jev-ultrafast](https://github.com/browser-use/jev-ultrafast)（MIT）。

本目录在上游 inspector 前端基础上增加了 **Mock preview**（`JEV_MOCK=1` 或场景「Mock preview」），无需 TypeSafe / 文本模型 API Key 与真实浏览器即可查看决策面板。

## 快速启动（推荐：mock）

```bash
cd jev-ultrafast
uv sync
JEV_MOCK=1 uv run jev
```

浏览器打开：http://127.0.0.1:8766

1. 场景选 **Mock preview · no API keys**（默认）
2. 点 **Start demo**
3. 点 **Choose next** / **Run automatically** 查看 operation / target 概率与决策轨迹

## 完整 live demo（需要密钥 + Chrome）

```bash
cd jev-ultrafast
cp .env.example .env
# 填写 TYPESAFE_API_KEY、TEXT_MODEL_API_KEY
uv sync
uv run jev
```

然后用 Google Flights / fixture 场景跑真实 agent。Chrome 通过 Browser Harness 连接；若异常可执行 `uv run browser-harness --doctor`。

端口可用 `TYPESAFE_DEMO_PORT` 覆盖（默认 `8766`）。
