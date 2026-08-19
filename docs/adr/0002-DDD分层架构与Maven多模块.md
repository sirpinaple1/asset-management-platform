# ADR-0002 — DDD 分层架构与 Maven 多模块

- 状态：Accepted
- 日期：2026-08-18
- 决策者：项目 Owner

## 背景

项目需"高可维护、可由多 Agent/多人承接"。传统三层（controller/service/repository）在复杂业务演进后易出现 service 膨胀、跨层调用、领域逻辑散落等问题。需选定一套能让边界清晰、依赖单向、领域知识集中的架构。

## 决策

采用 **DDD 分层架构 + Maven 多模块**：

```
asset-admin          启动装配
asset-interface      接口适配层（REST 控制器、OpenAPI、请求响应 DTO）
asset-application    应用层（用例编排、命令/查询、事务边界、端口定义）
asset-domain         领域层（聚合根、值对象、领域服务、领域事件、仓储接口）
asset-infrastructure 基础设施层（仓储实现、外部适配器、消息、存储）
asset-common         通用工具
```

依赖方向（单向，不可逆）：
- `interface → application → domain`
- `infrastructure → domain`（实现端口，依赖倒置）
- `admin → 所有`（装配）
- `domain` 零框架依赖（纯 POJO，无 Spring/JPA 注解）

模块内按限界上下文分包（asset/category/location/lifecycle/inventory/depreciation/consumption/shared），各层包结构镜像对称。

## 备选方案

- 传统三层：简单快上手，但领域逻辑易散落 service，长期可维护性差。未选。
- 混合（三层为主 + 局部 DDD）：规范需写两套落地条款，认知负担重。未选。
- 单模块分层包：初期简单，但后期模块边界靠包名约束弱，易被破坏。未选。
- 按资产类型拆领域模块（asset-it/fixed/equipment/consumable）：贴合四类资产，但初期过度设计、跨模块共享内核复杂。未选，改为模块内按上下文分包。

## 后果

- 正面：领域层零框架依赖，业务逻辑可独立测试、AI 承接时上下文集中；依赖方向可被 ArchUnit 自动校验；限界上下文边界清晰。
- 负面：模块与样板代码多，初期搭建成本高于单模块；团队需理解 DDD 概念（聚合根/值对象/端口）。
- 约束：domain 层禁止出现任何框架注解，由 Code Review + ArchUnit 兜底；跨上下文只通过 ID 引用。
- 后续：搭骨架时先建 6 模块 + ArchUnit 依赖校验测试，再填业务。
