# AGENTS.md — Agent / 开发者入口

> 本文件是任何 AI Agent 或开发者接手本项目时的第一份必读文件。
> 目标：用最短篇幅建立精简但完整的上下文，使任何人/AI 拿到代码即可延续进度。
>
> **修订记录**：2026-08-18 经 REVIEW-001 审阅后修订，适配单模块三层（ADR-0005）与鉴权复用（ADR-0004）。

## 项目简介

**资产管理系统**：自研替代 yideamobile 的企业资产管理系统。覆盖 IT 资产、固定资产、生产设备/工装/模具、耗材/配件四类，全生命周期管理（入库→领用→归还→调拨→盘点→维修→报废）+ 财务折旧 + 移动扫码盘点。
**鉴权复用** comm_public_basic（已部署）+ auth-center-frontend（已部署），不自建登录；钉钉登录复用 comm_public_basic `/login/dingTalk`。

## 技术栈

- 后端：**Spring Boot 3.x** + JDK 17 + MySQL 8+ + MyBatis Plus + **单模块三层**（controller/service/mapper，按上下文分包）
- 前端：Vue 3.5 + Vite + TypeScript + Element Plus + Pinia（业务前端，不含登录页）
- 鉴权：HTTP 调 comm_public_basic（Redis Token + 本地缓存 30-60s），不自建
- 迁移：Flyway | API 契约：OpenAPI | 测试：JUnit5 + Mockito + Testcontainers

## 必读文档（按顺序）

1. [STATUS.md](./STATUS.md) — 当前进度与下一步
2. [docs/ENGINEERING.md](./docs/ENGINEERING.md) — 工程红线 + 设计原则（**强制遵守**）
3. [docs/adr/](./docs/adr/) — 架构决策记录（了解为什么这么定）
4. [docs/REVIEW-001-方案可实施性审阅.md](./docs/REVIEW-001-方案可实施性审阅.md) — 方案审阅记录

## 工程红线速查（详见 ENGINEERING.md §1）

1. **分层依赖不可逆**：controller→service→mapper→entity；controller 不直调 mapper；entity 不外泄到 controller（经 dto）
2. **提交前必须编译 + 测试通过**：`mvn clean compile -DskipTests`
3. **密钥/配置不入库**：走环境变量或 `application-local.yml`（gitignore）；comm_public_basic 地址不入库
4. **只提交业务改动**：配置漂移、依赖锁漂移、本地调试代码不入库
5. **Conventional Commits**：`feat(scope): 描述`
6. **License 合规**：GPL/AGPL 不引入；参考项目只参考逻辑不抄代码；**comm_public_basic 只 HTTP 调用、不得引入依赖**（受"永不提交"红线约束）
7. **重大决策写 ADR**，进展更新 STATUS.md
8. **鉴权不可自建**：登录/用户/角色/菜单/权限复用 comm_public_basic，asset 库不存权限主数据（见 ADR-0004）

## 仓库结构

```
asset-backend/                    # 本仓库（后端）
├── AGENTS.md                     # 本文件
├── STATUS.md                     # 进度看板（主版本在此维护）
├── docs/
│   ├── ENGINEERING.md            # 工程宪法（红线+原则）
│   ├── REVIEW-001-*.md           # 方案审阅记录
│   ├── adr/                      # 架构决策记录（0001-0006）
│   └── modules/                  # 模块设计 spec（M01-M09 + M-FE01）
└── src/main/java/com/sk/asset/
    ├── config/                   # Spring Security、Swagger、AuthPort 配置
    ├── common/                   # 异常、Result、常量
    ├── auth/                     # 鉴权：AuthPort(HTTP 调 comm_public_basic)、TokenFilter
    ├── controller/{上下文}/       # REST 控制器
    ├── service/{上下文}/          # Service + Impl
    ├── mapper/{上下文}/           # MyBatis Plus Mapper
    ├── entity/{上下文}/           # PO
    ├── dto/{上下文}/              # 请求/响应 DTO
    └── enums/{上下文}/            # 枚举

# 关联仓库
前端：/Users/zhuanzmima0000/Documents/git/asset-frontend/
设计档案 + references：/Users/zhuanzmima0000/Documents/git/asset-management/
```

## 鉴权接入要点（见 ADR-0004）

- asset 后端不实现登录，通过 `auth/AuthPort` HTTP 调 comm_public_basic：
  - `POST /login/check`、`POST /login/dingTalk`、`POST /login/logout`、`GET /login/getAuth`、`GET /login/checkToken`、`POST /login/userSystemAuth`（请求头 `systemCode: asset`）
- token 校验：本地缓存（30-60s）+ 未命中回源 comm_public_basic
- asset 在 comm_public_basic `PortalProject` 注册（`projectEn=asset`）
- **comm_public_basic 接口契约以 GitLab 原始版为准**（本地为私改版），接入实现时核实
- 前端登录走 auth-center-frontend，登录后跳转 asset 前端带 token

## 接手约定（任何 Agent 接手前必做）

1. 读 AGENTS.md → STATUS.md → ENGINEERING.md → 最近 ADR（0004/0005 必读）
2. 确认当前阶段与下一步任务（STATUS.md）
3. 确认不违反红线（ENGINEERING.md §1）
4. 动手前若涉及架构/选型变更，先写 ADR 再编码
5. 涉及鉴权/用户/权限时，先查 ADR-0004，不得自建登录
6. 完成后更新 STATUS.md

## 当前状态

**阶段**：方案审阅完成，文档已按审阅意见修订，待搭建单模块三层骨架（详见 STATUS.md）
