# my-agent 管理前端

最小可用的知识库管理后台（登录 + 知识库 CRUD + 文档上传/分块），风格对齐 ragent frontend。

## 启动

```bash
cd my-agent/frontend
npm install
npm run dev
```

浏览器打开 http://127.0.0.1:5173

默认管理员账号：`admin` / `admin`（需后端已初始化该用户）

## 构建

```bash
npm run build
```

产物在 `dist/`。

## 环境变量

`.env`：

```
VITE_API_BASE_URL=http://localhost:18080
```

- 直接指向后端（后端已开启 CORS）
- 若改为空字符串，则走 Vite 代理：`/api` 与 `/knowledge-base` → `http://localhost:18080`

## API 路径说明

| 功能 | 前端请求路径 | 后端 |
|------|-------------|------|
| 登录 | `POST /api/auth/login` | AuthController |
| 登出 | `POST /api/auth/logout` | AuthController |
| 当前用户 | `GET /api/user/me` | AuthController |
| 知识库 CRUD | `/knowledge-base` | KnowledgeBaseController（**无** `/api` 前缀） |
| 文档上传/列表/分块 | `/knowledge-base/.../docs...` | KnowledgeDocumentController |

鉴权头：`Authorization: <raw Sa-Token>`（不加 `Bearer`）。

## 功能范围

- 登录页 + `RequireAuth` / `RequireAdmin`（`role === 'admin'`）
- 侧边栏：知识库
- 知识库列表：新建 / 重命名 / 删除
- 文档页：上传文件、开始分块、删除

> 用户管理：后端暂无 User CRUD 接口，本前端未接入。
