# STATUS.md — asset-frontend 进度看板

> 阶段性进展完成后更新本文件。最后更新时间见文末。
>
> 开发分支 `feat/m02-basedata`（worktree `.worktrees/feat-m02-basedata`，dev 端口 **5173**，主仓 5174）。

## 当前阶段

**V20260832 规范对齐收尾**：
- 分类管理页（两级树）+ 位置管理页（树形）+ 资产"细则"（spec）字段已完成，工作区待提交（vue-tsc 零错误）
- 后端契约已核验对齐（2026-08-28）：spec 全链贯通（Req/Resp/导出"细则"列）、分类 CRUD 护栏（同级重名/两级上限/删除保护均 400 带原因）、位置删除保护 409→400、编码前缀父链继承
- 审批中心 F3/F4（定向派单/通知铃铛/B3 概览卡）及四个修复批次已推送
- 待办：浏览器 E2E 联调 V20260832（分类/位置增删改、资产细则保存回显）→ 用户确认后提交推送

## 已完成（按里程碑）

- [x] **M-FE01 前端骨架**（2026-08-19）：Vue 3.5 + Vite + TS + Element Plus + Pinia + Axios；token 从 URL query 接收存 localStorage + 路由守卫清洗 URL；401 防抖跳回 auth-center；`/dashboard` 调 `/api/v1/me` 全链路打通
- [x] **M-FE02 基础数据页**（08-20~22）：公司只读；厂商/供应商 CRUD（服务端分页/状态 tabs/keyword 防抖搜索/乐观删除+restore 撤销）；导航层多页签工作台（path 键控 keep-alive、脏页签关闭确认、localStorage 持久化、面包屑、滚动恢复）；列表交互骨架（右键菜单/双击编辑/方向键导航/Ctrl+F·Delete/Ctrl+S 快捷键/列宽拖拽/深链 query 同步与 `?id=` 定位）
- [x] **M-FE03 资产主表**（08-22）：编码自动生成契约（新增不录 barcode 仅提示、编辑保留改码、保存后回显生成码）；导出 Excel blob 按当前筛选全量；详情抽屉（全字段 + 操作日志时间线 + 报废）
- [x] **M-FE04~06 单据页**（08-21~22）：领用&退库/借用&归还两菜单共用单据流；调拨 reject 传 `{reason}` 必填 + "调入位置与调入部门至少一项"联动校验；变更单 new* 前缀提交体 + 发起人自审（信息修正区别于审批流）；领用区域必填（审批后资产位置联动依据）；调拨终态二选一提示（人持有/部门持有/回库）
- [x] **M-FE07 盘点**（08-24）：五态明细核对 + 条码扫码自动匹配 + 人工确认 + 位置不符一键生成 INVENTORY_TRIGGERED 调拨
- [x] **M08 前端三件套**（08-24，daefef9/5273c98 已推）：
  - 迁移工具页：五阶段计数报告 / 单请求 5 分钟长超时 / 角色预检 + 二次确认
  - 审批中心三 tab 聚合：四类单据 allSettled 并行拉取、**共享池语义本地过滤**（PENDING 且非发起人 = 待我处理，零后端改动）、`?id=` 深链开详情抽屉
  - 工作台改造：待办卡三计数与审批中心联动、发起流程 popover 五入口 `?compose=1` 深链自动弹发起弹窗、最近使用足迹 localStorage（去重置顶上限 8 条）、进行中盘点提示条
- [x] **F3/F4 审批中心适配**（08-28，78de81f/e82c6b1 已推）：定向派单 assignee——UserSelector 远程搜索分页选人器（领用/调拨/变更三申请弹窗共用，数据源 `GET /v1/users`）；审批中心待办分区（directed 定向 / pool 共享池）；通知铃铛（未读数轮询 + jumpPath 五类单据列表页映射）；工作台 B3 `/stats/overview` 数据概览卡（响应式布局 + 失败重试）
- [x] **F3/F4 修复批次**（均已推）：
  - `@/utils/request` 路径不存在 → 统一 `import { request } from '@/api/config/request'`（1ebad79）
  - todoBucketOf 枚举统一 directed/pool + SVG 饼图多余全圆层（0501e7f）
  - "我的持有"未按登录用户过滤 → `?me=hold` 深链 + buildQuery 带 userId（8d80644）
  - UserSelector remote-method 误用 `@remote-method` 事件写法 → `:remote-method` prop 绑定（22dd6b2）
- [x] **分类管理页 + 位置管理页 + 资产"细则"**（08-28，工作区待提交）：
  - CategoryList/LocationList：树形表格（buildTree 组树、本地名称搜索——接口无 keyword，命中节点连同祖先保留）、增删改后重拉 basedataStore 保持全局下拉同步、双击行编辑
  - CategoryModal：父分类仅一级可选（对齐后端两级上限）；同级重名后端 400 由拦截器展示
  - LocationModal：父位置 el-tree-select（不限层级），编辑时剔除自身及子孙防环
  - 资产 spec 全链：AssetModal 细则输入（maxlength 500 对齐后端 varchar(500)、占位"如：16G内存/512G固态"）、列表列、详情抽屉；AssetQuery 不支持 spec 搜索仅展示；导出"细则"列由后端提供前端零改动

## 工程约定与决策点

| 约定 | 说明 |
|---|---|
| API 导入 | 一律具名导出 `import { request } from '@/api/config/request'`（`@/utils/request` 不存在，已踩坑一次） |
| remote-method | Element Plus 按 prop 绑定 `:remote-method`，事件写法 `@remote-method` 不会执行（已踩坑一次） |
| el-tree-select | `check-strictly`（大类/子类均可选中）；树数据由 `buildTree` 从扁平列表组树（悬挂节点按根处理） |
| keep-alive | 缓存键 = 路由 name，组件 `defineOptions({ name })` 必须对齐 |
| 错误提示 | 后端 400 业务错误由 axios 拦截器统一 ElMessage 展示，页面不二次处理 |
| 提交载荷 | 文本 trim、空值归一 undefined（不发空串）；可编辑 code/path 类字段由后端生成，前端契约不提交 |
| 提交门禁 | `npx vue-tsc --noEmit` 零错误（约 10s）；改动先落工作区，经用户确认后 commit 推 `feat/m02-basedata` |
| 深链同步 | 列表筛选/搜索词/页码同步 URL query（replace 不产生历史），`?id=` 定位编辑/详情、`?compose=1` 弹发起弹窗 |

## 本地联调环境

- 前端 dev：worktree `.worktrees/feat-m02-basedata`，端口 **5173**；asset-backend **6006**，接口前缀 `/api/v1`
- auth 链路：comm_public_basic **6002**（登录 `POST /login/check`，RSA 加密）；auth-center-frontend 8321
- 联调账号：`assetfe / Asset@2026`（userId=787，角色 asset-资产管理员）；备选 `SK9802`
- ⚠️ 403"尚未分配 asset 系统角色"复发性坑：密码被重置回默认 123456 会触发 comm_public_basic 默认密码拦截 → 直写本地库 bcrypt 修复

---

**最后更新**：2026-08-28（分类/位置管理页 + 资产细则完成待提交；后端 V20260832 契约核验对齐；补录 M-FE02~07 / M08 三件套 / F3/F4 及修复批次决策点）
