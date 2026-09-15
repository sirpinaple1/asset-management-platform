# AGENTS.md — asset-frontend 项目必读（AI 开工前先读完本文）

> 本仓为示例科技资产管理系统前端（Vue 3.5 + Vite + TS + Element Plus + Pinia）。开发直接在 `main` 分支进行，完成后 push `origin/main`（不绕 feat 分支）。

## 工程红线

- **版本号纪律（每次 push 必做）**：前端版本唯一来源是 `src/version.ts`（FRONTEND\_VERSION / RELEASE\_DATE / CHANGELOG）。每次 push 功能/修复上线，必须：FRONTEND\_VERSION 末位 +1、更新 RELEASE\_DATE、CHANGELOG 顶部增补一行。前后端版本独立演进，互不同步。目的：用户从左下角帮助面板即可确认新功能是否已上线。

## 关键约定（速查）

| 项             | 约定                                                                                            |
| ------------- | --------------------------------------------------------------------------------------------- |
| API 调用        | 一律具名导出 `import { request } from '@/api/config/request'`（`@/utils/request` 不存在）；接口前缀 `/api/v1` |
| 静默请求          | `request` 配置可加 `skipErrorToast: true`，失败不弹全局错误提示（版本探测等非关键请求用）                                 |
| 提交门禁          | `npx vue-tsc --noEmit` 零错误；不提交 `.env` 类本地配置与 `package-lock.json` 漂移                           |
| remote-method | Element Plus 按 prop 绑定 `:remote-method`，事件写法不生效（已踩坑）                                          |
| 错误提示          | 后端 400 业务错误由 axios 拦截器统一 ElMessage 展示，页面不二次处理                                                 |

