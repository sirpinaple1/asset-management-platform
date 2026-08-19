# REVIEW-001 — 方案可实施性审阅

- 审阅日期：2026-08-18
- 审阅对象：AGENTS.md / STATUS.md / docs/ENGINEERING.md / docs/adr/0001-0003
- 审阅依据：comm_public_basic 实际代码、Ciyo ciyo-asset 实际代码、用户最新诉求（基础功能优先 + 复用现有鉴权 + 钉钉扩展）
- 审阅结论：**部分不可实施，需重大调整后方可落地**

---

## 一、核心事实澄清（审阅前提）

### 1. comm_public_basic 真实形态（已读源码确认）

| 维度 | 事实 |
|---|---|
| 技术栈 | Spring Boot **2.7.17** + Java 17 + MyBatis Plus 3.5.3 + Spring Security + Redis + 钉钉 SDK |
| Token 机制 | **Redis Token（非 JWT）**。`JwtUtils.java` 整文件已注释，`TokenAuthenticationFilter` 注释明确"纯 Redis 方案，替代 JWT"。Token 是 opaque 字符串存 Redis，**无签名密钥可共享** |
| 多系统接入 | **已就绪**。`PortalProject`（门户项目表）= 系统注册表；`LoginController#getUserSystemAuth(systemCode)` 通过请求头 `systemCode` 返回用户在该系统的角色/菜单/按钮权限 |
| 钉钉登录 | **已实现**。`/login/dingTalk` + `UserDingTalkBindRequest`/`UnbindRequest`（绑定/解绑）+ `DingTalkController` |
| 登录接口 | `/login/check`（账密 RSA 加密）、`/login/dingTalk`、`/login/getAuth`、`/login/refresh`、`/login/checkToken`、`/login/logout` |
| 用户上下文 | `UserContextUtils.getCurrentUserId()` ThreadLocal |
| 部署状态 | **已部署在服务器**，承担公司多系统鉴权中心 |
| 仓库约束 | 用户既定红线：**永不提交、永不同步、本地私改**（见用户档案） |

### 2. Ciyo ciyo-asset 真实形态（已读源码确认）

| 维度 | 事实 |
|---|---|
| 架构 | **若依风格传统三层**（controller / service / serviceImpl / mapper / entity / vo / req / enums），**非 DDD** |
| 包组织 | 按业务实体平铺在 `com.ciyocloud.itam` 下：Device/Categories/Locations/Models/Manufacturers/Suppliers/Accessories/Consumables/Licenses/Stocktakes/Allocations/AssetRequests/Failures/Depreciations/Offering 等 15+ 实体 |
| 模块数 | 10 模块（admin/asset/auth/common/file/form/generator/job/message/system），按**业务+技术**分，非按 DDD 分层 |
| License | **GPL-3.0**（强传染性，闭源商用不得引入源码） |

### 3. 团队既有栈（已读本地仓库确认）

- produce（WMS 后端）、new_mes/new_wms（Vue3 前端）、new_app_mes（uni-app PDA）均已在本地
- new_mes/new_wms 前端已有完整 Vue3+Element Plus+Pinia 脚手架（layouts/stores/api/hooks/utils 齐全），可复用工程经验
- new_app_mes 有 uni-app 扫码/移动端经验，可复用于资产扫码盘点

---

## 二、方案偏差点（不可实施项）

### 偏差 1：ADR-0001 技术栈与 comm_public_basic 异构（严重）

**现状**：ADR-0001 定 Spring Boot **3.4**；comm_public_basic 是 **2.7.17**。

**后果**：
- Spring Boot 3.x 用 `jakarta.*`，2.7 用 `javax.*`，**包名不兼容**，asset 后端无法直接引用 comm_public_basic 的类（即便它愿意被打包成库）。
- Token 是 Redis opaque 字符串，asset 后端**无法本地校验**（无共享密钥）。校验必须走 HTTP 调用 comm_public_basic 的 `/login/checkToken` 或 `/login/getAuth`，每次请求一次外部调用，有性能与可用性开销。
- 替代：asset 后端共享 comm_public_basic 的 Redis（直读 token），但跨服务共享 Redis 是强耦合，且 comm_public_basic 是"永不提交"的私改仓库，Redis schema 不受 asset 控制。

**结论**：技术栈版本与鉴权复用方式强相关，**ADR-0001 必须重新决策**。

### 偏差 2：ADR-0002 DDD 6 模块过度设计（与"基础功能优先"冲突）

**现状**：ADR-0002 定 DDD 6 模块（admin/interface/application/domain/infrastructure/common）。

**问题**：
- 参考项目 Ciyo 是传统三层，**不是 DDD**——"参考"名不副实，照 DDD 写无法直接对照 Ciyo 代码。
- comm_public_basic 是传统三层，asset 走 DDD 与既有鉴权体系风格割裂。
- 用户明确"前期不要过度发散设计，着眼于基础功能"——DDD 6 模块的样板代码（聚合根/值对象/端口/ArchUnit）对基础 CRUD 是显著开销。
- 团队栈（produce/new_mes）均为传统三层，DDD 学习成本会拖慢基础功能落地。

**结论**：**ADR-0002 应降级**为单模块三层 + 包按限界上下文分包（chyinan 风格），保留"分门别类"诉求但去掉 DDD 样板。

### 偏差 3：STATUS.md 待办"前端脚手架含登录"错误

**现状**：STATUS.md 待办第 3 项"前端脚手架：Vue3 + Vite + Element Plus + Pinia 空工程"，隐含自建登录。

**问题**：用户明确登录复用 auth-center-frontend（已部署），asset 前端**不写登录页**。asset 前端是业务前端，登录态从 auth-center-frontend 带过来（token in URL/header/localStorage）。

**结论**：前端待办需改为"业务前端脚手架 + token 接收/刷新/拦截器对接 comm_public_basic"。

### 偏差 4：ENGINEERING.md R5 License 红线表述不完整

**现状**：R5 只提 GPL/AGPL 参考项目风险。

**遗漏**：comm_public_basic 是公司自有代码（GitLab 私有），无 License 问题，可深度复用——但受"永不提交/同步"约束，**只能 HTTP 调用，不得作为子模块/依赖引入**。这一点应写入红线，避免后续 Agent 误把 comm_public_basic 拉进 asset 工程依赖。

### 偏差 5：钉钉扩展被低估为"后续"

**现状**：用户提到"兼容钉钉手机端登录与操控等后续扩展"，方案里未展开。

**事实**：comm_public_basic **已实现钉钉登录全流程**（`/login/dingTalk` + 绑定/解绑）。asset 系统接入钉钉几乎零后端成本，只需前端在钉钉环境调 `/login/dingTalk`。这不是"后续扩展"，而是**接入鉴权时一并完成**的低成本项。方案应明确这一点，避免重复造轮子。

---

## 三、调整建议（具体到文档条款）

| 文档 | 条款 | 调整建议 |
|---|---|---|
| ADR-0001 | 技术栈版本 | **重新决策**：Spring Boot 2.7.x（与 comm_public_basic 同构）或 3.x（与 Ciyo 对齐，但鉴权走 HTTP 解耦）。见下方决策点 D1 |
| ADR-0002 | DDD 6 模块 | **降级**：单 Maven 模块 + 三层（controller/service/mapper）+ 包按限界上下文分包（asset/category/location/lifecycle/inventory/depreciation/consumption）。保留"分门别类"，去 DDD 样板 |
| ADR-0001 | 鉴权方式 | **新增条款**：asset 后端不实现登录，通过 HTTP 调用 comm_public_basic 校验 token；用户/角色/菜单/权限数据不本地存储，按需调 comm_public_basic 接口获取 |
| ADR-0003 | 传承机制 | 保持不变（分层文档仍有效） |
| STATUS.md | 待办 | 改：①搭单模块三层骨架 ②建资产核心表+Flyway ③业务前端脚手架+token对接 ④接入 comm_public_basic 鉴权(含钉钉) ⑤参考 Ciyo 提炼实体模型 |
| ENGINEERING.md | R1 依赖方向 | 改为三层依赖约束（controller→service→mapper，禁止跨层），删除 DDD domain 零依赖条款 |
| ENGINEERING.md | R5 License | 补充：comm_public_basic 为公司私有可复用，但只 HTTP 调用，不得引入工程依赖 |
| ENGINEERING.md | P1-P4 | 删除 DDD 分层/聚合根/端口条款，改为三层 + 包按上下文 + 跨上下文只 ID 引用 |
| AGENTS.md | 代码组织速览 | 改为单模块三层结构 + comm_public_basic 鉴权接入说明 |

---

## 四、待用户决策的关键问题

### D1. asset 后端 Spring Boot 版本（决定鉴权复用方式）

- **选项 A：Spring Boot 2.7.x（与 comm_public_basic 同构）**
  - 优势：`javax.*` 一致，理论上可复用 comm_public_basic 的工具类；团队 produce 也是 2.7 风格，栈统一
  - 劣势：2.7 已停止 OSS 维护（2023 年底），新特性缺失；Ciyo 3.4 参考需降级对照
- **选项 B：Spring Boot 3.x（与 Ciyo 对齐，鉴权走 HTTP 解耦）**
  - 优势：长期演进、安全更新；Ciyo 参考直接可用
  - 劣势：与 comm_public_basic 异构，鉴权必须 HTTP 调用（每次请求一次 `/login/checkToken`，可加本地缓存）
- **推荐**：B（3.x + HTTP 解耦 + 本地 token 缓存）。理由：comm_public_basic 是"永不提交"私改库，强依赖它会锁死版本；HTTP 解耦让 asset 系统独立演进，token 校验结果短时缓存（30-60s）可摊薄性能开销。

### D2. 架构粒度降级确认

- **选项 A：单模块三层 + 包按上下文分包（推荐，chyinan 风格）**
- **选项 B：保留 Maven 多模块但降为 3 模块**（asset-web 启动 / asset-core 业务 / asset-common 通用）
- **推荐**：A。基础功能阶段单模块最快，包按上下文分包已满足"分门别类"，后期需要再拆模块。

### D3. 前端登录态获取方式（待 auth-center-frontend 确认）

- auth-center-frontend 未本地确认，两种可能接入方式：
  - **方式 1**：auth-center-frontend 登录后跳转 asset 前端，token 在 URL query 或 localStorage
  - **方式 2**：asset 前端嵌入 auth-center-frontend 的登录组件/iframe
- **建议**：本次审阅不阻塞，搭脚手架时先按"方式 1（URL 带 token 跳转）"实现，clone auth-center-frontend 后再校准

---

## 五、不阻塞可实施的部分（保留）

以下条款经审阅仍成立，无需调整：

- 工程红线 R2（提交前编译测试）、R3（密钥配置隔离）、R4（提交纪律）、R6（生产库只读）、R7（传承不可破坏）
- 传承机制（AGENTS.md + STATUS.md + ADR + ENGINEERING.md 分层）
- 命名约定 N1、异常 N2、日志 N3、注释 N4
- 测试策略 Q1（按层测）、CI 门槛 Q2
- 数据迁移 Flyway、分支与发布
- 参考项目调研结论（Ciyo 业务模型参考、chyinan 工程规范参考）

---

## 六、审阅后行动清单（待用户确认 D1/D2 后执行）

1. 修订 ADR-0001（技术栈 + 鉴权方式）
2. 新增 ADR-0004（鉴权复用 comm_public_basic，含钉钉）
3. 修订 ADR-0002（架构降级为单模块三层）或标记 Superseded by ADR-0005
4. 修订 ENGINEERING.md（R1/R5/P1-P4 调整）
5. 修订 AGENTS.md / STATUS.md（结构 + 待办更新）
6. （可选）clone auth-center-frontend 确认前端接入方式
