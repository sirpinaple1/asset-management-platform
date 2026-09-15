# 工程规范与设计原则（ENGINEERING.md）

> 本文件是资产管理项目的工程宪法。所有红线条款不可违背，设计原则必须遵守。
> 任何 Agent / 开发者接手前**必读**本文件 + [AGENTS.md](../AGENTS.md) + [STATUS.md](../STATUS.md)。
>
> **修订记录**：2026-08-18 经 REVIEW-001 审阅后修订，适配单模块三层架构（ADR-0005）与鉴权复用（ADR-0004）。原 DDD 条款（ADR-0002）已 Superseded。

---

## 0. 文档层级

| 文档 | 位置 | 职责 |
|---|---|---|
| AGENTS.md | 根目录 | AI/人入口，精简索引 + 快速上手 + 红线速查 |
| **ENGINEERING.md** | docs/ | **本文件，红线 + 设计原则主体** |
| STATUS.md | 根目录 | 当前进度、阶段、阻塞、下一步 |
| docs/adr/ | docs/adr/ | 架构决策记录（不可篡改历史，只追加） |
| docs/REVIEW-001-*.md | docs/ | 方案可实施性审阅记录 |

---

## 1. 工程红线（不可违背）

> 红线 = 违反即视为破坏工程基线，PR 一律打回；已发生的必须立即回退/修复。

### R1. 分层依赖不可逆向（架构红线）
- 三层依赖单向：`controller → service → mapper → entity`。
- `controller` 不得直接调用 `mapper`（禁止跨层）。
- `entity`（PO）不得外泄到 controller，必须经 `dto` 转换。
- `service` 之间避免循环依赖；跨上下文 service 调用不得双向。
- 违反：由 Code Review + 可选 ArchUnit 单测兜底，发现即改。

### R2. 提交前必须编译 + 测试通过（质量红线）
- 任何后端改动 `git commit` 前必须 `mvn clean compile -DskipTests` 通过。
- 核心业务逻辑改动必须附带单元测试且 `mvn test` 通过。
- 编译/测试未通过**禁止 push**，禁止 `--no-verify` 绕过。

### R3. 配置与密钥隔离（安全红线）
- `application.yml` 只放非敏感默认值；密码、token、第三方密钥、数据库连接串、comm_public_basic 地址一律走环境变量或 `application-local.yml`（已 gitignore）。
- **绝不入库**：`.env`、`application-local.yml`、密钥文件、`*.pem`、IDE 配置。
- 前端 `env.local`、本机调试地址不入库。

### R4. 提交纪律（仓库卫生红线）
- 只提交**业务功能/特性**相关改动。
- **不入库**：配置漂移、依赖锁漂移、本地调试兜底代码、注释掉的代码块、`println` 调试输出。
- 提交信息遵循 **Conventional Commits**：`feat|fix|refactor|test|docs|chore|build|ci(scope): 简述`，正文写"为什么"。
- 单次提交聚焦一个意图，禁止"大杂烩"提交。

### R5. License 与依赖合规（法律红线）
- 引入第三方代码/依赖前必须检查 License。
- **GPL/AGPL** 等强传染性协议的代码**不得引入闭源项目**；可参考业务逻辑/数据模型思路，但**不得复制源码**。当前参考项目：西柚 Ciyo（GPL-3.0，仅参考业务模型）、chyinan（无 License，仅参考工程实践）。
- **comm_public_basic 为公司私有代码**，无 License 问题，可深度复用——但受"永不提交/同步"红线约束，**只能 HTTP 调用，不得作为子模块/jar 依赖引入 asset 工程**。
- 新增依赖在 PR 描述中声明 License 类型。

### R6. 数据库写操作隔离（运行安全红线）
- 测试/本地库可自由读写。
- **生产库只读**（进入生产阶段后启用）：任何调试、造数据、DDL 只落在本地/测试库，绝不连生产写。
- 迁移脚本（Flyway）是生产库 schema 变更的唯一合法途径，且必须先在测试库验证。
- comm_public_basic 的库**只读观测**，绝不写（资产系统通过其接口交互，不直连其库）。

### R7. 不可破坏既有上下文传承（协作红线）
- 重大决策必须写 ADR（docs/adr/），不得口头约定。
- 每次阶段性进展必须更新 STATUS.md。
- 修改 ENGINEERING.md 红线条款必须新增 ADR 说明理由，不得静默修改。

### R8. 鉴权复用不可自建（架构红线，新增）
- **asset 系统不得自建登录/用户/角色/菜单/权限主数据**，统一复用 comm_public_basic（见 ADR-0004）。
- 登录、登出、token 校验、用户信息、权限查询走 comm_public_basic HTTP 接口。
- asset 在 comm_public_basic 的 `PortalProject` 注册为一个项目（`projectEn=asset`），权限由 comm_public_basic 统一配置。
- 钉钉登录复用 comm_public_basic `/login/dingTalk`，不自建钉钉后端逻辑。

---

## 2. 架构设计原则

### P1. 单模块三层架构（强制，见 ADR-0005）

```
asset-backend/                              # 单 Maven 模块
└── src/main/java/com/sk/asset/
    ├── AssetApplication.java              # 启动类
    ├── config/                            # Spring Security、Swagger、Redis、AuthPort 配置
    ├── common/                            # 异常、Result、常量、工具
    ├── auth/                              # 鉴权接入：AuthPort(HTTP 调 comm_public_basic)、TokenFilter、UserContext
    ├── controller/                        # REST 控制器（按上下文分包）
    ├── service/                           # Service + Impl（镜像 controller 包结构）
    ├── mapper/                            # MyBatis Plus Mapper（镜像包结构）
    ├── entity/                            # 数据实体 PO（对应表）
    ├── dto/                               # 请求/响应 DTO
    └── enums/                             # 枚举
```

依赖方向（R1 强制）：`controller → service → mapper → entity`；`dto` 用于接口层隔离 `entity`。

### P2. 高内聚低耦合（落地条款）

| 维度 | 要求 |
|---|---|
| 包内 | 同一限界上下文的 controller/service/mapper/entity 内聚在同一子包（如 `controller/asset/`、`service/asset/`） |
| 上下文间 | 限界上下文之间**只通过 ID 引用**，不直接对象引用（如 `Asset` 持有 `userId`，不持有 `User` 对象——User 数据来自 comm_public_basic） |
| service 间 | 跨上下文 service 调用须显式注入，不得双向循环依赖 |
| 鉴权 | 用户/权限数据不本地存储，通过 `auth/AuthPort` HTTP 调 comm_public_basic 获取 |
| 外部依赖 | comm_public_basic、OSS、短信等外部服务通过 `auth/` 或 `config/` 下的端口/客户端抽象，便于 mock 测试 |

### P3. 契约优先（接口设计）

- API 先写 OpenAPI 契约（controller 注解生成），契约评审通过后再实现。
- 统一响应结构（与 comm_public_basic `Result` 对齐，便于前端复用）：

```json
{ "code": 200, "message": "成功", "data": {} }
```

- 分页统一：`{ page, size, total, records }`。
- 版本化：URL 前缀 `/api/v1/`，破坏性变更升版本号。
- 鉴权头：所有业务请求带 `Authorization: Bearer <token>` + `systemCode: asset`。

### P4. 包按限界上下文分包（分门别类）

各层（controller/service/mapper/entity/dto/enums）内部按限界上下文镜像分包：

```
controller/asset/         # 资产上下文
controller/category/      # 分类
controller/location/      # 位置
controller/lifecycle/     # 生命周期（领用/归还/调拨/报废）
controller/inventory/     # 盘点
controller/depreciation/   # 折旧
controller/consumption/   # 耗材
```

service / mapper / entity / dto / enums 包结构与之镜像对称。

---

## 3. 代码规范

### N1. 命名约定

| 类型 | 约定 | 示例 |
|---|---|---|
| 实体 PO | 名词 + `Entity` 或名词 | `AssetEntity`, `Category` |
| DTO（出参） | `XxxDTO` / `XxxVO` | `AssetDTO` |
| 请求 DTO（入参） | `XxxReq` / `XxxQuery` | `CreateAssetReq`, `AssetPageQuery` |
| Service | `XxxService` + `XxxServiceImpl` | `AssetService`, `AssetServiceImpl` |
| Mapper | `XxxMapper` | `AssetMapper` |
| Controller | `XxxController` | `AssetController` |
| 枚举 | 名词 | `AssetStatus`, `DeviceStatus` |
| 鉴权端口 | `AuthPort` / `XxxClient` | `AuthPort`(HTTP 调 comm_public_basic) |

- 包名全小写，类名 PascalCase，方法/变量 camelCase，常量 UPPER_SNAKE。
- 布尔变量/方法用 `is/has/can` 前缀：`isAvailable()`。

### N2. 异常处理

- 业务异常抛 `BusinessException`（含错误码 + 消息），与 comm_public_basic `BusinessException` 语义对齐。
- 全局异常处理 `@RestControllerAdvice`，统一映射到响应结构。
- 鉴权失败（token 无效/过期）统一映射 401。
- 禁止 `catch (Exception e) {}` 空捕获；禁止用异常控制正常流程。

### N3. 日志规范

- SLF4J + Logback（Spring Boot 默认），禁止 `System.out.println`。
- 入口/出口/关键业务操作 INFO，异常 ERROR（含堆栈），细节调试 DEBUG。
- 敏感信息（密码、token、身份证）脱敏，禁止明文打印。

### N4. 注释与文档

- public API、复杂业务规则必须有 Javadoc 说明"为什么"。
- 不写"what"型注释（重复代码含义），只写"why"型注释。

---

## 4. 质量保障

### Q1. 测试策略

| 层 | 类型 | 工具 | 覆盖要求 |
|---|---|---|---|
| service | 单元测试（mock mapper） | JUnit5 + Mockito | 核心业务逻辑覆盖 |
| mapper | 集成测试 | Testcontainers (MySQL) | 核心 CRUD |
| controller | API 集成测试 | MockMvc | 核心接口 |
| auth | AuthPort 测试（mock HTTP） | Mockito | token 校验/缓存逻辑 |

### Q2. CI 门槛（PR 必过）

1. `mvn clean verify`（编译 + 单测 + 集成测试）
2. Conventional Commits 校验
3. 无新增 License 违规依赖

### Q3. Code Review

- PR 必须至少一人 review 通过方可合并。
- Review 清单：分层依赖、红线遵守、命名、异常处理、测试覆盖、契约一致性、鉴权复用（不得自建登录）。

---

## 5. 数据与迁移

- 迁移工具：**Flyway**，脚本 `src/main/resources/db/migration/V{yyyyMMdd}__{描述}.sql`。
- schema 与种子数据分离：`V{n}__schema_*.sql` 建表，`V{n}__seed_*.sql` 初始数据。
- 多环境配置：`application.yml`（默认）+ `application-{dev,test,prod}.yml` + `application-local.yml`（本地覆盖，gitignore）。
- **asset 库只存资产业务数据**，用户/角色/菜单/权限不入 asset 库（在 comm_public_basic 库）。

---

## 6. 分支与发布

- 初期（无生产）：trunk-based，`main` + 短命 `feature/*` 分支，PR 合并。
- 进入测试/生产后参照团队既有约定（prod/test/dev 分支语义），生产提交走 cherry-pick 精确提交，禁止整支 merge 到 prod。
- 版本号：SemVer（MAJOR.MINOR.PATCH），发布打 tag `v1.0.0`。

---

## 7. 多 Agent / 多人协作约定

1. 接手前必读：AGENTS.md → STATUS.md → ENGINEERING.md → 相关 ADR。
2. 重大决策必须新增 ADR，不得口头约定或散落在 commit message。
3. 完成阶段工作后更新 STATUS.md（状态、进度、下一步、阻塞）。
4. 遵守全部红线（第 1 节），不因"赶进度"绕过。
5. 提交前执行 R2（编译 + 测试），提交信息遵循 R4（Conventional Commits）。
6. 修改本文件红线条款必须新增 ADR 说明理由，不得静默修改。
7. 不确定时，在 STATUS.md 标注"待决策"并提示人类介入，不要擅自臆断。
8. **涉及鉴权/用户/权限时，先查 ADR-0004**，不得自建登录体系。
