# STATUS.md — 项目进度看板

> 任何阶段性进展完成后必须更新本文件。最后更新时间见文末。
>
> **修订记录**：2026-08-18 经 REVIEW-001 审阅后修订，架构降级为单模块三层 + 鉴权复用 comm_public_basic。

## 当前阶段

**Phase 4 收尾 + 规范对齐**：
- 后端：B1/B2/B3（定向待办/通知中心/统计聚合）+ M07 盘点 + CI 自动部署均已落地；分类两级结构重构 + 资产"细则"字段（V20260832）代码完成（工作区待提交，测试 400/400）
- 前端：feat/m02-basedata 已上线分类管理/位置管理页 + 资产"细则"字段（vue-tsc 零错误），后端契约已对齐（2026-08-28 第二批）；审批中心前端 F3/F4 适配仍待做
- CI/CD：任意分支 push → verify（编译+全量单测）→ 自动部署演练沙箱 193.112.174.178 + Pipeline 页手动一键回滚
- 待执行：M08 历史数据迁移（POST /api/v1/migration/run）；V20260832 等迁移脚本需在测试库 172.16.5.247 手动执行

## 已完成

- [x] 创建项目目录 `asset-management/`
- [x] 开源调研：Snipe-IT、Shelf.nu、西柚 Ciyo、chyinan/Asset-Management-System、若依固定资产
- [x] 候选项目 clone 到 `references/`（西柚 GPL-3.0 仅参考业务模型；chyinan 仅参考工程实践）
- [x] 调研 comm_public_basic 源码：Redis Token（非 JWT）+ 多系统接入（PortalProject）+ 钉钉登录已就绪
- [x] 调研 Ciyo ciyo-asset 源码：若依风格传统三层（非 DDD），15+ 业务实体
- [x] 方案可实施性审阅：[docs/REVIEW-001](./docs/REVIEW-001-方案可实施性审阅.md)（识别 5 处偏差，给出调整建议）
- [x] 架构决策修订：
  - [ADR-0004](./docs/adr/0004-鉴权复用comm_public_basic与SpringBoot3解耦.md)：鉴权复用 comm_public_basic + Spring Boot 3.x + HTTP 解耦 + 钉钉
  - [ADR-0005](./docs/adr/0005-架构降级为单模块三层.md)：单模块三层（Superseded ADR-0002 的 DDD 6 模块）
- [x] 工程规范修订：[docs/ENGINEERING.md](./docs/ENGINEERING.md)（新增 R8 鉴权红线，P1-P4 适配单模块三层）
- [x] AGENTS.md 修订（结构 + 鉴权接入要点）
- [x] **M01-A 后端骨架**（2026-08-19）：
  - `asset-backend/` 单 Maven 模块：Spring Boot 3.5.16 + JDK 17 + MyBatis Plus 3.5.17 + Flyway + Spring Security（permitAll 占位，M01-B 替换）
  - 包骨架 `com.sk.asset`（config/common/auth/controller/service/mapper/entity/dto/enums，package-info 注明边界，上下文子包随 M02+ 落地）
  - Flyway `V20260819__create_core_tables.sql` 一次建齐 19 张核心表（逻辑外键 + utf8mb4，字段设计依据 ADR-0006 + 模块文档 + M08 迁移规则）
  - API 文档：SpringDoc 2.8.17 端点 + knife4j-openapi3-ui 静态 webjar（/doc.html）
  - 验证：`mvn clean compile` 通过；本地启动 Flyway 成功落 19 表；doc.html / v3/api-docs 200
- [x] **M01-B 前置处理**（2026-08-19，AuthPort/TokenFilter 之外的三项安全基建）：
  - OpenApiConfig 注册 BearerAuth SecurityScheme（HTTP bearer，非 JWT）+ 全局 security 引用 → /doc.html 出现 Authorize 按钮，M01-B 后可直接手动测受保护接口
  - SecurityConfig 增加 CORS：`CorsConfigurationSource` bean（origin 白名单 `app.cors.allowed-origins`，默认 `http://localhost:5173` 即 M-FE01 Vite 开发端口）+ 链上 `.cors(withDefaults())`；allowCredentials=true 显式 origin
  - permitAll 占位链加 `@Profile("local")`：非 local 环境回退 Spring Boot 默认安全配置（全部请求需认证，fail-closed）
  - 验证：白名单 origin 预检 200 + 正确回显 Allow-Origin/Credentials；非白名单 origin 预检 403；prod profile 无认证访问 /doc.html、/v3/api-docs 均 401
- [x] **M01-B 前置阻塞项解除**（2026-08-19，grillme 会话核实）：
  - **comm_public_basic 接口契约核实完成**（origin/main 与本地私改版逐项比对，契约层一致）：响应统一 `{code, message, data}`，code=200 成功；token 一律 `Authorization: Bearer` 头；`/login/userSystemAuth` 需额外携带请求头 `systemCode: asset`；**无效/缺失 token 时三接口（checkToken/getAuth/userSystemAuth）均经 TokenException 全局处理返回 HTTP 401 + {code:401}**（ExceptionConfig:148，实测确认）
  - **auth-center-frontend token 传递方式确认**：登录成功后跳转子系统时 token 通过 **URL query 参数**传递；子系统前端取参后以 `Authorization: Bearer` 头访问后端 → M-FE01 落地接收拦截器
- [x] **M01-B 鉴权接入**（2026-08-19）：
  - `auth/` 包：`AuthPort` 接口 + `CommPublicBasicAuthPort`（RestClient 三连回源 checkToken→getAuth→userSystemAuth + Caffeine 30s 短缓存仅存成功结果 + 401/403/503 语义映射）、`TokenAuthFilter`（Bearer 提取 → SecurityContext + UserContext 注入，finally 双清理）、`AuthContext`/`UserContext`
  - `SecurityConfig`：STATELESS + TokenAuthFilter 替换 permitAll 占位；文档端点 fail-closed（仅 local `app.security.docs-permit-all=true` 放行）；CORS 复用前置处理
  - `GET /api/v1/me` 验证接口（MeResp：用户信息 + asset 角色 + 按钮权限码）
  - 配置 `app.auth.*`（base-url 不入库，application-local.yml + .example 模板）
  - 本地 comm_public_basic 库 `basic_portal_project` 注册 asset 项目（id=22，web_url=localhost:5173，**is_visible=0 暂隐藏**，M-FE01 前端就绪后置 1）
  - **契约修复**：comm_public_basic 无效 token 返回 HTTP 401，最初实现误按"服务不可用 503"处理 → 新增 `translateResponseError` 映射（401→401"token 无效或已过期"，其余→503 fail-closed）
  - 测试：**单测 19 个全过**（AuthPort 11 / TokenFilter 6 / MeController 2），`mvn clean test` BUILD SUCCESS
  - **全链路实测**（本地 comm_public_basic:6002 + asset-backend:6006）：admin 登录取 token（密码 RSA 公钥加密）→ 无 token 401 / 伪造 token 401（修复后语义正确）/ 有效 token + 空 asset 角色 403"尚未分配 asset 系统角色"（admin 为默认密码用户，被 comm_public_basic"默认密码用户不得进入业务系统"策略拦截，判定语义正确）。**200 成功路径由单测覆盖**（有效token_三接口全通过_返回上下文并写缓存）；live 200 需非默认密码 + 有角色的用户，待 M-FE01 联调或正式用户就绪时补验

- [x] **M-FE01 前端骨架**（2026-08-19）：
  - `asset-frontend/`：Vue 3.5 + Vite + TypeScript + Element Plus + Pinia + Axios + Vue Router 4
  - token 接收：路由守卫 `beforeEach` 从 URL query 取 token → 存 localStorage → `stripAuthParamsFromUrl` 清洗 URL（history 路由）
  - axios 拦截器：请求自动附加 `Authorization: Bearer` + `systemCode: asset`；响应 401 清 token + 跳回 auth-center 登录页（防抖 `redirectingToLogin`）
  - `/dashboard` 工作台页：调 `GET /api/v1/me` 展示用户姓名/部门/岗位/角色，全链路打通（auth-center → asset-frontend → asset-backend → comm_public_basic）
  - **全链路实测**（assetfe / SK9802 两账号）：auth-center 登录 → 点击 asset 卡片 → 跳转 asset-frontend → 工作台正常渲染用户信息
  - **redirectingToLogin 防抖修复**：`resetLoginRedirectFlag()` 导出 + 路由守卫 `beforeEach` 每次导航重置，防止整页跳转被中断后标志卡死、二次 401 无法再跳登录
  - **懒验证设计落档**：路由守卫只校验 token 存在性不预验有效性（有意为之），过期 token 由页面首个 API 401 兜底驱逐；约定见 M-FE01 spec"鉴权验证策略"节 + ENGINEERING.md P5
- [x] **asset 项目注册 comm_public_basic**（2026-08-19）：
  - `basic_portal_project` 注册 asset（id=22，project_en=asset，web_url=localhost:5173）
  - **is_visible=0, is_enabled=0**（comm_public_basic 约定：**0=显示/启用，1=隐藏/禁用**，与前端直觉相反——应用中心按 0/0 过滤展示）
  - 联调账号：assetfe（`Asset@2026`）/ SK9802（`Sk9802@2026`），均已分配 role_id=350 asset-资产管理员

- [x] **M02 基础数据模块**（2026-08-20）：
  - Company Entity/Mapper/Service/Controller（只读接口：GET list + GET detail）
  - Manufacturer 完整 CRUD（Entity/Mapper/Service/Controller/DTO）
  - Supplier 完整 CRUD（Entity/Mapper/Service/Controller/DTO）
  - AssetCategory 树形结构（Entity/Mapper/Service/Controller/DTO，扁平列表）
  - AssetLocation 树形结构（Entity/Mapper/Service/Controller/DTO，materialized path，?parentId 子树查询）
  - DepreciationRule 骨架（Entity/Mapper/空 Service，Phase 4 实现）
  - AssetModel 关联查询（Entity/Mapper/Service/Controller/DTO，含关联对象名称，?categoryId 筛选）
  - 种子数据脚本 V20260820（company/asset_category/asset_location 初始数据）
  - 测试覆盖：**71 个测试全部通过**（Service 层单元测试 24 个 + Controller 层集成测试 28 个 + M01 鉴权测试 19 个），0 失败
  - API 端点：GET/POST/PUT/DELETE /api/v1/manufacturers、/api/v1/suppliers、/api/v1/locations、/api/v1/models；GET /api/v1/categories（只读，d40a2b5 移除写端点）；GET /api/v1/companies（只读）
  - **待跟进**：V20260820 种子数据脚本已写入 src/main/resources/db/migration/，按项目约定需在测试库 172.16.5.247 上手动执行
  - **代码质量审查**（2026-08-20，REVIEW-M02，实测 `mvn clean test` 71/71 通过）：
    - **通过项**：分层严格遵守 R1（entity 经 toEntity/updateEntity/from DTO 三件套转换不外泄）；命名符合 N1（XxxReq/XxxResp/构造器注入版 Controller）；契约符合 P3（统一 Result + /api/v1/ + Swagger 注解齐全）；参数化查询无 SQL 注入；@TableLogic 逻辑删除 + 自动填充统一；测试 mock 边界正确（service mock mapper、controller mock service）
    - **P1 待修（M03 前处理）**：① 注入方式不统一——Location/AssetModel 的 Controller+Service 已改构造器注入（b114b52），但 Manufacturer/Supplier/Category/Company 四组仍是 @Autowired 字段注入，且对应 4 个 Controller 测试被迫用反射注入 mock，应统一为构造器注入；② Location 树完整性无服务端保障——LocationReq.path 由客户端任意指定（materialized path 应服务端按 parentId 派生）、parentId 无存在性校验（可插悬挂节点）、移动节点不重算子树 path
    - **P2 待修（M03 时一并处理）**：③ 删除无引用完整性检查（manufacturer/supplier/location/model 被删除时不检查是否被 asset_model/asset 引用，M03 有数据后将产生悬挂引用）；④ AssetModelReq 外键 ID（categoryId/manufacturerId/depreciationId/companyId）无存在性校验；⑤ AssetModel 详情接口不带关联名称（列表 JOIN 带、详情不带，不一致）
    - **P3 minor**：⑥ Manufacturer/Supplier/Category Controller 测试未覆盖 404 分支（Category 仅 1 个测试，getById 未测）；⑦ email 字段缺 @Email 格式校验；⑧ CompanyServiceImpl.list() 缺排序（其余 list 均有 orderBy）；⑨ Service 单测多为转发验证型，M03 起复杂业务应测业务规则

- [x] **DevOps 基建：远端 + CI + 健康端点**（2026-08-20）：
  - 公司 GitLab 远端建立：`gitlab.tritree.cn/sirpinaple/asset-backend`（私有，push-to-create）；本机 `~/.ssh/config` 映射 `id_rsa_gitlab`，push 直连免密
  - GitLab CI（`.gitlab-ci.yml`）：push 触发 `mvn clean verify`（纯单测无 DB 依赖），Maven 仓库跨流水线缓存；**共享 runner 是否接单待网页确认**（pipeline 页 pending=无 runner 需找管理员或自注册）
  - Actuator 健康端点：只暴露 `/actuator/health`（含 liveness/readiness 探针，容器健康检查就绪），其余 actuator 端点 fail-closed
  - **修复存量 bug**：docs-permit-all 失效——TokenAuthFilter 的 401 先于授权层发生，local 下 `/doc.html` 实测无法免 token 访问（401）。引入公共路径旁路机制：`shouldNotFilter` 旁路 + 授权层 permitAll 由 SecurityConfig 同一份 publicPaths 派生，永不脱节
  - 测试 75/75（TokenAuthFilterTest 新增 4 个旁路用例：健康端点/子端点/业务路径不旁路/文档端点旁路）

- [x] **M02.5 原型对齐 + REVIEW-M02 修复**（2026-08-22，前端原型 asset-system-preview.html 对齐）：
  - 迁移脚本 `V20260822__align_prototype_adjustments.sql`：manufacturer/supplier 加 status（1-启用 0-停用）；receive_receipt/asset_allocation 加 type（RECEIVE-领用 BORROW-借用，M04 共用单据流）
  - 厂商/供应商：分页（`PageResp` 统一契约 + MybatisPlusConfig 分页拦截器 + mybatis-plus-jsqlparser 依赖）+ keyword 模糊 + status 筛选 + 批量删除（引用检查任一被引用整体 409 拒绝）+ EasyExcel 导出端点（`/export`）
  - REVIEW-M02 P1①：Manufacturer/Supplier/Category/Company 全部统一构造器注入（测试去反射）
  - REVIEW-M02 P1②：Location path 服务端按 parentId 派生（物化路径不可客户端指定）+ 父节点存在性校验
  - REVIEW-M02 P2④⑤：AssetModel 外键存在性校验（category/manufacturer/depreciation/company）+ 详情 JOIN 关联名称（与列表一致）
  - GlobalExceptionHandler 补 `MethodArgumentNotValidException`→400（此前参数校验失败落入兜底 500）与 `IllegalArgumentException`→400（非法枚举参数）
  - 测试 111/111（厂商/供应商分页+导出+批量删除+409 引用链路全覆盖）

- [x] **M03 资产主表 CRUD + 状态机**（2026-08-22）：
  - `enums/asset/AssetStatus`：IDLE/IN_USE/PENDING_CONFIRM/DISCARD + `canTransitionTo` 状态机（DISCARD 为终态；非法流转 409）
  - Asset/AssetLog Entity + AssetMapper（详情 JOIN 六关联名称）/AssetLogMapper
  - `AssetServiceImpl`：save（barcode 唯一 409 + 六外键存在性 400 + 状态强制 IDLE + 写"新增"日志）、updateById（状态不随编辑变更）、`changeStatus(assetId, newStatus, operatorId, note)`（M04/M05 统一流转入口，写"【状态】由【旧值】变更为【新值】"格式日志）、discard、page/listBy（7 维筛选 + 批量名称回填）、listLogs
  - `AssetController`：GET 分页 / GET /export / GET 详情 / POST 新增 / PUT 编辑 / POST /{id}/discard / GET /{id}/logs（状态无编辑端点，只能业务流转）
  - DTO：AssetReq/AssetResp（含 statusLabel）/AssetQuery/AssetLogResp/AssetExportRow/AssetDiscardReq
  - 测试 147/147（AssetStatusTest 状态机全路径 7 + AssetServiceImplTest 15 + AssetControllerTest 13）；`POST /api/v1/assets/import` 批量导入留待 M08 历史迁移一并实现

- [x] **M04 领用/借用单（ARE/BOR）审批流**（2026-08-21，前端契约对齐）：
  - 迁移 `V20260823__receipt_user_name_snapshot.sql`：receive_receipt 加 applicant_name/approver_name、asset_allocation 加 user_name（ADR-0004 asset 库不存用户主数据 → 姓名业务时点快照，对齐 department/operator_label 快照模式）
  - `enums/receipt`：ReceiptType（RECEIVE/BORROW + 单号前缀 ARE/BOR）/ ReceiptStatus（PENDING/APPROVED/REJECTED）
  - `entity/mapper/service/controller/dto` 的 receipt 上下文：ReceiveReceipt（主表）+ ReceiveReceiptItem（明细，回填资产编码/名称/序列号）+ AssetAllocation（持有关系）
  - `ReceiveReceiptServiceImpl`：create（资产存在性 404 + 待审批占用 409 领用借用互斥 + serial_no 锁定读取号 + 明细写入 + 资产→PENDING_CONFIRM）、approve（PENDING 校验 409 + 审批人≠申请人 403 + 资产→IN_USE + asset 持有人更新 + asset_allocation 写入）、reject（资产→IDLE + approveRemark 记录原因）、list/getById（明细批量回填）；全部 @Transactional，任一资产流转失败整体回滚
  - serial_no 生成：`likeRight + orderByDesc + LIMIT 1 FOR UPDATE` 锁定读串行取号（当天按前缀分别自增），uk_receipt_serial_no 兜底
  - `AllocationServiceImpl`：list（assetId/userId/type/active 筛选 + 资产名称回填，为前端"退库/归还"提供 allocation id 查询渠道）+ returnAllocation（持有关系闭环 + 资产→IDLE + 持有人仍为本记录持有人时清空）
  - `AssetService.changeStatus` 增加显式操作类型重载（M04 日志区分「领用/借用/归还」；原签名行为不变）
  - 端点：POST/GET /api/v1/receipts、GET /api/v1/receipts/{id}、POST /{id}/approve、POST /{id}/reject、GET /api/v1/allocations、POST /api/v1/allocations/{id}/return
  - 测试 185/185（新增 38：ReceiveReceiptServiceImplTest 16 + AllocationServiceImplTest 4 + ReceiveReceiptControllerTest 13 + AllocationControllerTest 5）；前端 M04 页面（worktree stash）可按此契约联调

- [x] **M03 补充：资产编码改为系统自动生成**（2026-08-21，用户验收反馈）：
  - 迁移 `V20260824__asset_barcode_auto_generate.sql`：asset_category 加 barcode_prefix（种子对齐旧系统前缀：SKSCDM 镀膜/SKSCFZ 辅助/SKSCJC 检测/SKBGDN IT数码，见 M08 迁移文档）
  - 生成规则：`{分类前缀}-{yyyyMMdd}-{4位序号}`（如 SKSCDM-20260821-0001）；前缀缺省回退 SK；取号复用 ARE/BOR 单号模式（likeRight + orderByDesc + LIMIT 1 FOR UPDATE，uk_asset_barcode 兜底）
  - 日期段与旧系统编码（前缀-序号）命名空间隔离 → M08 历史 563 条 upsert 不冲突
  - API 契约：POST /assets 传 barcode 即忽略（兼容旧前端）；PUT 不传 barcode 保持原编码、传则改码（唯一校验照旧）
  - 前端待适配：新增弹窗隐藏资产编码输入框，保存后从响应取生成编码展示

- [x] **修复：在用报废悬死持有关系**（2026-08-21，用户验收反馈 SFBGIT2528 场景）：
  - 根因：IN_USE→DISCARD 合法但 asset_allocation 无人闭环；退库因 DISCARD→IDLE 非法被 409 卡死，领用&退库页永远显示"持有中"（Ciyo 参考系统同样缺陷且更糟：退库会静默覆盖报废状态）
  - 修复：changeStatus 落 DISCARD 联动闭环持有中 allocation（returned_at=now + note 记报废原因）+ 清空资产持有人 + 报废日志追加原持有人；PENDING_CONFIRM→DISCARD 本就被状态机阻止，待审批单据不受影响
  - 测试 190/190（新增 3：在用报废闭环回归 + 闲置报废跳过 + InUse 无持有更新计数）

- [x] **M05 调拨单（ATR）审批流**（2026-08-21）：
  - 迁移 `V20260825__transfer_order_name_snapshot.sql`：transfer_order 加姓名快照列（applicant_name/from_user_name/to_user_name/confirmer_name，对齐 V20260823 M04 快照模式）+ reject_reason（拒绝原因）；asset_allocation.type 枚举扩至 TRANSFER（已本地库实测应用）
  - `enums/transfer`：TransferStatus（PENDING/COMPLETED/CANCELLED/REJECTED）+ TransferSource（MANUAL/INVENTORY_TRIGGERED，M07 盘点触发预留）
  - `entity/mapper/service/controller/dto` 的 transfer 上下文：TransferOrder（主表）+ TransferOrderItem（明细，回填资产编码/名称/序列号 + 调出/调入位置名称）
  - `TransferOrderServiceImpl`：create（调入区域/部门至少一项 400 + 资产存在 404 + 报废资产 409 + 待确认调拨占用 409 + 待审批领用/借用占用 409（与 M04 互斥）+ 调入位置存在性 400 + ATR 单号锁定读串行取号；**调拨不锁定资产状态**，调出位置取首台资产当前位置）、confirm（调入方确认：资产 location/user_id/user_department 更新 + 旧持有关系闭环 + 新建调入方持有记录 type=TRANSFER + 写"调拨"日志（【位置】/【部门】/【使用人】变更明细，不改状态）+ 待确认期间资产报废 409 拦截；确认人≠发起人 403）、reject（资产不变，记录拒绝原因）、cancel（仅发起人可撤 403，资产不变）；全部 @Transactional
  - `AssetService` 新增 `writeLog`（不改状态写日志，M06 实物信息变更复用）
  - M04 反向占用校验：领用/借用 create 增加待确认调拨单占用检查（双向互斥，防止两单据同时操作同一资产）
  - 端点：POST/GET /api/v1/transfers、GET /api/v1/transfers/{id}、POST /{id}/confirm、POST /{id}/reject、POST /{id}/cancel（列表支持 status/source/userId/dept/date 筛选）
  - 测试 229/229（新增 39：TransferOrderServiceImplTest 22 + TransferOrderControllerTest 14 + M04 反向占用 1 + writeLog 2）；前端 M05 调拨页面（菜单已预留 comingSoon）可按此契约开发
  - **待跟进**：V20260825/V20260828 需在测试库 172.16.5.247 手动执行；前端持有关系列表（领用&退库/借用&归还按 type 过滤）不显示 TRANSFER 持有记录，需前端"全部"视图或后端 type 参数扩展（M05 持有经调拨产生的用户退库场景）

- [x] **修复：调拨持有一致性**（2026-08-21，用户验收反馈"在用却无人持有"矛盾）：
  - 根因：confirm 原逻辑只在指定负责人时建持有，且只填区域时旧持有闭环后持有人字段不清零——终态既非"有持有"也非"无持有"
  - 修复（终态二选一）：填使用人 → 人持有（TRANSFER）→ IN_USE；只填部门 → 部门持有（user_id=NULL, department=新部门）→ IN_USE；只填区域 → 调拨回库（闭环旧持有 + user_id/user_department 归零）→ IDLE；状态联动走 changeStatus 状态机（同态跳过，DISCARD 前置拦截不受影响）
  - 迁移 `V20260828__allocation_user_id_nullable.sql`：asset_allocation.user_id 改可空（NULL=部门持有，M04 人持有不受影响），已本地库实测应用
  - E2E 三场景实测通过（部门持有 IDLE→IN_USE / 部门持有回库闭环归零 IDLE / 人持有回库闭环归零 IDLE），持有记录零悬死；日志含"（回库）"标记与【状态】联动条目
  - 测试 272/272（surefire 全量口径，新增 3：部门持有 + 回库 + IDLE→IN_USE 联动）

- [x] **M06 实物信息变更单（AOC）**（2026-08-21）：
  - 迁移 `V20260826__change_order_target_values.sql`：change_order 加姓名快照列（applicant_name/confirmer_name，对齐 M04/M05 快照模式）+ 变更后目标值列（new_user_id/new_user_name/new_user_department/new_location_id/new_location_detail/new_company_id，**null = 不变更**，清空类操作走资产编辑）；asset_allocation.type 枚举扩至 CHANGE（已本地库实测应用，flyway 至 v20260826）
  - `enums/change`：ChangeStatus（PENDING/CONFIRMED/CANCELLED）+ ChangeField（变更字段白名单：user_id/user_department/location_id/location_detail/company_id，不含编码/分类等固有属性）
  - `entity/mapper/service/controller/dto` 的 change 上下文：ChangeOrder（主表，一单 = N 台资产 + 一组统一新值）+ ChangeOrderItem（明细，**每台资产的每个实际变化字段一行**，value_before/value_after 存展示值——位置/公司存名称、使用人存姓名，供变更前/后对比与 AOC 打印格式）
  - `ChangeOrderServiceImpl`：create（至少一项变更字段 400 + 资产存在 404 + 报废 409 + 待审批领用/借用占用 409 + 待确认调拨占用 409 + 待确认变更单自身互斥 409 + 变更后位置/公司存在性 400 + AOC 单号锁定读串行取号 + 明细行仅记实际变化字段、指定字段与当前值一致 400"无变更内容"）；confirm（更新 asset 归属字段 + **使用人变化时同步持有关系**：闭环旧 allocation + 新建 type=CHANGE 持有（避免"在用报废悬死持有"同类脱节）+ 写"实物信息变更"日志（明细行 before/after 拼装，与单据严格一致）+ 待确认期间资产报废 409 拦截；**信息修正单据允许发起人自己确认**——区别于 M04/M05 审批流，制单与执行常为同一资产管理员）；cancel（仅发起人可撤 403，资产不变）；全部 @Transactional
  - **M04/M05 反向占用校验**：领用/借用与调拨的 create 均增加待确认变更单占用检查（三向互斥闭环，防止单据同时操作同一资产）
  - 端点：POST/GET /api/v1/change-orders、GET /api/v1/change-orders/{id}、POST /{id}/confirm、POST /{id}/cancel（列表支持 status/userId/assetId/date 筛选，**assetId 按明细行反查该资产的变更历史**）
  - 测试 267/267（新增 40：ChangeOrderServiceImplTest 24 + ChangeOrderControllerTest 12 + M04/M05 反向占用 2）；`Map.of()` 空集 NPE 坑：不可 null-key 查询，名称批量查询空集返回 HashMap
  - **待跟进**：V20260826 需在测试库 172.16.5.247 手动执行；旧系统 AOC 打印格式（Excel File 5）前端打印预览待 M-FE 对接；本地 6006 端口若跑着 M06 之前的旧实例需重启后才含变更单端点

- [x] **M04 补充：领用区域必填 + 审批更新资产位置**（2026-08-21，用户验收反馈"领用后位置不变，盘点会错"）：
  - 迁移 `V20260827__receipt_location.sql`：receive_receipt 加 location_id（存量单 NULL，审批跳过位置更新兼容过渡）
  - 申请契约：`locationId` @NotNull 必填（缺省 400"领用区域不能为空"）+ create 校验位置存在性（400）
  - 审批联动：approve 时资产 `location_id` 更新为领用区域（与持有人同一 update），日志追加"领用区域：XX"；列表/详情回填 `locationName`；home_location_id（归属位置）不动，归还/盘亏可追溯"家"位置
  - 测试 270/270（新增 3：位置缺失 400 ×2 + 存量单跳过位置更新 + 位置 SET 断言）；前端待适配：申请弹窗加"领用区域"必选下拉（GET /api/v1/locations 取数）

- [x] **M07 盘点（Stocktake）**（2026-08-24）：
  - 迁移 `V20260829__stocktake_creator_name_and_transfer_ref.sql`：stocktake 加 creator_name（对齐 M04/M05/M06 快照模式）；transfer_order 加 stocktake_id（盘点触发调拨关联 + 防重复生成 + 反查来源）
  - `enums/stocktake`：StocktakeStatus（PENDING→IN_PROGRESS→COMPLETED/CANCELLED）+ StocktakeItemStatus（PENDING/MATCHED/LOCATION_MISMATCH/NOT_FOUND/EXTRA）
  - `entity/mapper/service/controller/dto` 的 stocktake 上下文：Stocktake（主表，范围=位置含子树/分类，统计计数回填）+ StocktakeItem（明细，expected=创建时账面位置快照）
  - `StocktakeServiceImpl`：create（范围校验 400 + 报废排除 + 空范围 400 + 明细快照批量写入）、start（PENDING 409 校验）、scanItem（提交实际位置判定 MATCHED/LOCATION_MISMATCH，notFound 标记盘亏；已盘不可重盘 409）、scanByBarcode（PDA 入口：任务内资产更新明细/范围外已登记资产记盘盈 EXTRA/未登记 404/报废 409）、complete（剩余待盘批量记盘亏 + 逐台写"盘点处理"日志）、report（五态汇总 + 差异明细，进行中可看实时统计）、createTransfers（位置不符按实际位置分组生成 source=INVENTORY_TRIGGERED 调拨单，任务须 COMPLETED + stocktake_id 防重 409；EXTRA 盘盈不自动调拨，人工处置）、cancel（仅 PENDING/IN_PROGRESS 且创建人 403）；全部 @Transactional
  - `TransferOrderService` 新增 create 重载（显式 source + stocktakeId，原方法委托 source=MANUAL 不变）；TransferOrder/TransferResp 透传 stocktakeId
  - 端点：POST/GET /api/v1/stocktakes、GET /{id}、POST /{id}/start、POST /{id}/cancel、GET /{id}/items?status=、POST /{id}/items/{itemId}/scan、POST /{id}/scan（PDA 按条码）、POST /{id}/complete、GET /{id}/report、POST /{id}/transfer
  - 统计用 SQL groupBy（selectMaps 别名规避 map-underscore 配置差异）；复用 M06 教训：Map.of() 空 map 不可 null-key 查询（范围/位置为 null 时先判空再 get）
  - 测试 314/314（新增 44：StocktakeServiceImplTest 28 + StocktakeControllerTest 15 + TransferOrderServiceImplTest 1 盘点触发重载）
  - **待跟进**：V20260829 需在测试库 172.16.5.247 手动执行；本地 6006 重启后 Flyway 自动落地；PDA 扫码前端（uni-app）Phase 4 独立会话实现

- [x] **B1 定向待办（assignee 模型）**（2026-08-27，commit 8afbe18，审批中心方案 §3-B1）：
  - 迁移 `V20260830__approval_assignee.sql`：receive_receipt/transfer_order/change_order 加 assignee_user_id（NULL=共享池，存量数据不动）+ 索引
  - 三单提交体/列表 Query/Resp 透传 assigneeUserId；service 校验收紧：提交时 assignee 不能是申请人自己（死单防御）+ 存在性校验（comm_public_basic sys_user）；approve/reject/confirm 操作人必须 = assignee（NULL 保持非发起人即可）
  - `auth/UserDirectory`：独立 JDBC 只读 sys_user（`app.user-directory.*` 配置，与 M08 `app.migration.auth-db-*` 刻意分离——生命周期/权限/降级语义独立）；未配置时存在性校验降级跳过、搜索 503
  - `GET /api/v1/users?keyword=&page=&size=`：用户搜索（工号/姓名模糊，分页），F3 选人器数据源
  - 测试：UserDirectoryTest + 三单 service 测试（assignee 校验分支 + 存量 NULL 回归）

- [x] **B2 通知中心**（2026-08-27，审批中心方案 §3-B2）：
  - 迁移 `V20260831__notification.sql`：sys_notification 表（user_id/type/title/biz_type/biz_id/read_flag + user_id,read_flag,id 索引）
  - `notification/` 上下文：SysNotification 实体 + NotificationType 枚举（DOC_SUBMITTED/APPROVED/REJECTED/COMPLETED）+ NotificationService（notify 与业务同事务写入；接收人空静默跳过；标题超长截断 200）
  - 端点：GET /api/v1/notifications（分页，unread=true 仅未读）、GET /unread-count、POST /{id}/read（404 防越权）、POST /read-all
  - 写入点：三单定向提交→通知 assignee；审批通过/拒绝→通知申请人（M04 领用借用 + M05 调拨）；变更确认→通知申请人（M06）；共享池单据无定向接收人不通知
  - 测试：NotificationServiceImplTest 9 + 三单 service 集成断言

- [x] **B3 统计聚合**（2026-08-27，审批中心方案 §3-B3）：
  - `GET /api/v1/stats/overview`：assetStatusCounts（GROUP BY status，AssetStatus 枚举全集补 0）/ myTodoCount（三单 PENDING 定向我或共享池；领用借用调拨排除我发起，变更允许自审含我发起——与审批中心"待我处理"口径一致）/ myHoldingCount（asset.user_id=我 且 IN_USE）/ inProgressStocktakeCount（我创建的进行中盘点，用户确认口径）
  - `stats/` 上下文：StatsService 纯 count 聚合，无缓存；简单索引列等值查询
  - 测试 372/372（`mvn test` 全量通过）；Flyway V20260830/V20260831 本地库实测落地

- [x] **CI/CD：push 自动部署演练沙箱 + 一键回滚**（2026-08-28，commit 44b52e8）：
  - `.gitlab-ci.yml` 扩展为两阶段：`verify`（mvn clean verify，纯 Mockito/MockMvc 单测无 DB 依赖）→ `deploy`（任意分支 push 自动触发）
  - 部署目标：演练沙箱 **193.112.174.178**（Lighthouse）；流程 = mvn package → 备份沙箱当前 jar 到 `/srv/backups/asset-backend.jar.prev` → scp 推新 jar → `docker compose up -d --build asset-backend` 重建容器 → 探活（sleep 20s + `/actuator/health` UP 才算成功）
  - **一键回滚**：`rollback` 任务 `when: manual`（Pipeline 页手动触发），恢复 `.jar.prev` 并重建容器；首次部署前无备份时明确报错退出
  - Runner 为 sirpinaple 本机 shell executor（Mac），复用本机 Maven 仓库；非交互 shell 统一补齐 PATH（brew/sdkman/nvm）+ JAVA_HOME；SSH 免密 `id_ed25519`
  - **注意**：任意分支 push 都会部署沙箱——功能未完成时慎 push（或接受沙箱短暂处于半成品状态，可 rollback 回退）

- [x] **方案文档两份**（docs/，2026-08-27/28）：
  - [资产台账OA完整解决方案.md](./docs/资产台账OA完整解决方案.md)：责任到人的全生命周期台账 OA 方案 v1.0（持有关系双轨制/离职对账/双链路留痕等核心设计），对外讲解与评审用总纲
  - [资产数据流转说明.md](./docs/资产数据流转说明.md)：领用/退库/调拨的完整数据流转（依据 M04/M05/B1/B2 实际代码，含状态机与两条不变式），前后端联调与测试用对照文档
  - 两文档均为未跟踪文件，随下批业务改动一并提交

- [x] **分类两级结构重构 + 资产"细则"字段**（2026-08-28，依据《资产规范.docx》，工作区待提交）：
  - 迁移 `V20260832__category_tree_and_asset_spec.sql`（本地库已实测应用）：
    - 分类重构为两级：4 大类（房屋建筑 SKFW / 办公设施 SKBG / 运输设备 SKYS / 生产设备 SKSC，sort 1-4）+ 子类（办公设施：IT数码 SKBGIT / 空调 SKBGKT / 辅助办公 SKBGFZ / 家具 SKBGJJ；生产设备：镀膜 SKSCDM / 清洗 SKSCQX / 移印 SKSCYX / 烘箱 SKSCHX / 检测 SKSCJC / 辅助 SKSCFZ / 虚拟资产 SKSCXN）；复用原 9 行中 8 行挂树（566 条资产零换绑），新增清洗/移印/家具 3 子类；「A资产」7 条资产引用迁至辅助设备后逻辑删除；全类前缀覆盖（含大类，资产直挂大类不再回退 SK）
    - asset 表新增 `spec`（细则，varchar(500)）：同品牌型号的配置差异（如内存大小）；Asset 实体/AssetReq(@Size 500)/AssetResp/AssetExportRow（导出列"细则"）全链贯通
  - `generateBarcode` 前缀向上继承：分类自身无 prefix 沿 parent 链取最近非空值（防回退 SK）
  - CategoryServiceImpl 健壮性（页面维护分类的护栏）：名称必填 + 同级重名 400 + 父分类必须存在且为一级（两级上限）+ 不得把自己设为父（防环）+ 带子分类不得再挂父 + code 冲突友好 400（预检 + DuplicateKeyException 兜底，逻辑删除行占用也覆盖）+ 删除保护（有子分类/被资产引用 400，删除时释放 code 供重建）；CategoryController 补 POST/PUT/DELETE 端点（前端 M02 页面契约早已调用）；CategoryReq 补 barcodePrefix
  - 验证：mvn compile 通过；测试 392/392（CategoryServiceImplTest 重写 17 个覆盖全部校验分支 + CategoryControllerTest 补 7 个增删改用例）；本地库迁移后核对：分类树两级 15 行活跃、资产 category_id 悬空引用 0、前缀全覆盖、辅助设备 100 条（93+7 迁入）
  - **待跟进**：V20260832 需在测试库 172.16.5.247 手动执行；本地 6006 需重启加载新代码（spec 字段 + 分类写端点）；前端适配已完成（feat/m02-basedata：资产弹窗/列表"细则"字段 + 分类页两级树）
- [x] **前端 feat/m02-basedata 契约对齐**（2026-08-28 第二批，工作区待提交）：
  - 分类：code 后端自动生成（`CAT{id}`，前端契约不提交 code）；update 修复误清存量 code（请求 code 为空时保持库中原值，如 V20260832 的 COATING）
  - 位置：LocationServiceImpl 补同级重名拦截（save/update，400 带原因）；删除保护错误码 409→400 对齐前端契约（有子位置/被资产当前位置或应归放位置引用，countAssetRefs 已覆盖 location_id OR home_location_id）；code 后端自动生成（`LOC{id}`）；LocationReq.sortOrder 已有无需改
  - 资产 spec：上批已全链贯通（Req/Resp/导出"细则"列），AssetQuery 不支持 spec 搜索（前端仅展示，符合预期）
  - 验证：全量测试 400/400（位置服务 +6 用例、分类服务 +2 用例）

## 进行中

- [ ] **M08-A/B 历史数据迁移执行验证**（代码完成 2026-08-24，已提交 76a3604，测试 340/340）：
  - `migration/` 包（一次性工具模块）：MigrationService（编排：基础数据→资产→领用单→调拨单→日志）+ MigrationController（`POST /api/v1/migration/run`，双重门禁：`app.migration.enabled` + `asset-资产管理员` 角色）+ OperatorTextParser（操作人文本→工号/姓名）+ LogContentParser（content→diff_json）+ AuthUserDirectory（sys_user 匹配/建号，独立 JDBC 直连 comm_public_basic 库）
  - **实测源数据规模**（旧系统 2026-08-18 导出）：资产 563 / 领用单 **162**（M08 文档记 346 系含空行口径）/ 调拨单 56 / 日志 **1773**（文档记 1775 偏差 2 条）
  - 幂等策略：资产/单据按 barcode/serial_no upsert；**asset_log 按"时间区段清除+重灌"**（created_at < 2026-08-19 的日志只可能来自迁移，run 开始时清除后重写，含 563 条"迁移导入"标记日志）；持有关系仅补建（已有持有中跳过）
  - 用户匹配：工号（SK\d+）→ 姓名唯一命中 → 重名取最小 id（警告）→ 未命中**自动创建 sys_user 账号**（SK 工号段顺延，BCrypt 默认密码 123456，remark 标记来源；默认密码用户被 comm_public_basic 拦在业务系统外，改密后可登录）
  - 关键映射决策：报废/闲置资产清使用人（对齐 M04 归还/M03 报废联动不变量）；在用资产补建 asset_allocation（type=RECEIVE + note 标记）；领用单/调拨单导出**无资产明细列**只迁主表（items 空，待审批单在新系统无明细可审批——数据局限，见 MigrationService 类注释）；规格型号并入 remark（"规格：xxx"）；操作人含"+盘点"后缀的调拨单 source=INVENTORY_TRIGGERED；位置树按 Excel 区域路径逐级建节点，叶子段同名顶级种子节点收养归位（不产生双节点）
  - **有意偏离 M08 文档**："通过 service 层方法写入"改为直写 mapper——业务方法会重新生成编码、强制状态、以当前时间写日志，与迁移保真冲突
  - 配置：`app.migration.*`（enabled/xlsx-dir/auth-db-*，默认关闭；本地已配 ~/Downloads + 本地 comm_public_basic 库）；AssetLog 实体补 diffJson 字段（V20260819 建表已有列）
  - 测试 340/340（新增 26：OperatorTextParser 8 + LogContentParser 7 + AuthUserDirectory 8 + 状态映射 3）
  - **待执行**：重启本地 6006（新代码已编译进 target/classes）→ Redis 管理员 token（如 `73075822b7a34ab8976b9634f1f56d52`）或前端登录态调 `POST /api/v1/migration/run` → 按结果 warnings 复核；注意本地测试资产 SFBGIT2528/SFBGIT2563 在 Excel 中，迁移会将其重置为历史基线（预期行为）

## 待办（按模块分阶段，详见 docs/modules/）

### Phase 1 — 骨架（所有模块的前提）
1. ~~**M01-A** 后端骨架~~（已完成，2026-08-19）
2. ~~**M01-B** 鉴权接入：AuthPort + TokenFilter + UserContext + `/api/v1/me` 验证接口~~（已完成，2026-08-19）
3. ~~**M-FE01** 前端骨架~~（已完成，2026-08-19）

### Phase 2 — 基础数据（M03+ 的外键依赖）
6. ~~**M02** 基础数据 CRUD：company / asset_category / asset_location(树) / manufacturer / supplier / asset_model~~（已完成，2026-08-20）

### Phase 3 — 核心业务（按依赖顺序）
7. ~~**M03** 资产主表 CRUD + 状态机（IDLE/IN_USE/DISCARD/PENDING_CONFIRM）~~（已完成，2026-08-22）
8. ~~**M04** 领用/借用单（ARE/BOR）审批流：申请→审批→资产状态联动（`receive_receipt.type` 区分领用/借用，共用单据流）~~（已完成，2026-08-21）
9. ~~**M05** 调拨单（ATR）审批流：调出→调入确认→归属更新~~（已完成，2026-08-21）
10. ~~**M06** 实物信息变更单（AOC）：变更前/后记录 + 确认执行~~（已完成，2026-08-21）
11. ~~**M08-A/B** 历史数据迁移：563条资产 + 162条领用单 + 56条调拨单 + 1773条日志~~（代码完成 2026-08-24，执行验证见"进行中"）

### Phase 4 — 支撑功能（Phase 3 完成后）
12. ~~**M07** 盘点（Stocktake）：扫码核对 + 差异处理 + 触发调拨~~（已完成，2026-08-24）
13. ~~CI 流水线：编译 + 测试~~（已完成，2026-08-20 GitLab CI verify；2026-08-28 扩展为 verify + 自动部署沙箱 + 手动回滚，见"CI/CD"条目）
14. **M09** 折旧（低优先级，需 asset.amount 有真实数据才有意义）
15. 审批中心前端 F3/F4 适配（后端 B1/B2/B3 已就绪：待办列表/通知中心/工作台统计）
16. PDA 扫码前端（uni-app，M07 盘点移动端入口，独立会话实现）

## 关键决策记录

| ADR | 主题 | 状态 |
|---|---|---|
| [0001](./docs/adr/0001-技术栈选型.md) | 技术栈：Spring Boot 3 + MySQL + Vue3 + Element Plus | Accepted（版本由 0004 修订为 3.x） |
| [0002](./docs/adr/0002-DDD分层架构与Maven多模块.md) | DDD 分层 + Maven 多模块 | **Superseded by 0005** |
| [0003](./docs/adr/0003-多Agent上下文传承机制.md) | AGENTS.md + ADR + STATUS 分层传承 | Accepted |
| [0004](./docs/adr/0004-鉴权复用comm_public_basic与SpringBoot3解耦.md) | 鉴权复用 comm_public_basic + HTTP 解耦 + 钉钉 | Accepted |
| [0005](./docs/adr/0005-架构降级为单模块三层.md) | 单模块三层（Superseded 0002） | Accepted |
| [0006](./docs/adr/0006-数据库核心表设计.md) | 数据库核心表设计：model中间层/manufacturer+supplier分离/asset_allocation/depreciation_rule | Accepted |

## 阻塞 / 待决策

- （无。原两项阻塞已于 2026-08-19 解除，核实结论见"M01-B 前置阻塞项解除"条目）

## 本地开发环境备忘（M01-A/M01-B 实测，2026-08-19；CI/沙箱 2026-08-28 补充）

- **演练沙箱**：193.112.174.178（Lighthouse），`/srv/app/asset-backend/` + `/srv/docker-compose.yml`（容器 asset-backend）；部署与回滚全由 GitLab CI 代劳（push 即部署，Pipeline 页手动 rollback），**不要手工改沙箱 jar**——会被下次 CI 部署覆盖，且丢失备份链
- 后端端口 **6006**；本地库 `db_sk_asset`（127.0.0.1:3306，实际为 MySQL 9.6 Homebrew 版，非 MariaDB）
- `application-local.yml`（gitignore）存连接信息 + comm_public_basic 地址（127.0.0.1:6002），模板 `application-local.yml.example`
- comm_public_basic 本地（私改版）：端口 6002，库 `db_comm_public_basic`（127.0.0.1:3306），token TTL -1 永久；登录 `POST /login/check` 密码需 RSA 公钥加密（PKCS#1 v1.5，公钥见其 application.yml；openssl 命令：`printf '密码' | openssl pkeyutl -encrypt -pubin -inkey pub.pem | base64`，公钥包 X.509 PEM 头尾）
- ⚠️ admin/123456 为**默认密码用户**：comm_public_basic 策略拦截其进入业务系统（userSystemAuth 返回空角色 → asset-backend 403）。联调 200 成功路径需使用改过密码或新建的用户
- asset 已注册 `basic_portal_project`（id=22，**is_visible=0, is_enabled=0**；comm_public_basic 约定 0=显示/启用 1=隐藏/禁用，与前端直觉相反——应用中心按 0/0 过滤展示）
- 联调账号：assetfe（`Asset@2026`）/ SK9802（`Sk9802@2026`），均分配 role_id=350 asset-资产管理员；⚠️ 重置密码会设回默认 123456 → 触发"默认密码用户不得进入业务系统"拦截 → userSystemAuth 返回空角色 → 403，改密码即可恢复
- ⚠️ **M02 种子数据脚本版本号必须晚于 20260819**（V1 已占用 `V20260819`，M02 文档中的 `V20260819__seed_base_data.sql` 需改为实施当日版本号，否则 Flyway 撞号）
- ⚠️ knife4j 只引入 `knife4j-openapi3-ui` 静态 webjar：其 4.5.0 增强 starter 与 springdoc 2.3+ 不兼容（`getGroupConfigs` 移除触发 NoSuchMethodError），勿改回 `knife4j-openapi3-jakarta-spring-boot-starter` + `knife4j.enable=true`
- Flyway 对 MySQL 9.6 报"未测试版本"警告（正常，迁移已成功；如遇问题可显式升级 flyway 版本）

## 参考资料索引

- 业务模型参考：`references/ciyo-itasset/ciyo-asset/`（itam 下的 Device/Categories/Locations/Models/Manufacturers/Suppliers/Accessories/Consumables/Licenses/Stocktakes/Allocations/AssetRequests/Failures/Depreciations）
- 工程规范参考：`references/asset-mgmt-system/`（Flyway V1-V5、Docker、Swagger、测试）
- 鉴权复用对象：`/Users/zhuanzmima0000/Documents/git/comm_public_basic/`（本地私改版，仅读源码理解逻辑；原始版在 GitLab `gitlab.tritree.cn/tt_java/comm_public_basic`）
- 业务标杆：Snipe-IT（GitHub snipe/snipe-it，字段最全）

---

**最后更新**：2026-08-28（补登 CI 自动部署演练沙箱 + 一键回滚、OA 方案/数据流转两份文档；分类两级重构 + spec 字段 + 前端契约对齐归档为已完成（工作区待提交）；刷新当前阶段与 Phase 4 待办）
**当前阶段负责人**：待指派
