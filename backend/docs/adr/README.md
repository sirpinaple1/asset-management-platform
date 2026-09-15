# 架构决策记录（ADR）

> ADR = Architecture Decision Record。记录项目中每一个重要架构/选型决策的**背景、决策、后果**。
> 不可篡改历史：已 Accepted 的 ADR 不得修改，只能新增 ADR 标记其被 Superseded。

## 为什么需要 ADR

- 多 Agent / 多人经手时，"为什么这么定"比"定了什么"更重要。
- 避免同一个问题被反复讨论、反复推翻。
- 新人/AI 接手时能快速理解决策脉络，而非面对一堆"既成事实"的代码。

## 何时写 ADR

- 技术选型（框架、库、数据库、迁移工具）
- 架构风格与分层决策
- 模块划分变更
- 红线条款的增删改
- 任何"为什么"无法从代码本身推断的设计

## ADR 模板

```markdown
# ADR-XXXX — 标题

- 状态：Proposed | Accepted | Deprecated | Superseded by ADR-YYYY
- 日期：YYYY-MM-DD
- 决策者：

## 背景
（为什么需要这个决策？面临什么问题/约束？）

## 决策
（选择了什么？具体方案。）

## 备选方案
（还考虑过什么？为什么没选？）

## 后果
（这个决策带来的正面/负面影响、约束、后续要做的。）
```

## 文件命名

`{序号4位}-{简短标题}.md`，如 `0001-技术栈选型.md`。序号连续递增，不复用。

## 索引

| ADR | 标题 | 状态 |
|---|---|---|
| [0001](./0001-技术栈选型.md) | 技术栈选型 | Accepted（版本由 0004 修订为 3.x） |
| [0002](./0002-DDD分层架构与Maven多模块.md) | DDD 分层架构与 Maven 多模块 | **Superseded by 0005** |
| [0003](./0003-多Agent上下文传承机制.md) | 多 Agent 上下文传承机制 | Accepted |
| [0004](./0004-鉴权复用comm_public_basic与SpringBoot3解耦.md) | 鉴权复用 comm_public_basic + Spring Boot 3.x + HTTP 解耦 + 钉钉 | Accepted |
| [0005](./0005-架构降级为单模块三层.md) | 架构降级为单模块三层（Superseded 0002） | Accepted |
| [0006](./0006-数据库核心表设计.md) | 数据库核心表设计 | Accepted |
| [0007](./0007-钉钉OA审批双向集成.md) | 钉钉 OA 审批双向集成（实验公司验证 → 生产切换） | Proposed |
