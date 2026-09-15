# M-FE01 — 前端骨架

## 职责

搭建业务前端工程：Vue3 + Vite + TypeScript + Element Plus + Pinia，
不含登录页，含 token 接收/刷新/请求拦截器，接通 comm_public_basic，
验证能拿到当前用户信息后进入业务页面。

## 工程要求

- 脚手架：`asset-frontend/`，`npm create vue@latest` 或手动配置 Vite
- 依赖：Vue 3.5、Vite、TypeScript、Element Plus、Pinia、Axios、Vue Router 4
- 不含登录页：登录由 auth-center-frontend 完成后跳转过来

## token 接收方式（待 auth-center-frontend 确认后校准）

暂按方式 1（URL query 传 token）实现：

```
auth-center-frontend 登录 → 跳转 /asset-frontend?token=xxx
前端路由守卫：读取 query.token → 存 localStorage → 清除 URL 中的 token 参数
后续请求：axios 拦截器自动附加 Authorization: Bearer <token>
```

如 clone auth-center-frontend 后确认是 localStorage 直接共享或 postMessage，按实际调整。

## 鉴权验证策略：懒验证（有意为之的设计选择，非缺陷）

路由守卫**只校验本地 token 存在性，不预验有效性**（不在守卫里调 `/api/v1/me`）。
过期 token 会放行进入页面，由该页面首个 API 调用触发 401，axios 响应拦截器统一
清 token 并跳回 auth-center 登录页。选择理由与配套约束：

- **理由**：守卫预验要在每次导航前多一次串行 HTTP 往返（asset-backend 侧还有 30s
  回源缓存，预验结果本身滞后），换来的只是"报错提前一拍"；懒验证以首次 401 兜底，
  换取零额外延迟与全链路最简。
- **配套约束（对全部业务页面强制）**：每个业务页面挂载时必须至少发起一次 API 调用
  （业务数据或 `/me`），依赖 401 兜底完成过期驱逐；无任何请求的纯静态页面视为违反约定
  （错误页/NotFound 除外）。
- **401 防抖标志 `redirectingToLogin`（request.ts）在路由守卫 `beforeEach` 中每次导航重置**：
  防止整页跳转被中断后标志卡死，导致二次 token 过期时无法再次跳转登录。

## 目录结构

```
asset-frontend/
├── src/
│   ├── api/          # axios 实例 + 各模块请求函数
│   ├── stores/       # Pinia stores（useUserStore / useAssetStore 等）
│   ├── router/       # Vue Router，路由守卫（未登录→重定向 auth-center）
│   ├── views/        # 页面（按模块分目录）
│   ├── components/   # 公共组件
│   ├── hooks/        # 可复用组合式函数
│   └── utils/        # token 工具、格式化等
├── .env.development  # 后端地址（不入库）
└── vite.config.ts    # 代理配置
```

## 必须跑通的验证页面

路由 `/dashboard`：调 `GET /api/v1/me`，展示当前用户姓名 + 公司 + 部门，
能看到这个页面说明鉴权全链路（auth-center → asset-frontend → asset-backend → comm_public_basic）
打通。

## 阻塞项

**必须先 clone auth-center-frontend，确认 token 传递方式后再写路由守卫。**
（见 STATUS.md 阻塞项 1）

## 依赖

- M01（后端骨架 + `/api/v1/me` 接口）
- auth-center-frontend 确认 token 传递方式

## 完成标准

```
npm run dev 启动无报错
从 auth-center-frontend 跳转后，/dashboard 正确展示用户信息
刷新页面后 token 从 localStorage 恢复，不需要重新登录
无效 token 时跳转回 auth-center-frontend 登录页
```

## 单次 AI 会话切分建议

**会话 FE01**（单会话）：建工程 + 路由守卫 + axios 拦截器 + useUserStore + /dashboard 页面

可与 M03/M04 后端会话并行，联调在前后端各自完成后做一次。

## 接手前必读

`AGENTS.md` → `STATUS.md`（确认阻塞项 1 是否已解决）→ `docs/adr/0004-鉴权复用...md`
