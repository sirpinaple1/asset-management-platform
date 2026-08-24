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
├── stores/receipt.ts       # M04 领用/借用单 + 持有关系状态管理
├── stores/transfer.ts       # M05 调拨单状态管理
├── stores/change.ts         # M06 实物信息变更单状态管理
├── composables/            # useListInteractions（列表交互）/ useUndoMessage（撤销提示）
├── utils/tree.ts           # 扁平列表 → 树形结构（分类/位置下拉）
├── router/index.ts         # 路由守卫：接收 token → 清洗 URL → 未登录跳 auth-center
├── utils/token.ts          # token 存取 / URL 参数清洗 / 登录页跳转
├── layouts/DefaultLayout.vue  # 顶栏 + 侧边栏（含基础数据/资产菜单）+ 内容区
└── views/
    ├── dashboard/          # 工作台（鉴权全链路验证页）
    ├── basedata/           # M02 基础数据：公司（只读）/厂商/供应商 CRUD；M08 数据迁移工具页
    ├── asset/              # M03 资产：列表/新增编辑弹窗/详情抽屉（含操作日志）
    ├── receipt/            # M04 领用/借用单：单据流 + 持有中资产退库归还
    ├── transfer/           # M05 调拨单：列表/发起调拨弹窗/详情抽屉（确认/拒绝/撤销）
    └── change/             # M06 实物信息变更单：列表/发起变更弹窗/详情抽屉（变更前后对比/确认执行/撤销）
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
- [x] M04-Frontend: 领用/借用单页面（领用&退库 / 借用&归还 两菜单共用单据流：状态 tabs/搜索/分页/发起申请弹窗含闲置资产选择器/详情抽屉含审批操作/持有中资产退库归还；已对齐后端 29bd8a6 M04 契约——statusLabel/typeLabel 回填、GET /v1/allocations 持有列表、POST /v1/allocations/{id}/return 退库归还、申请 department/reason 必填）
- [x] M05-Frontend: 调拨单（ATR）页面（状态 tabs/搜索/分页/发起调拨弹窗含闲置·在用资产选择器与调入位置树/详情抽屉含调入方确认·拒绝与发起人撤销；已对齐后端 1e0cb87 M05 契约——reject body={reason} 必填、toDepartment/reason 选填且调入位置与部门至少一项、toUserId 调入负责人、rejectReason 拒绝原因回显、发起人自审 403/重复调拨 409 拦截，curl 全链路验证通过；4dc0d23 确认终态语义同步——填负责人→人持有在用/只填部门→部门持有在用/只填区域→回库闲置清归属，弹窗与确认弹窗加终态提示、Allocation.userId 改可空（NULL=部门持有）+持有中视图兜底展示"部门（部门持有）"、type 联合加 TRANSFER，curl 验证部门持有与回库两路径）
- [x] M04-Frontend-适配: 领用区域必填（4a69c36）：申请弹窗加"领用区域"树选必填（审批通过后资产位置更新至此）、列表加领用区域列、详情抽屉展示；curl 验证 400 预检/审批后位置联动（森科设备仓→IT部在用仓+在用）
- [x] M06-Frontend: 实物信息变更单（AOC）页面（状态 tabs/搜索/分页/发起变更弹窗含闲置·在用资产选择器与 new_* 五字段变更表单——使用人+姓名快照/使用部门/区域树/存放位置明细/归属公司，至少填一项/详情抽屉含变更后信息统一目标值区块+变更前·后对比明细与确认执行·撤销；已对齐后端 M06 契约——**提交体 new* 前缀字段+newUserName 快照、状态 PENDING=待确认/CONFIRMED/CANCELLED、明细 value 存展示值（位置/公司名称、使用人姓名）、变更单为信息修正单据确认执行允许发起人自审（区别于 M04/M05 审批流）、撤销仅发起人 403、同资产 PENDING 单重复发起 409、与 M04/M05 单据互斥校验**，curl 全链路 16 项 + 浏览器端到端 9 步验证通过）
- [x] M07-Frontend: 盘点管理页面（状态 tabs/搜索/分页/创建盘点任务弹窗——范围按位置树或分类树二选一快照/详情抽屉含开始·取消·完成·五态明细核对——待盘/相符/位置不符/盘亏/盘盈、条码扫码自动匹配、完成即盘亏结转、盘点报告与触发调拨归位；已对齐后端 M07 契约——**创建范围 locationId/categoryId 至少一项、开始快照范围内资产、扫码 POST /v1/stocktakes/{id}/scan 按条码定位明细、人工确认核对接口、完成自动结转剩余待盘为盘亏、报告统计五态、位置不符项一键生成 INVENTORY_TRIGGERED 调拨单（TransferOrder 加 stocktakeId 字段）**，curl 全链路 + 浏览器端到端（创建→开始→扫码→完成→报告）验证通过）
- [x] M08-Frontend: 数据迁移工具页（基础设置菜单；POST /v1/migration/run 触发——五阶段计数表格 基础数据/资产主数据/领用单ARE/调拨单ATR/操作日志 rows·inserted·updated·skipped、自动创建账号标签区、警告列表滚动区（上限200）、起止时间与耗时统计、执行中秒级计时；单请求 5 分钟长超时（迁移 2500+ 行远超全局 15s）；前端角色预检 asset-资产管理员 + 二次确认弹窗，后端双重门禁 503未启用/403无权限 业务错误由 axios 拦截器统一提示；契约依据 asset-backend migration 包 MigrationController/MigrationResult）
- [ ] M02-Frontend-Tree: 分类/位置树形结构
- [ ] M02-Frontend-Model: 型号管理（关联分类+厂商）
