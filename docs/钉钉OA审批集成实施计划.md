# 钉钉 OA 审批集成 —— 实施计划（P0–P5）

> 版本：v1.0（2026-08-28）
> 前置：已确认决策见《钉钉OA审批集成方案.md》——**双入口**（系统内发起为主 + 钉钉原生表单发起补充）、归还/退库保留免审批、离职自动清算。
> 本文档是可直接执行的工程任务清单：每阶段给出目标、接口契约、类设计、任务勾选、验收标准。

---

## 阶段总览

```
P0 前置准备（钉钉后台配置）      ──▶ 配置清单 + process_code
P1 钉钉基建（token缓存/Client/Stream）──▶ 事件能收到
P2 发起联动（入口A：系统内发起推钉钉）──▶ 系统发起→钉钉审批
P3 回调落地（入口A finish + 入口B start/finish + 对账）──▶ 审批结果→资产联动
P4 通知外发（工作通知 + 审批待办）  ──▶ 钉钉消息闭环
P5 离职联动（user_leave_org 清算）  ──▶ 离职自动退库
（P6 可选：钉钉 H5 微应用移动端）
```

---

## P0 前置准备（需钉钉管理员配合）

### 目标
钉钉侧一切就绪：应用、权限、审批模板、Stream 事件订阅。

### 任务清单
- [ ] **确认/创建企业内部应用 `asset-oa`**（独立应用，与 comm_public_basic 登录应用分离）：
  - 记录 `appKey / appSecret / agentId / corpId`
  - 配置服务器出口 IP 白名单（如有）
- [ ] **申请接口权限**（开发者后台 → 权限管理）：
  - 企业调用接口执行审批操作的权限（processinstance/create/get/terminate）
  - 审批流数据管理权限
  - 成员信息读权限（用户 userid 反查，用于 dd_user_id 映射兜底）
  - 工作通知（asyncsend_v2，默认已含）
  - 待办应用中待办写权限（审批待办提醒，可选）
  - 通讯录读权限（user_leave_org 离职事件）
- [ ] **创建审批模板**（管理后台 → 审批，或代码 `process/template/save`）并记录 `process_code`：

| 模板 | 表单字段 | 审批人 |
|---|---|---|
| 领用申请 | 资产编号、资产名称、领用区域、事由 | 入口A：系统 approvers_v2 传入；入口B：模板配置 |
| 借用申请 | 资产编号、资产名称、借用区域、事由、预计归还日期 | 同上 |
| 调拨申请 | 明细表格[资产编号]×N、调出区域、调入区域、目标使用人、事由 | 同上 |
| 离职退库确认单 | 离职员工工号、姓名、确认说明 | HR + 资产管理员 |

- [ ] **启用 Stream 事件订阅**：应用 → 事件与回调 → 勾选「启用 Stream 模式」→ 订阅事件：
  - 审批事件 `bpms_instance_change`
  - 通讯录用户变更 `user_leave_org`
- [ ] **理清 comm_public_basic 两套凭据**（`DingTalkController.java` L54 硬编码 vs `application-dev.yml` L42）：确认登录应用使用的凭据与 corpId，`sys_user.dd_user_id` 绑定数据质量（离职/未绑定占比）
- [ ] **asset-backend 配置项**（`application.yml` + 本地/生产 profile）：

```yaml
app:
  dingtalk:
    app-key: ${DINGTALK_APP_KEY:}
    app-secret: ${DINGTALK_APP_SECRET:}
    agent-id: ${DINGTALK_AGENT_ID:}
    corp-id: ${DINGTALK_CORP_ID:}
    process-codes:                    # 各业务模板 process_code
      receive: PROC-XXX
      borrow: PROC-XXX
      transfer: PROC-XXX
      leave-return: PROC-XXX
    notify-enabled: true              # 钉钉工作通知开关
    todo-enabled: false               # 审批待办开关（依赖待办权限，可后开）
    stream:                           # Stream 长连接
      enabled: true
    auth-center-base-url: ${AUTH_CENTER_BASE_URL:}   # 调 comm_public_basic 反查 dd_user_id
```

### 验收
- [ ] 钉钉后台能看到 `asset-oa` 应用、4 个模板、Stream 订阅状态正常
- [ ] asset-backend 配置齐全（P1 起才校验连通性）

---

## P1 钉钉基建（asset-backend 独立闭环）

包路径：`com.sk.asset.dingtalk`（新分包，仿 `com.sk.asset.auth` 的端口化风格）

### 类设计

| 类 | 说明 | 关键方法 |
|---|---|---|
| `config/DingtalkProperties` | `@ConfigurationProperties("app.dingtalk")` 绑定 P0 配置 | — |
| `client/DingTalkTokenClient` | access_token 获取 + **Redis 缓存**（提前 5 分钟刷新，参照 dashboard-server dingtalk-token.js 思路） | `String getAccessToken()` |
| `client/DingTalkApiClient` | topapi 统一调用器（参照 comm_public_basic `DingTalkApiClient.postToOldApiWithUrlToken` L55-79） | `createProcessInstance(...)`、`getProcessInstance(id)`、`terminateProcessInstance(id, operator, remark)`、`sendWorkNotification(...)` |
| `stream/DingtalkStreamBootstrap` | Stream 客户端生命周期（`dingtalk-stream` SDK，@Bean initMethod=start）；`registerAllEventListener` 按 `event.getEventType()` 分发到处理器 | `start()` |
| `stream/ApprovalEventProcessor` | 处理 `bpms_instance_change`：start→P3 入口B建单；finish/terminate→P3 回调落地 | `onInstanceChange(JSONObject data)` |
| `stream/LeaveOrgEventProcessor` | 处理 `user_leave_org`：userId[] → P5 清算 | `onLeaveOrg(JSONObject data)` |
| `port/DingTalkUserPort` | 内部 userId ↔ 钉钉 dd_user_id 双向映射（HTTP 调 comm_public_basic `GET /user/dingTalk/{internalUserId}` 兜底；发起时优先快照） | `String ddUserIdByInternal(Long userId)` |

### 接口契约（钉钉官方，已核验）

**① 应用凭证**
```
POST https://api.dingtalk.com/v1.0/oauth2/accessToken
body: {"appKey":"...","appSecret":"..."}
resp: {"accessToken":"xxx","expireIn":7200}
```

**② 发起审批实例**
```
POST https://oapi.dingtalk.com/topapi/processinstance/create?access_token=xxx
body: {
  "agent_id": 123,
  "process_code": "PROC-XXX",
  "originator_user_id": "manager432",
  "dept_id": 100,                       // 发起人部门id，根部门-1
  "approvers_v2": [                     // 多节点按序审批；入口A由系统审批链驱动
    {"user_ids":["dd_user_a"], "task_action_type":"NONE"},   // 部门主管
    {"user_ids":["dd_user_b"], "task_action_type":"NONE"}    // 仓管
  ],
  "form_component_values": [
    {"name":"资产编号","value":"SKSCDM-20260827-0001"},
    {"name":"领用区域","value":"A栋3F研发区"},
    {"name":"事由","value":"新员工入职配备"}
  ]
}
resp: {"errcode":0,"errmsg":"ok","process_instance_id":"xxx"}
```

**③ 审批实例详情**
```
POST https://oapi.dingtalk.com/topapi/processinstance/get?access_token=xxx
body: {"process_instance_id":"xxx"}
resp: {"process_instance":{
        "title":"...","status":"COMPLETED","result":"agree",
        "originator_userid":"manager432",
        "form_component_values":[{"name":"资产编号","value":"..."}],
        "operation_records":[...]}}
// status: RUNNING/COMPLETED/TERMINATED；result: agree/refuse
```

**④ 撤销审批实例**（入口B资产不可用时自动拦截）
```
POST https://oapi.dingtalk.com/topapi/processinstance/terminate?access_token=xxx
body: {"process_instance_id":"xxx","operating_userid":"manager432","remark":"资产已被占用"}
resp: {"errcode":0}
```

**⑤ 工作通知（OA 消息）**
```
POST https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2?access_token=xxx
body: {"agent_id":123,"userid_list":"dd_user_a",
      "msg":{"msgtype":"oa","oa":{
        "message_url":"https://asset.example.com/assets/1",
        "head":{"bgcolor":"FF00B2FF","text":"资产领用审批结果"},
        "body":{"title":"审批已通过","form":[{"key":"单号:","value":"ARE20260828-0001"}]}}}}
resp: {"errcode":0,"task_id":123}
```

**⑥ 审批待办提醒**（可选，P4；新版 TODO 用 unionId）
```
POST https://oapi.dingtalk.com/topapi/process/workrecord/task/create?access_token=xxx   // 自有审批待办（基于 process_instance_id）
```

### 事件 JSON 样例（固化为契约，用于集成测试）

```
// bpms_instance_change (Stream data)
{
  "EventType":"bpms_instance_change",
  "processInstanceId":"ad253df6-...",
  "corpId":"dingxxx",
  "createTime":1495592259000,
  "title":"张三提交的领用申请",
  "type":"finish",                  // start | finish | terminate
  "staffId":"er5875",
  "url":"https://aflow.dingtalk.com/...",
  "processCode":"PROC-XXX",
  "result":"agree",                 // 仅 finish 有
  "finishTime":1495592859000
}

// user_leave_org
{
  "EventType":"user_leave_org",
  "EventTime":1663143335567,
  "CorpId":"dingxxx",
  "BizId":"1663143335567",
  "eventId":"c7c7120f...",
  "timeStamp":"1685501863357",
  "userId":["015xxxx227"]
}
```

### 任务清单
- [ ] 新增 `com.sk.asset.dingtalk` 包 + `DingtalkProperties`
- [ ] `DingTalkTokenClient`：access_token Redis 缓存 + 提前刷新 + 失败退避
- [ ] `DingTalkApiClient`：create/get/terminate/worknotification 封装（RestTemplate，超时/错误码处理）
- [ ] `DingtalkStreamBootstrap`：Stream 启动 + 事件分发 + 心跳日志
- [ ] `ApprovalEventProcessor` / `LeaveOrgEventProcessor` 骨架（先打日志）
- [ ] `DingTalkUserPort`：dd_user_id 反查（调 comm_public_basic）

### 验收
- [ ] 启动后日志显示 Stream 连接建立，钉钉后台 Stream 状态在线
- [ ] 在钉钉发一条测试审批，系统日志收到 `bpms_instance_change` 事件（start+finish）
- [ ] access_token 二次调用命中缓存不重新请求

---

## P2 发起联动（入口 A：系统内发起 → 钉钉审批）

### 数据模型（Flyway）
`src/main/resources/db/migration/V20260834__dingtalk_approval.sql`

```sql
CREATE TABLE approval_instance (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  biz_type            VARCHAR(20)  NOT NULL COMMENT 'RECEIVE/BORROW/TRANSFER/CHANGE/LEAVE_RETURN',
  biz_id              BIGINT       NOT NULL COMMENT '业务单据 id',
  process_instance_id VARCHAR(64)  NOT NULL COMMENT '钉钉审批实例 id',
  process_code        VARCHAR(64)  NOT NULL COMMENT '钉钉审批模板唯一码',
  title               VARCHAR(255),
  originator_dd_user_id VARCHAR(64),
  status              VARCHAR(20)  COMMENT 'RUNNING/COMPLETED/TERMINATED',
  result              VARCHAR(20)  COMMENT 'agree/refuse',
  callbacks           JSON         COMMENT '事件回调明细(审计)',
  created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_instance (process_instance_id),
  KEY idx_biz (biz_type, biz_id)
);

ALTER TABLE receive_receipt ADD COLUMN dingtalk_instance_id VARCHAR(64) NULL AFTER status;
ALTER TABLE transfer_order   ADD COLUMN dingtalk_instance_id VARCHAR(64) NULL AFTER status;
ALTER TABLE change_order     ADD COLUMN dingtalk_instance_id VARCHAR(64) NULL AFTER status;
```

### 类设计

| 类 | 关键方法 |
|---|---|
| `entity/ApprovalInstance` + `mapper/ApprovalInstanceMapper` | 标准 MyBatis Plus |
| `service/ApprovalSyncService`（impl） | `String createDingtalkInstance(String bizType, Long bizId, Long originatorUserId, List<Long> approverUserIds, List<FormField> form)` — 调 ApiClient → 写映射表 → 回填单据 `dingtalk_instance_id` |
| `service/DingtalkFormAssembler` | 单据 → 钉钉表单字段（资产编号/区域/事由/明细表格 JSON） |

### 改造点（现有代码，最小侵入）

| 文件 | 改动 |
|---|---|
| `ReceiveReceiptServiceImpl.create()`（L89，审批链解析后） | 事务内调 `ApprovalSyncService.createDingtalkInstance`；两级链 userId→dd_user_id（DingTalkUserPort/快照）；失败整体回滚 |
| `TransferOrderServiceImpl.create()`（L83） | 同上（assignee 或强制指定处理人，见下） |
| `ChangeOrderServiceImpl.create()`（L89） | 同上 |
| `ApprovalChainResolver` | 无改动（仍是 userId 级） |

> **共享池单据处理**（决策已定：双入口）：
> - 有 `assignee_user_id` 的单据 → 正常推钉钉；
> - 无 assignee（共享池）的单据 → **保持站内审批**（不推钉钉），审批中心照常站内处理；`dingtalk_instance_id` 置 NULL 表示未上钉钉。两种模式按单并存，前端按字段区分展示。

### 前端改动
- [ ] `ApprovalCenter.vue`：单据 `dingtalk_instance_id` 非空 → 隐藏行内「通过/拒绝」按钮，显示「已在钉钉审批」标签 + 钉钉链接（事件 `url` 或模板配置的审批链接）
- [ ] 发起弹窗（`ReceiptApplyModal/TransferApplyModal/ChangeApplyModal`）：无感知；提交失败文案改为「钉钉审批创建失败，请重试」

### 验收
- [ ] 系统发起领用单 → 钉钉审批人收到原生审批任务，表单值与单据一致
- [ ] 两级审批人在钉钉按序审批
- [ ] 无 assignee 的单据仍走站内审批，未创建钉钉实例
- [ ] 审批人未绑定钉钉 → 发起被阻断并提示

---

## P3 回调落地（核心联动）

### 入口 A：finish 落地

| 类 | 方法 | 逻辑 |
|---|---|---|
| `service/ApprovalCallbackService`（impl） | `handleInstanceFinish(String processInstanceId, JSONObject event)` | ① 查 `approval_instance`（不存在→记日志忽略）② 调 `processinstance/get` 校验 result ③ **幂等**：instance 状态机（已 COMPLETED 不再执行）+ 单据状态二次校验 ④ agree→复用 `ReceiveReceiptServiceImpl.approve(...)` / `TransferOrderServiceImpl.confirm(...)` / `ChangeOrderServiceImpl.confirm(...)`；refuse/terminate→复用 `reject(...)` ⑤ 更新映射表 status/result/callbacks |

**幂等关键点**：`approval_instance.process_instance_id` 唯一键 + 单据 status 校验（PENDING 才执行）——重复事件自然跳过。

### 入口 B：start 建单锁资产

| 类 | 方法 | 逻辑 |
|---|---|---|
| `ApprovalCallbackService` | `handleInstanceStart(String processInstanceId, JSONObject event)` | ① `processinstance/get` 取表单值 ② `DingtalkFormAssembler` 解析（资产编号/区域/事由；调拨为明细表格）③ 资产校验：存在/未报废/无在途单占用 ④ 成功→事务建单（status=PENDING）+ 资产 PENDING_CONFIRM + 写 approval_instance ⑤ 失败→`terminateProcessInstance` 自动撤销 + 工作通知发起人说明原因 + 映射表记 `REJECTED_BY_SYSTEM` |

> 审批人：入口 B 由钉钉模板配置，系统不干预（映射表只记实例）。

### 超时对账（可靠性兜底）
- [ ] 定时任务（如每 10 分钟）：扫 `approval_instance.status=RUNNING` 且 `updated_at` 超 24h（可配）→ 调 `processinstance/get` 补状态；仍 RUNNING 的告警系统管理员

### 任务清单
- [ ] `ApprovalCallbackService`：handleInstanceStart / handleInstanceFinish + 幂等
- [ ] `ApprovalEventProcessor` 接线：type=start→handleInstanceStart；finish/terminate→handleInstanceFinish
- [ ] 对账定时任务
- [ ] 前端：审批中心/资产详情展示钉钉审批状态与链接

### 验收
- [ ] 入口 A：钉钉同意→资产变 IN_USE+持有记录+通知申请人；拒绝→资产回 IDLE
- [ ] 入口 B：钉钉发起领用→start 即建单锁资产；审批同意→资产落实 IN_USE
- [ ] 入口 B：表单资产不可用→钉钉审批被系统自动撤销，发起人收到原因通知
- [ ] 重复推送 finish 事件→只执行一次
- [ ] 回调时资产已被他单占用→409 拦截，不破坏状态机，告警管理员

---

## P4 通知外发（工作通知 + 待办提醒）

### 改造点

| 位置 | 改动 |
|---|---|
| `NotificationServiceImpl.notify()`（L36，唯一扩展点） | 追加异步钉钉外发：`DingTalkNotifier`（`@Async`，失败仅记日志不阻塞站内） |
| 新类 `notification/DingTalkNotifier` | 审批结果 → OA 消息（asyncsend_v2，message_url 深链单据详情）；待办提醒 → `workrecord/task/create`（todo-enabled 开启时） |
| 消息模板 | 「审批已通过/被拒绝」「您有一条待审批：领用申请」「资产不可用，审批已撤销」 |

### 权限注意
- `asyncsend_v2` 明确**不适合作审批任务提醒**（官方文档），待办类提醒必须用创建待办接口（P4 可先只做结果通知，待办后开）

### 验收
- [ ] 审批通过/拒绝后，申请人收到钉钉工作通知，点击跳到单据/资产详情
- [ ] 审批人收到审批待办（todo-enabled=true 时），在钉钉待办点击直达审批
- [ ] 站内通知与钉钉通知并存，互不影响

---

## P5 离职联动（自动清算）

### 逻辑

```
user_leave_org 事件（userId[] = 钉钉 userid）
  → LeaveOrgEventProcessor
  → 逐个 userId：DingTalkUserPort 映射内部用户
  → 清算服务 UserAssetClearService.clear(userId)（复用《资产台账OA完整解决方案》§6.2）：
      ├─ 校验：无 PENDING 单据（有则标记待处理，通知管理员）
      ├─ 名下持有中资产批量归还（闭环持有 + 资产回 IDLE，复用归还通道）
      ├─ 该用户为 assignee 的 PENDING 单提示改派
      └─ 输出清算报告 + 通知 HR/资产管理员
```

### 兜底
- [ ] 「离职退库确认单」审批模板：HR 发起 → finish(agree) → `ApprovalCallbackService` 定位 `biz_type=LEAVE_RETURN` → 触发同一清算逻辑（幂等：已清算则跳过）

### 新增
- [ ] `service/UserAssetClearService`（impl）：清算主逻辑 + 清算报告表（或复用 asset_log）
- [ ] `LeaveOrgEventProcessor` 接线 + 幂等（eventId 去重）

### 验收
- [ ] 钉钉删除员工 → 系统自动把其名下持有资产全部回 IDLE，持有记录闭环，通知到位
- [ ] 有在途单据的员工离职 → 不静默清算，提示改派
- [ ] 离职退库确认单审批通过 → 二次清算幂等安全

---

## 测试策略

| 层级 | 内容 |
|---|---|
| 单元 | DingTalkTokenClient 缓存、ApprovalSyncService（mock ApiClient）、幂等/状态机校验、表单装配（含明细表格 JSON） |
| 集成 | 本地启动 + 钉钉 Stream 连真实沙箱；固化 P1 事件 JSON 样例做契约测试；重复事件/资产占用/未绑定等负向用例 |
| 手工联调 | 双入口全流程：发起→钉钉审批→系统联动→通知；DingTalkDebugTool 辅助免登调试 |

## 关键依赖 / 阻塞项
1. **钉钉管理员**：应用创建、权限审批、模板、Stream 订阅（P0，阻塞 P1-P3）
2. **公网出网**：Stream 需应用环境能访问公网（仅出站）
3. **comm_public_basic 不动**：dd_user_id 反查走 HTTP（DingTalkUserPort），不入依赖
4. **凭据安全**：appKey/appSecret 走环境变量，禁止入库提交
