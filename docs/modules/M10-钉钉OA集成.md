# M10 — 钉钉 OA 审批双向集成（模块设计）

> 状态：设计稿（未编码）。决策依据：[ADR-0007](../adr/0007-钉钉OA审批双向集成.md)。
> 目标环境：先在实验公司（瀚海长航有限公司，单人）验证，后经配置切换到正式生产公司。
> 最后更新：2026-08-31

## 1. 目标与范围

| 流向 | 场景 | 说明 |
|---|---|---|
| A. 系统 → 钉钉 | 系统内提交单据（领用/借用/调拨/退库/实物变更）→ 自动创建钉钉 OA 审批 + 抄送 | 审批人 = 系统审批链快照映射的钉钉 userId |
| B. 钉钉 → 系统 | 在钉钉上完成审批（同意/拒绝/撤销）→ 实时回传推进系统单据状态机 | Stream 事件驱动，幂等 |
| C. 钉钉发起 | 用户在钉钉 OA 直接填表发起（如 IT 资产退还单）→ 系统自动建单/执行业务动作 | 表单按资产编码关联系统资产 |

不在本模块范围：钉钉消息通知（走现有通知中心，见 §9 联动）、扫码盘点、通讯录同步。

## 2. 前置条件（钉钉侧，实验公司操作清单）

1. **创建企业内部应用**（开放平台 → 应用开发）：拿到 `AppKey` / `AppSecret` / `AgentId`；
2. **申请接口权限**：`Workflow.Instance.Write`（发起/撤销审批）、`Workflow.Instance.Read`（实例详情）、`Workflow.Form.Write`（可选，程序化建模板）；
3. **事件订阅**：事件与回调 → 订阅方式选 **Stream 模式**；订阅事件 `bpms_task_change`（审批任务开始，结束，转交）与 `bpms_instance_change`（审批实例开始，结束，终止）；
4. **审批模板**：每个单据类型一个模板，两种创建方式任选：
   - OA 管理后台手工创建（模板编辑页 URL 中取 `processCode`，如 `PROC-EF6YJL35P2xxxx`）；
   - 程序化创建：`POST /v1.0/workflow/processCentres/forms`（权限 `Workflow.Form.Write`），**推荐**——实验公司调好后同一脚本在生产品公司重放，保证两环境模板一致；
5. **用户绑定**：`sys_user.dd_user_id` 完成绑定（comm_public_basic 已有绑定接口/管理入口；实验公司全员需绑定，否则该用户相关单据 OA 同步降级）。

> ⚠️ 钉钉**标准版**配额：OpenAPI 累计约 1 万次/月、事件推送约 5000 条/月。生产公司上线前必须核对其钉钉版本与配额。

## 3. 总体架构

```
┌─────────────── asset-backend（单实例） ───────────────────┐
│                                                            │
│  service/receipt/transfer/...        integration/dingtalk  │
│  ┌──────────────────────┐   afterCommit   ┌─────────────┐  │
│  │ ReceiveReceiptService │──────────────▶│ OaInstance  │  │
│  │ TransferOrderService  │   (outbox)     │ Service     │  │
│  │ AllocationService …   │                │  │ 同步创建  │  │
│  └─────────▲────────────┘                │  ▼          │  │
│            │ 状态机唯一漏斗                │ DingTalk    │  │
│            │ approve/reject/changeStatus │ Client ─────┼──▶ api.dingtalk.com
│            │                             │  (token+缓存)│     POST /v1.0/workflow/processInstances
│  ┌─────────┴────────────┐                │  ▲          │  │     GET  /v1.0/workflow/processInstances/{id}
│  │ OaEventDispatcher    │◀──────────────│  │ 实例详情   │  │
│  │ (Stream 监听入口)     │   路由/幂等     │ DingTalk    │  │
│  └──────────────────────┘                │ StreamClient┼──▶ WebSocket 长连接（免公网）
│                                          └─────────────┘  │    bpms_task_change / bpms_instance_change
│  映射：DingTalkUserIdMapper（user-directory 只读连接查 sys_user.dd_user_id + Caffeine 5min）│
└────────────────────────────────────────────────────────────┘
```

三条链路共用一套关联与幂等基座（`oa_instance` + `oa_event_log`），事件落地统一走现有 service 状态机方法，**钉钉审批只是入口之一，不旁路业务规则**（校验、快照、资产状态联动全部复用）。

## 4. 配置设计

`application.yml`（默认关闭，无敏感信息）：

```yaml
app:
  dingtalk:
    enabled: false                 # 总开关；false 时 OaInstanceService 全部 no-op
    # 以下全部不入库（R3），在 application-{env}.yml / 环境变量提供
    # app-key / app-secret / agent-id / corp-id
    api-base: https://api.dingtalk.com
    stream:
      enabled: true                # 事件订阅 Stream 模式
    sync:
      max-retry: 3                 # oa_instance 创建失败重试次数
    cache:
      user-map-ttl-seconds: 300    # comm userId -> dd_user_id 映射缓存
    process-codes:                 # 单据类型 -> 审批模板 processCode（实验/生产各一套，换环境只改这里）
      receive: ""                  # 领用（ARE）
      borrow: ""                   # 借用（BOR）
      transfer: ""                 # 调拨（ATR）
      return: ""                   # 退库/归还（参考 IT 资产退还单）
      change: ""                   # 实物信息变更（AOC）
```

**环境切换**：实验 → 生产 = 在生产环境配置中替换 `app-key/app-secret/agent-id/corp-id` + 五个 `processCode`，并在生产公司重放模板创建脚本。代码零改动（ADR-0007 D5）。

## 5. 数据模型（Flyway `V20260834__dingtalk_oa_integration.sql`）

```sql
-- 单据 ⇄ 钉钉审批实例 关联表（outbox）
CREATE TABLE oa_instance (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  biz_type             VARCHAR(32)  NOT NULL COMMENT 'RECEIVE/BORROW/TRANSFER/RETURN/CHANGE',
  biz_id               BIGINT       NOT NULL COMMENT '单据主键（receive_receipt/transfer_order/allocation/change_order.id）',
  serial_no            VARCHAR(32)  COMMENT '冗余单号，排查用',
  process_code         VARCHAR(64)  NOT NULL,
  process_instance_id  VARCHAR(64)  COMMENT '钉钉审批实例ID，创建成功后回填',
  sync_status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CREATED/FAILED/SKIPPED',
  sync_attempts        INT          NOT NULL DEFAULT 0,
  last_error           VARCHAR(500),
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_oa_biz (biz_type, biz_id),
  UNIQUE KEY uk_oa_instance (process_instance_id)
) COMMENT '钉钉OA审批实例关联（M10）';

-- 事件幂等 + 处理审计表
CREATE TABLE oa_event_log (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id             VARCHAR(64)  NOT NULL COMMENT '钉钉事件唯一ID，幂等键',
  event_type           VARCHAR(32)  NOT NULL COMMENT 'bpms_task_change / bpms_instance_change',
  process_instance_id  VARCHAR(64),
  process_status       VARCHAR(16)  NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED/PROCESSED/IGNORED/FAILED',
  process_error        VARCHAR(500),
  payload              JSON,
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_oa_event (event_id)
) COMMENT '钉钉事件日志（M10，幂等去重）';
```

不建钉钉用户映射表（直接查 `sys_user.dd_user_id`，见 ADR-0007 D3）。

## 6. 方向 A：系统 → 钉钉（自动创建 OA + 抄送）

### 6.1 触发点（事务提交后异步）

| 单据 | 触发方法 | 钉钉审批人 | 备注 |
|---|---|---|---|
| 领用/借用（ARE/BOR） | `ReceiveReceiptServiceImpl.create` afterCommit | 一级部门主管 → 二级领料仓管理员（快照；同人合并为一个节点） | 状态机终审通过前资产保持 PENDING_CONFIRM |
| 调拨（ATR） | `TransferOrderServiceImpl.create` afterCommit | 调入方负责人（`toUserId`；NULL=共享池时取 assignee，仍空则取调出位置管理员） | 复用现有"调入方确认"语义，钉钉 agree=COMPLETED、refuse=REJECTED |
| 实物变更（AOC） | `ChangeOrderServiceImpl.create` afterCommit | `assigneeUserId`（NULL=共享池 → 不指定审批人，改用钉钉待办通知，见 §9） | |
| 退库/归还 | 新增"退库申请"入口后接入（见 §8.4）；当前 `/allocations/{id}/return` 直执行不走 OA | 仓管理员 | 一期可不接（见 §11 分期） |

实现方式：`ApplicationEventPublisher` 发布 `OaSyncRequestedEvent(bizType, bizId)`，`OaInstanceService` 监听并处理；**业务事务不因钉钉失败回滚**（outbox 记录 FAILED + 重试 + 告警）。

### 6.2 创建实例（`DingTalkClient.createProcessInstance`）

`POST /v1.0/workflow/processInstances`，请求体要点（已按官方文档核实，编码时以实际为准）：

```jsonc
{
  "processCode": "PROC-xxxx",            // app.dingtalk.process-codes.{type}
  "originatorUserId": "020253xxx",       // 申请人 dd_user_id
  "microappAgentId": 123456,
  "approvers": [                          // 指定审批人，覆盖后台流程（ADR-0007 D2）
    { "actionType": "AND", "userIds": ["一级审批人ddUserId"] },
    { "actionType": "AND", "userIds": ["二级审批人ddUserId"] }
  ],
  "ccList": ["抄送人ddUserId"],           // 申请人 + 可配置抄送列表
  "ccPosition": "FINISH",                 // 审批结束时抄送
  "formComponentValues": [ /* §8 模板字段 */ ]
}
```

返回 `processInstanceId` 回填 `oa_instance`（`sync_status=CREATED`）。

### 6.3 失败与补偿

- 任一审批链用户**未绑定钉钉** → 该单据 OA 同步置 `SKIPPED` + 通知中心告警 systemAdmin（不阻塞系统内审批）；
- 钉钉 API 失败 → `FAILED` + `sync_attempts` 计数，定时任务重试（max-retry 3），仍失败告警；
- 已在系统内终态的单据重试时跳过（状态机守卫）。

## 7. 方向 B：钉钉 → 系统（审批结果实时回传）

### 7.1 事件接入（Stream 模式）

`DingTalkStreamClient` 建立 WebSocket 长连接，订阅两事件（后台"事件与回调"配置）：

| 事件 | 关注字段 | 处理 |
|---|---|---|
| `bpms_task_change` type=finish, result=**agree** | processInstanceId, staffId(审批人), remark, taskId | 按 §7.2 路由到对应单据 service 的 approve 逻辑推进（两级链：step1→step2→终态） |
| `bpms_task_change` type=finish, result=**refuse** | 同上 | 路由到 reject（remark=拒绝理由） |
| `bpms_task_change` result=redirect | staffId(转交人) | **仅记录日志**，不改变系统审批人快照（转交语义与快照冻结冲突，一期不支持，见 §10 风险） |
| `bpms_instance_change` type=finish | result=agree/refuse | 终态兜底：task 事件漏处理时由实例事件补（幂等，状态机守卫） |
| `bpms_instance_change` type=terminate | — | 发起人撤销 → 系统单据撤销（仅 PENDING 态允许，等价系统内撤销） |
| `bpms_instance_change` type=start | processCode, staffId | **方向 C 入口**（钉钉人工发起，见 §8） |

处理流程：`OaEventDispatcher` 收到事件 → ① `eventCorpId` 与配置 `corp-id` 匹配校验 → ② `event_id` 插 `oa_event_log`（唯一键冲突=重复事件，直接 ACK）→ ③ 按 `processCode` 反查 `oa_instance` 定位单据 → ④ 调用现有 service 方法落地 → ⑤ 更新 `process_status`。事件信息不足时回查实例详情接口补全。

### 7.2 单据路由与状态映射

| 钉钉动作 | 领用/借用（两级链） | 调拨 | 实物变更 |
|---|---|---|---|
| 一级 agree（step=1） | 推进 step=2（复用 approve 内部推进逻辑） | — | — |
| 末级 agree | approve 终态：资产 PENDING_CONFIRM→IN_USE、写持有关系 | COMPLETED（资产位置迁移） | CONFIRMED（执行目标值变更） |
| 任一级 refuse | REJECTED（释放占用） | REJECTED | CANCELLED |
| 发起人撤销 | 撤销单据（释放占用） | CANCELLED | CANCELLED |

**操作人语义**：以事件 `staffId` 反查 comm userId 作为 operator，写审批快照（`approval_step1_at/remark` 等），与系统内审批留痕一致。校验：staffId 必须等于当前 step 快照审批人，否则事件置 IGNORED + 告警（防越权，对齐 B4 规则）。

**双端并发冲突**：系统内与钉钉同时审批 → 状态机 + 单据状态守卫先到先得，后到幂等丢弃（记 `oa_event_log` IGNORED）。

## 8. 方向 C + 表单模板设计（钉钉侧表单细则）

> 参考正式公司现有「IT 资产退还单」的单据形态：申请人/部门/资产明细/原因/审批链。以下每类模板统一包含「单据编号」「来源」两个系统锚点字段。同一模板兼容两种发起方式：API 发起（系统填单，指定 approvers）与钉钉人工发起（复用 OA 后台流程）。

### 8.1 领用单（ARE）/ 借用单（BOR）

| 字段 | 控件 | 必填 | 说明 |
|---|---|---|---|
| 单据编号 | 单行文本 | 系统* | 系统单号（如 ARE202608310001）；钉钉发起留空，系统受理后回填评论 |
| 领用类型 | 单选：领用/借用 | 是 | 钉钉发起时选；系统发起自动填 |
| 资产编码 | 多行文本 | 是 | 一行一个（`asset.barcode`），系统受理时校验存在性/状态/持有人 |
| 领用部门 | 单行文本 | 是 | |
| 领用区域 | 单行文本 | 是 | 系统 `asset_location` 名称（受理时转 id，名称不存在则评论报错） |
| 事由 | 多行文本 | 是 | |

### 8.2 调拨单（ATR）

| 字段 | 控件 | 必填 | 说明 |
|---|---|---|---|
| 单据编号 | 单行文本 | 系统* | ATR 单号 |
| 资产编码 | 多行文本 | 是 | 一行一个 |
| 调出位置 | 单行文本 | 是 | 留空=取首台资产当前位置 |
| 调入位置 | 单行文本 | 是 | 受理时转 id |
| 调入部门 | 单行文本 | 是 | |
| 调拨原因 | 多行文本 | 是 | |

### 8.3 实物信息变更单（AOC）

| 字段 | 控件 | 必填 | 说明 |
|---|---|---|---|
| 单据编号 | 单行文本 | 系统* | AOC 单号 |
| 资产编码 | 多行文本 | 是 | 一行一个（一单 = N 台 + 一组统一新值） |
| 变更后使用人 | 单行文本 | 否 | 姓名（受理时反查 comm userId） |
| 变更后位置 | 单行文本 | 否 | |
| 变更后归属公司 | 单行文本 | 否 | |
| 变更原因 | 多行文本 | 是 | |

### 8.4 退库/归还单（对应「IT 资产退还单」）

> 现状：系统退库 = `POST /api/v1/allocations/{id}/return` 直接执行，无审批环。引入 OA 后新增"退库申请单"语义：钉钉发起 → 仓管理员审批 → agree 后系统执行 allocation 闭环（`returned_at`、资产 IN_USE→IDLE、清持有人、写日志，全部复用现有 `AllocationService` 逻辑）。

| 字段 | 控件 | 必填 | 说明 |
|---|---|---|---|
| 单据编号 | 单行文本 | 系统* | 系统受理后生成回填 |
| 资产编码 | 多行文本 | 是 | 一行一个；受理时校验发起人（或表单指定持有人）确为在持 |
| 退还原因 | 多行文本 | 是 | |
| 退还位置 | 单行文本 | 否 | 默认资产原位置/仓库 |
| 原持有人 | 内部联系人 | 否 | 钉钉发起默认发起人；管理员代退时选他人 |
| 备注 | 多行文本 | 否 | 写入 allocation note |

### 8.5 钉钉发起（方向 C）受理规则

`bpms_instance_change type=start` 事件 → 解析 `formComponentValues` → 逐项校验（资产存在、状态合法、持有人匹配、位置/人员可解析）→ 全部通过：创建系统单据（PENDING，绑定 `oa_instance`）→ 后续审批事件走 §7；**校验失败：不建单，通过审批评论接口（`oa-comments` 同款 OpenAPI）回写错误清单**，单据在钉钉侧由发起人自行撤销。

## 9. 与现有模块联动

- **通知中心（B2）**：OA 同步失败/降级 → SysNotification 告警 systemAdmin；钉钉 agree/refuse → 单据操作日志（asset_log）正常留痕（operator=审批人）；
- **审批链配置（B4）**：`ApprovalConfig` 不变——钉钉审批人完全来自系统审批链快照，配置入口仍只在系统内；
- **抄送**：`ccList` = 申请人（FINISH 时抄送）；额外抄送人（如资产管理员）经 `app.dingtalk.cc-user-ids` 配置扩展；
- **待办**：共享池单据（assignee 为空、不指定 approvers 的场景）改走钉钉**待办任务**（`topapi/process/workrecord/create`，创建待办非审批）+ 现有系统通知，不硬造审批人。

## 10. 包结构与分层（R1 合规）

```
com.sk.asset.integration.dingtalk/
├── DingTalkProperties.java          # @ConfigurationProperties("app.dingtalk")
├── DingTalkClient.java              # accessToken（缓存至过期前5min）+ createProcessInstance / getInstance / terminate / addComment
├── DingTalkUserIdMapper.java        # comm userId ↔ dd_user_id（user-directory 连接 + Caffeine）
├── DingTalkStreamClient.java        # Stream 长连接生命周期（enabled 时启动，失败重连退避）
├── OaEventDispatcher.java           # 事件入口：corpId 校验 / event_id 幂等 / processCode 路由
├── service/OaInstanceService(.Impl) # outbox：登记 / 创建 / 状态回写 / 重试 / 定时补偿
├── service/OaFormHandler.java       # 方向 C：表单解析 + 校验 + 建单（各 bizType 实现类）
├── entity/OaInstance.java, OaEventLog.java
├── mapper/OaInstanceMapper.java, OaEventLogMapper.java
└── dto/…                            # 钉钉 API 请求/响应 DTO（不外泄到 controller）
```

依赖方向：`service/{receipt,transfer,change}` → `integration/dingtalk`（仅接口）；`integration/dingtalk` → 现有业务 service（事件回传调用状态机方法，**循环依赖以事件/接口解耦**：dispatcher 经 `ApplicationEventPublisher` 或直接注入 service 接口，编码时以无循环为准）。

## 11. 实施分期

| 阶段 | 内容 | 验证标准 |
|---|---|---|
| M10-A | 配置基建 + `DingTalkClient`（token/发起/详情）+ 两张表 + 方向 A（领用单先行）+ 降级开关 | 实验公司：系统提交领用单 → 钉钉出现待审批，表单字段正确 |
| M10-B | Stream 事件接入 + 幂等 + 方向 B 回传（领用单两级审批推进/拒绝/撤销） | 钉钉点同意 → 系统单据状态实时推进、资产 IN_USE；双端并发无脏数据 |
| M10-C | 方向 C（退库单钉钉发起受理）+ 借用/调拨/变更模板接入 | 钉钉发起退库 → agree 后 allocation 闭环、资产 IDLE |
| M10-D | 抄送扩展、同步失败定时补偿、对账（实例状态 vs 单据状态一致性巡检） | 故障注入（拔网线/错 token）后恢复，数据最终一致 |

## 12. 测试策略

- 单测：`DingTalkClient` / `OaEventDispatcher` / `OaInstanceService` 全 mock 钉钉 API（Mockito），覆盖幂等（重复 eventId）、越权 staffId、未绑定用户降级、afterCommit 失败不回滚业务；
- 集成：WireMock 模拟钉钉端点；`oa_event_log` 幂等以 DB 唯一键实测；
- E2E：实验公司手工全链路（单人自审自批，两级同人合并场景天然覆盖）；生产切换前在新公司重放一遍 M10-A~C 验证清单。

## 13. 风险与待确认

| # | 风险/待确认 | 处置 |
|---|---|---|
| 1 | 钉钉标准版配额（API 1万次/月、事件 5000条/月） | 生产公司上线前核对钉钉版本，必要时升级付费版 |
| 2 | comm_public_basic **GitLab 原始版**是否具备 dd_user_id 绑定能力（本地私改版确认有） | 接入前按 AGENTS.md 约定核实原始版；缺失则生产侧补绑定管理入口 |
| 3 | 钉钉 Stream/服务端 SDK 的 License 与 Spring Boot 3/JDK17 兼容性 | 落地时核查（官方 SDK Apache-2.0，预计合规）；引入前过 R6 检查 |
| 4 | 转交（redirect）事件与审批人快照冻结冲突 | 一期不支持钉钉转交（仅记日志）；模板描述中注明"请勿转交" |
| 5 | 钉钉人工发起表单手填资产编码易错 | 受理时强校验 + 评论回执错误清单；后续可评估智能填单 |
| 6 | 生产服务器出网（api.dingtalk.com + Stream 网关）防火墙 | 上线前网络探测；Stream 断线自动重连（退避）+ 告警 |
| 7 | 两级审批事件时序（一级 agree 与实例 finish 竞态） | 状态机守卫 + 幂等，先到先得；实例 finish 仅作兜底 |
