# STATUS.md — 项目进度看板

> 任何阶段性进展完成后必须更新本文件。最后更新时间见文末。
>
> **修订记录**：2026-08-18 经 REVIEW-001 审阅后修订，架构降级为单模块三层 + 鉴权复用 comm_public_basic。

## 当前阶段

**Phase 1 骨架搭建中**：M01-A 后端骨架已完成，下一步 M01-B 鉴权接入（AuthPort + TokenFilter）。

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

## 进行中

- （无）

## 待办（按模块分阶段，详见 docs/modules/）

### Phase 1 — 骨架（所有模块的前提）
1. **[阻塞] clone auth-center-frontend** 确认前端 token 传递方式（见阻塞项）
2. **[阻塞] 核实 comm_public_basic GitLab 原始版接口契约**（见阻塞项）
3. ~~**M01-A** 后端骨架~~（已完成，2026-08-19）
4. **M01-B** 鉴权接入：AuthPort + TokenFilter + UserContext + `/api/v1/me` 验证接口
5. **M-FE01** 前端骨架：Vue3+Vite+Element Plus+Pinia + token 接收拦截器 + /dashboard 验证页

### Phase 2 — 基础数据（M03+ 的外键依赖）
6. **M02** 基础数据 CRUD：company / asset_category / asset_location(树) / manufacturer / supplier / asset_model

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

- **comm_public_basic 接口契约待核实**：本地为私改版，接入实现时须以 GitLab 原始版为准（用户提示）。需在 M01-B 实现前从 GitLab 拉取/核实 `/login/checkToken`、`/login/userSystemAuth`、`PortalProject` 的原始接口契约。
- **auth-center-frontend 接入方式待确认**：clone 后校准前端 token 传递方式。

## 本地开发环境备忘（M01-A 实测，2026-08-19）

- 后端端口 **6006**；本地库 `db_sk_asset`（127.0.0.1:3306，实际为 MySQL 9.6 Homebrew 版，非 MariaDB）
- `application-local.yml`（gitignore）存连接信息，模板 `application-local.yml.example`
- ⚠️ **M02 种子数据脚本版本号必须晚于 20260819**（V1 已占用 `V20260819`，M02 文档中的 `V20260819__seed_base_data.sql` 需改为实施当日版本号，否则 Flyway 撞号）
- ⚠️ knife4j 只引入 `knife4j-openapi3-ui` 静态 webjar：其 4.5.0 增强 starter 与 springdoc 2.3+ 不兼容（`getGroupConfigs` 移除触发 NoSuchMethodError），勿改回 `knife4j-openapi3-jakarta-spring-boot-starter` + `knife4j.enable=true`
- Flyway 对 MySQL 9.6 报"未测试版本"警告（正常，迁移已成功；如遇问题可显式升级 flyway 版本）

## 参考资料索引

- 业务模型参考：`references/ciyo-itasset/ciyo-asset/`（itam 下的 Device/Categories/Locations/Models/Manufacturers/Suppliers/Accessories/Consumables/Licenses/Stocktakes/Allocations/AssetRequests/Failures/Depreciations）
- 工程规范参考：`references/asset-mgmt-system/`（Flyway V1-V5、Docker、Swagger、测试）
- 鉴权复用对象：`/Users/zhuanzmima0000/Documents/git/comm_public_basic/`（本地私改版，仅读源码理解逻辑；原始版在 GitLab `gitlab.tritree.cn/tt_java/comm_public_basic`）
- 业务标杆：Snipe-IT（GitHub snipe/snipe-it，字段最全）

---

**最后更新**：2026-08-19（M01-B 前置处理完成：BearerAuth 文档认证 / CORS 白名单 / permitAll 限 local）
**当前阶段负责人**：待指派
