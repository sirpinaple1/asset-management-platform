# ADR-0004 — 鉴权复用 comm_public_basic + Spring Boot 3.x + HTTP 解耦

- 状态：Accepted
- 日期：2026-08-18
- 决策者：项目 Owner
- 关联：修订 ADR-0001 的版本与鉴权条款；与 ADR-0005（架构降级）配套

## 背景

审阅（REVIEW-001）发现：comm_public_basic 已是公司部署在服务器的鉴权中心，承担多系统登录/用户/角色/菜单/权限/钉钉登录。asset 系统若自建登录，既重复造轮子，又与公司统一身份体系割裂。用户明确要求复用 comm_public_basic 后端 + auth-center-frontend 前端登录服务，并兼容钉钉手机端登录扩展。

源码确认的关键事实：
- comm_public_basic 本地版为 Spring Boot 2.7.17 + Redis Token（非 JWT，`JwtUtils` 已注释）+ 多系统接入（`PortalProject` + `/login/userSystemAuth(systemCode)`）+ 钉钉登录（`/login/dingTalk` + 绑定/解绑）。
- 本地版为开发便利私改版（含永久 token 等），**原始生产逻辑以 GitLab 仓库为准**。接入实现时须以 GitLab 原始版接口契约为准核实。
- comm_public_basic 受"永不提交/同步"红线约束，是独立服务，不是可引入的库。

## 决策

### 1. asset 后端不实现登录，通过 HTTP 复用 comm_public_basic 鉴权

- 登录、登出、token 刷新、用户信息、权限查询全部走 comm_public_basic 现有接口：
  - `POST /login/check`（账密，RSA 加密）
  - `POST /login/dingTalk`（钉钉登录）
  - `POST /login/logout`
  - `GET /login/getAuth`（当前用户信息）
  - `POST /login/refresh`
  - `GET /login/checkToken`
  - `POST /login/userSystemAuth`（请求头 `systemCode`，返回用户在 asset 系统的角色/菜单/按钮权限）
- asset 后端**不存储用户/角色/菜单/权限主数据**，按需 HTTP 调用获取。
- asset 系统在 comm_public_basic 的 `PortalProject` 注册一个项目（`projectEn=asset`，`permissionCode=asset`），菜单/按钮权限由 comm_public_basic 统一配置。

### 2. Token 校验：HTTP 调用 + 本地短时缓存

- asset 后端收到请求 → 提取 token → 查本地缓存（key=token，value=用户信息+权限，TTL 30-60s）→ 未命中则 HTTP 调 `comm_public_basic /login/checkToken` + `/login/getAuth` + `/login/userSystemAuth` 校验并回填缓存。
- 缓存仅用于摊薄性能，失效后回源。token 失效（登出/过期）由缓存 TTL 自然收敛 + comm_public_basic 校验兜底。
- **不共享 Redis**（避免与 comm_public_basic 强耦合，且其 Redis schema 受私改影响不稳定）。

### 3. Spring Boot 版本：3.x（与 comm_public_basic HTTP 解耦）

- asset 后端采用 Spring Boot 3.x（jakarta.*），通过 HTTP 与 comm_public_basic（2.7.x，javax.*）解耦。
- HTTP 解耦使 asset 版本不被 comm_public_basic 锁定，可独立演进；接口契约（URL + 请求/响应结构）跨版本稳定。

### 4. 钉钉手机端登录：接入 comm_public_basic 现有能力

- comm_public_basic 已实现 `/login/dingTalk`（ddUserId → token）+ 用户钉钉绑定/解绑。
- asset 前端在钉钉环境内调 `/login/dingTalk` 完成登录，无需自建钉钉后端逻辑。
- 钉钉端"操控"（资产扫码盘点等）由 asset 业务接口承担，鉴权同上。

### 5. 前端登录态：auth-center-frontend 为主，asset 前端接收 token

- 登录主流程走 auth-center-frontend（已部署），asset 前端不写登录页。
- 登录后跳转 asset 前端，token 通过 URL query / localStorage 传递（接入方式待 clone auth-center-frontend 后校准，见 STATUS 待办）。
- asset 前端请求统一带 `Authorization: Bearer <token>` 头 + `systemCode: asset` 头。

## 备选方案

- **asset 后端用 Spring Boot 2.7.x 与 comm_public_basic 同构**：未选。同构优势有限（comm_public_basic 是独立服务不能引依赖，javax 一致无实际复用收益），且 2.7 已停 OSS 维护。
- **共享 Redis 直读 token**：未选。与 comm_public_basic 强耦合，其 Redis schema 受私改不稳定，且跨服务共享存储是反模式。
- **asset 自建登录**：未选。与公司统一身份体系割裂，重复造轮子，违背用户诉求。
- **依赖 comm_public_basic 打包成 jar 引入**：未选。它是独立服务非库，且受"永不提交"红线约束。

## 后果

- 正面：asset 系统零登录代码、零用户/权限主数据维护；钉钉登录零成本接入；与公司统一身份体系一致；版本独立演进。
- 负面：每次请求需 token 校验（本地缓存 30-60s 摊薄）；强依赖 comm_public_basic 可用性（可用性风险通过缓存 + 降级策略缓解）。
- 约束：comm_public_basic 接口契约变更会影响 asset——须在接入实现时以 GitLab 原始版为准核实，并在 STATUS/ADR 记录契约快照。
- 后续：搭骨架时实现 `AuthPort`（HTTP 客户端 + 缓存），注册 PortalProject，clone auth-center-frontend 校准前端登录态传递。
