# asset-frontend

资产管理系统前端（Vue3 + Vite + TypeScript + Element Plus + Pinia）

不含登录页：登录由 auth-center-frontend 完成后跳转 `?token=xxx` 过来，
本工程接收 token（URL query → localStorage）并以 `Authorization: Bearer` 访问 asset-backend。

## 快速开始

```bash
cp .env.development.example .env.development   # 按需修改后端/鉴权中心地址
npm install
npm run dev        # http://localhost:5173（asset-backend CORS 白名单已含此端口）
```

## 脚本

- `npm run dev` 开发服务器（/api 代理到 asset-backend:6006）
- `npm run build` 类型检查 + 生产构建
- `npm run type-check` 仅类型检查

## 结构

```
src/
├── api/
│   ├── config/request.ts   # axios 实例：Bearer 头 + 401/403/503 拦截
│   ├── interface/          # Result / MeInfo / basedata 等契约类型
│   └── modules/            # 各模块请求函数（user.ts、basedata.ts）
├── stores/user.ts          # useUserStore：token 登录态 + /me 信息
├── stores/basedata.ts      # M02 基础数据（公司/厂商/供应商等）状态管理
├── stores/asset.ts         # M03 资产列表（服务端分页/筛选）状态管理
├── composables/            # useListInteractions（列表交互）/ useUndoMessage（撤销提示）
├── utils/tree.ts           # 扁平列表 → 树形结构（分类/位置下拉）
├── router/index.ts         # 路由守卫：接收 token → 清洗 URL → 未登录跳 auth-center
├── utils/token.ts          # token 存取 / URL 参数清洗 / 登录页跳转
├── layouts/DefaultLayout.vue  # 顶栏 + 侧边栏（含基础数据/资产菜单）+ 内容区
└── views/
    ├── dashboard/          # 工作台（鉴权全链路验证页）
    ├── basedata/           # M02 基础数据：公司（只读）/厂商/供应商 CRUD
    └── asset/              # M03 资产：列表/新增编辑弹窗/详情抽屉（含操作日志）
```

## 鉴权链路（ADR-0004）

auth-center-frontend 登录 → 跳 `localhost:5173?token=xxx&name=xxx&systemcode=asset`
→ 路由守卫存 localStorage 并清洗 URL → axios 携带 `Authorization: Bearer <token>`
→ asset-backend(6006) TokenAuthFilter → comm_public_basic 校验。
token 失效（HTTP 401）自动清除并跳回 auth-center 登录页（带 returnUrl）。

## 开发进度

- [x] M-FE01: 前端骨架（鉴权验证、工作台）
- [x] M02-Frontend: 基础数据 CRUD（公司/厂商/供应商，验收清单见 `docs/M02-前端验收清单.md`）
- [x] M02.5-Frontend: 厂商/供应商切换服务端分页契约（tabs/搜索/分页）
- [x] M03-Frontend: 资产主表（列表筛选分页/新增编辑弹窗/详情抽屉含操作日志/报废/导出，验收清单见 `docs/M03-前端验收清单.md`）
- [ ] M02-Frontend-Tree: 分类/位置树形结构
- [ ] M02-Frontend-Model: 型号管理（关联分类+厂商）
