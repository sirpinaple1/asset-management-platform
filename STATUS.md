# STATUS.md — 项目进度看板

> 任何阶段性进展完成后必须更新本文件。最后更新时间见文末。
>
> **修订记录**：2026-08-18 经 REVIEW-001 审阅后修订，架构降级为单模块三层 + 鉴权复用 comm_public_basic。

## 当前阶段

**Phase 3 进行中**：M06 实物信息变更单（AOC）已完成，下一步 M08-A/B 历史数据迁移。

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
  - **待跟进**：V20260825 需在测试库 172.16.5.247 手动执行；前端持有关系列表（领用&退库/借用&归还按 type 过滤）不显示 TRANSFER 持有记录，需前端"全部"视图或后端 type 参数扩展（M05 持有经调拨产生的用户退库场景）

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

## 进行中

- （无）

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
11. **M08-A/B** 历史数据迁移：563条资产 + 346条领用单 + 56条调拨单 + 1775条日志

### Phase 4 — 支撑功能（Phase 3 完成后）
12. **M07** 盘点（Stocktake）：扫码核对 + 差异处理 + 触发调拨
13. **M09** 折旧（低优先级，需 asset.amount 有真实数据才有意义）
14. CI 流水线：GitHub Actions（编译 + 测试）

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

## 本地开发环境备忘（M01-A/M01-B 实测，2026-08-19）

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

**最后更新**：2026-08-21（M06 实物信息变更单完成，测试 267/267；同日完成 M05 调拨单审批流、在用报废悬死持有关系修复、资产编码自动生成、M04 审批流）
**当前阶段负责人**：待指派
