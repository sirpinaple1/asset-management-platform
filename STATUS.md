# STATUS.md — 项目进度看板

> 任何阶段性进展完成后必须更新本文件。最后更新时间见文末。
>
> **修订记录**：2026-08-18 经 REVIEW-001 审阅后修订，架构降级为单模块三层 + 鉴权复用 comm_public_basic。

## 当前阶段

**Phase 2 完成 → Phase 3 开始**：M01 骨架 + M02 基础数据模块均已完成，下一步 M03 资产主表 CRUD + 状态机。

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
  - API 端点：GET/POST/PUT/DELETE /api/v1/manufacturers、/api/v1/suppliers、/api/v1/categories、/api/v1/locations、/api/v1/models；GET /api/v1/companies（只读）
  - **待跟进**：V20260820 种子数据脚本已写入 src/main/resources/db/migration/，按项目约定需在测试库 172.16.5.247 上手动执行

## 当前阶段

**Phase 2 完成 → Phase 3 开始**：M02 基础数据模块已完成，下一步 M03 资产主表 CRUD + 状态机。

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
7. **M03** 资产主表 CRUD + 状态机（IDLE/IN_USE/DISCARD/PENDING_CONFIRM）
8. **M04** 领用单（ARE）审批流：申请→审批→资产状态联动
9. **M05** 调拨单（ATR）审批流：调出→调入确认→归属更新
10. **M06** 实物信息变更单（AOC）：变更前/后记录 + 确认执行
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

**最后更新**：2026-08-20（M02 基础数据模块完成；71 个测试全部通过；V20260820 种子数据脚本待手动执行至测试库 172.16.5.247）
**当前阶段负责人**：待指派
