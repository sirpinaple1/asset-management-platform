# 资产平台 × 钉钉 OA 审批深度集成方案

> 版本：v1.0（2026-08-28）
> 定位：将「领用 / 借用 / 调拨 / 归还 / 离职退库」审批搬到钉钉上完成，审批结果与资产系统**直接联动**（状态机 / 持有记录 / 通知闭环）。
> 前置事实：登录链路已打通（钉钉免登 → auth-center → asset），本文核心是**审批联动**。

---

## 0. 结论速览（TL;DR）

| 维度 | 结论 |
|---|---|
| 登录 | **已可用，零改动**。钉钉 JSAPI 免登 → comm_public_basic 换 Redis token → auth-center 跳转 asset，`sys_user.dd_user_id` 映射已存在 |
| 发起模式（推荐） | **系统内发起单据 → 自动创建钉钉审批实例**。员工在资产平台选资产发起，系统锁定资产（PENDING_CONFIRM）+ 解析审批链，再推送钉钉原生 OA 审批 |
| 审批处理 | 审批人在**钉钉原生审批应用**里点同意/拒绝，无需进系统 |
| 回调接收（推荐） | **Stream 模式**（WebSocket 长连接）。零公网 IP / 域名 / 加解密；`dingtalk-stream:1.1.0` 依赖已在 comm_public_basic 引入过，方案照搬 |
| 联动落地 | asset-backend 收到 `bpms_instance_change`(finish) 事件 → 查 `processinstance/get` 详情 → **复用现有 approve/reject 逻辑**落库 |
| 离职退库 | **通讯录离职事件 `user_leave_org` 自动清算**（复用 §6.2 规划清退接口）+ 「离职退库确认单」审批模板兜底 |
| 新增资产 | `approval_instance` 映射表 + 单据表 `dingtalk_instance_id` 列 + `app.dingtalk.*` 配置 + DingTalkClient/Stream 监听 |
| 依赖改动 | **asset-backend 独立闭环**，comm_public_basic 除登录外**不动**（符合"永不提交"红线） |

### 已确认决策（2026-08-28）

| 决策点 | 结论 |
|---|---|
| 发起入口 | **双入口**：系统内发起（主）+ 钉钉原生审批表单直接发起（补充）——详见 §4.1/§4.2 |
| 归还 / 退库 | **保留免审批**（一键操作），钉钉侧仅补工作通知提醒；离职退库走自动清算 + 兜底模板 |
| 下一步 | 细化实施计划（见《钉钉OA审批集成实施计划.md》） |

---

## 1. 现状盘点（代码级，已核实）

### 1.1 登录链路（已具备 ✅）

```
钉钉客户端 ──JSAPI requestAuthCode──▶ authCode
   │                                  │
   │                     auth-center-frontend: GET /api/dingtalk/getUserInfo?code=
   │                                  │
   │                     comm_public_basic: oapi topapi/v2/user/getuserinfo → userid
   │                                  │ → loginByDingTalk(userid) → Redis token(loginType=dingtalk)
   ▼                                  ▼
asset-frontend ←token in URL/localStorage── auth-center 跳转子系统
```

- `auth-center-frontend/src/components/auth/DingTalkLogin.tsx`：免登全流程 + 调试工具（普通浏览器可模拟钉钉环境联调）
- `comm_public_basic` `DingTalkController.java`：`/api/dingtalk/getUserInfo`（L69）+ `topapi/v2/user/getuserinfo`（L114）
- `sys_user.dd_user_id` 绑定关系已建（`UserController` 提供 bind/unbind/查询）
- asset-frontend 无自建登录，路由守卫从 URL 提取 token 存 `localStorage(asset_token)`

### 1.2 审批/通知（已具备 ✅，是联动的落点）

| 能力 | 实现 | 位置 |
|---|---|---|
| 领用/借用审批 | 两级审批链（部门主管→仓管），`receive_receipt` 表 + 快照列 | `ReceiveReceiptServiceImpl.create/approve/reject` |
| 调拨 | 调入方确认，`transfer_order` | `TransferOrderServiceImpl.create/confirm/reject` |
| 变更 | 允许发起人自审，`change_order` | `ChangeOrderServiceImpl.create/confirm` |
| 审批链配置 | `approval_config` 表 + `ApprovalChainResolver` | 前端 `ApprovalConfigModal.vue` |
| 站内通知 | `sys_notification`，`NotificationService.notify()` 单点入口 | 唯一对外扩展点 |
| 资产状态机 | `AssetService.changeStatus()` 硬校验 + 必写 `asset_log` | 联动落地必须复用 |

### 1.3 缺口（需新增）

- asset-backend **无钉钉配置/调用封装/事件接收/审批实例映射表**
- comm_public_basic：`DingTalkController.java` L54-55 **硬编码一套凭据**，与 `application-dev.yml` L42-45 另一套**并存不一致**（联调前必须理清）；access_token **无缓存**（每次现取，高频场景有频率限制风险）
- 参考仓库 `ciyo-itasset/.../DingTalkSendMsgHandle.java` 有完整 OA 工作通知实现（整类被注释，可作写法参考）

---

## 2. 目标架构

```
┌──────────────────────────── 钉钉侧 ────────────────────────────┐
│ 员工（钉钉内）                    审批人（钉钉内）               │
│  ① 打开资产 H5 / PC 网页发起       ④ 钉钉原生 OA 审批：         │
│     选资产+区域+事由               ✔同意 / ✘拒绝 / 撤销          │
│  ② 系统锁定资产+PENDING_CONFIRM      ▲                          │
└──────────────┬─────────────────────┼──────────────────────────┘
               │ ③ processinstance/create   │ ⑤ 事件回调(Stream)
               ▼                          ▼
┌─────────── asset-backend ──────────────────────────────────────┐
│  DingTalkClient(access_token 缓存)   DingTalkStreamListener     │
│  ApprovalSyncService                 └ bpms_instance_change     │
│   create: 建单→锁资产→推钉钉审批实例                              │
│   callback: processinstance/get 验果 → approve()/reject()       │
│   ── 复用 ── AssetService.changeStatus / asset_allocation /     │
│              NotificationService.notify (+钉钉外发)              │
└───────────┬─────────────────────────────────────────────────────┘
            │ ⑥ 工作通知/待办（审批结果、待我审批提醒）
            ▼
  钉钉工作通知 OA 消息（message_url 深链资产详情）
```

**架构原则**：
1. **系统是唯一事实源**：单据、资产占用、审批链解析全在系统内；钉钉只是「审批通道」。
2. **审批人由系统决定**：`approvers_v2` 显式传审批人（系统审批链配置驱动），钉钉模板只定义表单，不配审批人。
3. **联动逻辑放 asset-backend**，与 comm_public_basic 仅共享钉钉企业身份与 userid（userid 是企业维度全局的，与应用无关）；comm_public_basic 除登录外零改动。

---

## 3. 登录方案（已就绪，只需一步收尾）

1. **现状**：钉钉免登已全链路打通，asset 复用 auth-center 登录态，**不需要新写登录**。
2. **待补（可选）**：把 asset-frontend 打包为钉钉 **H5 微应用**（工作台图标直达），移动端进入即免登。移动端是 PC 布局，需要响应式适配（列为 Phase 6）。
3. **用户映射缺口**：asset 回调时需要「钉钉 userid → 内部 userId」。两种取法：
   - **推荐**：发起单据时把 `dd_user_id` **快照**进 `approval_instance` / 单据列，回调时直接查，零额外调用；
   - 兜底：新增 `DingTalkUserPort`（仿现有 `AuthPort`），调 comm_public_basic `GET /user/dingTalk/{internalUserId}` 反查。

---

## 4. 审批联动方案（核心）

### 4.1 主链路

#### 入口 A：系统内发起（主入口，推荐）

```
① 员工发起（系统页面选资产+区域+事由，前端不变）
   └─ ReceiveReceiptServiceImpl.create():
       ├─ [既有] 校验 / 锁资产 PENDING_CONFIRM / 两级审批链解析
       ├─ [新增] DingTalkClient.createProcessInstance(
       │           process_code=领用模板, originator=发起人ddUserId,
       │           approvers_v2=[{部门主管},{仓管}],     ← 系统审批链驱动
       │           form=[资产,区域,事由,...])
       │         → 返回 process_instance_id
       ├─ [新增] 写 approval_instance 映射表 + 单据 dingtalk_instance_id 列
       └─ [事务] 失败则整体回滚，前端提示"钉钉审批创建失败，请重试"
② 审批人收到钉钉审批任务（原生 OA 审批界面）
③ 钉钉 finish 事件（agree/refuse/terminate）→ Stream 推送
   └─ DingtalkApprovalCallbackHandler:
       ├─ 按 process_instance_id 查 approval_instance → 定位单据
       ├─ 调 processinstance/get 校验最终 result（防伪造/防重复）
       ├─ 幂等：process_instance_id+result 唯一约束 / 单据状态机
       └─ agree → 复用 ReceiveReceiptServiceImpl.approve(...)
          refuse/terminate → 复用 reject(...)（资产回 IDLE）
④ 通知：站内（既有）+ 钉钉工作通知 OA 消息（message_url → 单据/资产详情）
```

#### 入口 B：钉钉原生审批表单直接发起（补充入口）

```
① 员工在钉钉 OA 审批应用选「领用申请」模板填表发起（资产编号/名称、区域、事由…）
   └─ 钉钉推送 bpms_instance_change start 事件（type=start, staffId=发起人）
② 系统收到 start 事件 → ApprovalSyncService.acceptFromDingTalkStart():
       ├─ processinstance/get 取表单值，解析资产/区域/事由
       ├─ 建 receive_receipt 单据（status=PENDING）+ 资产 → PENDING_CONFIRM 锁定
       ├─ 写 approval_instance（biz_type=RECEIVE, biz_id=新单ID, status=RUNNING）
       └─ 资产不可用（不存在/被占用/报废）→
          调用 processinstance/terminate 撤销该审批实例
          + 工作通知发起人说明原因 + 映射记录 REJECTED_BY_SYSTEM
③ 审批人（本入口由钉钉模板配置的审批人）在钉钉原生审批界面审批
④ finish 事件 → 与入口 A 共用同一回调落地逻辑（§4.1 ③④）
```

> 入口 B 的关键：**start 事件即建单锁资产**，把"资产占用冲突"提前到审批流运行前暴露，避免审批通过了资产却不可用的死局。审批人由钉钉模板配置（补充入口可接受），系统审批链配置仍是入口 A 的唯一事实源。

### 4.2 审批链 → 钉钉节点映射

| 现有语义 | 钉钉表达 |
|---|---|
| 领用/借用两级（部门主管→仓管） | `approvers_v2` 传 2 个节点，`task_action_type=NONE`（单人），按序审批 |
| 两级同人合并 | 只传 1 个节点（保持系统既有合并逻辑） |
| 调拨/变更 assignee 指定 | `approvers_v2` 传 1 个节点 = assignee 的 dd_user_id |
| 共享池（assignee=NULL） | **决策点**：建议限制为「发起时必须指定处理人」后走钉钉；或共享池单据暂留站内审批 |

> 审批人必须是已绑定钉钉的账号（`dd_user_id` 非空），发起时校验，未绑定则提示先到 auth-center 绑定。

### 4.3 数据模型（Flyway 新增）

```sql
-- V2026xxxx__dingtalk_approval.sql
CREATE TABLE approval_instance (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  biz_type            VARCHAR(20)  NOT NULL COMMENT 'RECEIVE/BORROW/TRANSFER/CHANGE/LEAVE_RETURN',
  biz_id              BIGINT       NOT NULL COMMENT '业务单据 id',
  process_instance_id VARCHAR(64)  NOT NULL COMMENT '钉钉审批实例 id',
  process_code        VARCHAR(64)  NOT NULL COMMENT '钉钉审批模板唯一码',
  title               VARCHAR(255) COMMENT '审批标题',
  originator_dd_user_id VARCHAR(64) COMMENT '发起人钉钉 userid',
  status              VARCHAR(20)  COMMENT 'RUNNING/COMPLETED/TERMINATED',
  result              VARCHAR(20)  COMMENT 'agree/refuse',
  callbacks           JSON         COMMENT '事件回调明细(审计)',
  created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_instance (process_instance_id),
  KEY idx_biz (biz_type, biz_id)
) COMMENT '钉钉审批实例↔系统单据映射';

ALTER TABLE receive_receipt ADD COLUMN dingtalk_instance_id VARCHAR(64) NULL COMMENT '钉钉审批实例id' AFTER status;
ALTER TABLE transfer_order   ADD COLUMN dingtalk_instance_id VARCHAR(64) NULL COMMENT '钉钉审批实例id' AFTER status;
ALTER TABLE change_order     ADD COLUMN dingtalk_instance_id VARCHAR(64) NULL COMMENT '钉钉审批实例id' AFTER status;
```

### 4.4 事件接收：Stream 模式（推荐）

- 原理：应用通过 WebSocket 反向长连接与钉钉平台通讯，**无需公网 IP/域名/加解密/白名单**。
- 依赖：`com.dingtalk.open:dingtalk-stream`（comm_public_basic 已用 1.1.0，可同版本）。
- 参考代码骨架：

```java
@Configuration
public class DingtalkStreamConfig {
    @Bean(initMethod = "start")
    public OpenDingTalkStreamClient dingTalkStreamClient(DingtalkProperties p,
                                                         ApprovalEventProcessor processor) {
        return OpenDingTalkStreamClientBuilder.custom()
            .credential(new AuthClientCredential(p.getAppKey(), p.getAppSecret()))
            .registerAllEventListener(new GenericEventListener() {
                public EventAckStatus onEvent(GenericOpenDingTalkEvent event) {
                    try {
                        String topic = event.getTopic();        // bpms_instance_change / user_leave_org ...
                        processor.process(topic, event.getData());
                        return EventAckStatus.SUCCESS;          // 确认，重试语义
                    } catch (Exception e) { return EventAckStatus.LATER; } // 失败重推
                }
            })
            .build();
    }
}
```

- 备选 HTTP 回调（SyncHTTP）：需公网 URL + AES-CBC 加解密 + 验签 + 绕过 `ResponseConfig` 统一包装；仅在「公司已有稳定公网网关」时考虑。

### 4.5 可靠性设计

| 风险 | 对策 |
|---|---|
| 事件重复推送 | `approval_instance` 上 `process_instance_id` 唯一键 + 单据状态机二次校验（已 APPROVED 的单不再执行） |
| 事件丢失/处理失败 | Stream `EventAckStatus.LATER` 让钉钉重推；另加**兜底对账**：审批中心对 `RUNNING 超时` 的实例定时调 `processinstance/get` 补状态 |
| 回调时资产已被他单占用/报废 | 复用现有 `approve/reject` 状态机硬校验（409 回滚），异常落 `callbacks` + 告警通知系统管理员 |
| 钉钉创建审批失败 | 单据事务回滚，前端可重试；预留「管理员手动重推钉钉」按钮 |
| 审批人未绑钉钉 | 发起时校验 `dd_user_id`，未绑定则提示去 auth-center 绑定 |

---

## 5. 分业务场景落地

### 5.1 领用 / 借用（ARE/BOR）✅ 主链路
- 模板：`领用申请` / `借用申请`（字段：资产编号、资产名称、领用/借用区域、事由、预计归还日期）
- 审批人：两级链 → `approvers_v2`
- 通过 → `approve()`（资产 IN_USE + 建持有 RECEIVE/BORROW + 通知）；拒绝 → `reject()`（回 IDLE）

### 5.2 调拨（ATR）
- 模板：`调拨申请`（字段：调出区域、调入区域、目标使用人、事由）
- 审批人：assignee 或强制指定
- 通过 → `TransferOrderServiceImpl.confirm()` 走既有终态分发（人持有/部门持有/回库）

### 5.3 归还 / 退库（现状免审批）✅ 已决策：保留免审批 + 提醒
- **免审批**：归还/退库在系统（PC 或钉钉 H5）内一键执行，联动不变（复用 `allocations/{id}/return`）。
- **钉钉侧仅补工作通知提醒**：待归还资产提醒、归还完成回执。
- 不做「归还申请」审批模板。

### 5.4 离职退库（重点）
- **主通道（自动）**：订阅通讯录事件 `user_leave_org` → 事件带 `userId[]`（钉钉 userid）→ 映射内部用户 → 调 `POST /api/v1/users/{id}/clear`（复用《资产台账OA完整解决方案》§6.2 规划的清算逻辑：校验无 PENDING 单 → 名下持有资产批量归还回 IDLE → 输出清算报告）→ 通知 HR / 资产管理员。
- **兜底（人工）**：`离职退库确认单` 审批模板，HR 发起，审批通过后系统清算（幂等，`approval_instance.biz_type=LEAVE_RETURN`）。
- **防孤儿单**：该用户为 assignee 的 PENDING 单据提示改派。

### 5.5 通知外发（增强）
- 在 `NotificationService.notify()`（唯一扩展点）追加异步钉钉外发：
  - **审批结果**：工作通知 OA 消息 `topapi/message/corpconversation/asyncsend_v2`（head/body/message_url，参照 ciyo-itasset 注释实现）；
  - **审批待办提醒**：文档明确 `asyncsend_v2` 不适合作审批任务提醒，应使用**创建待办**接口（自有审批待办 `topapi/process/workrecord/task/create`，或新版 `POST /v1.0/todo/users/{unionId}/tasks`，注意新版用 unionId）。

---

## 6. 钉钉官方接口清单（已核验）

| 用途 | 接口 | 关键参数 |
|---|---|---|
| 应用凭证 | `POST /v1.0/oauth2/accessToken` | appKey, appSecret（返回 accessToken，2h 有效） |
| 发起审批实例 | `POST /topapi/processinstance/create` | process_code, originator_user_id, dept_id, **approvers_v2**(多级/会签/或签), form_component_values, agent_id |
| 审批实例详情 | `POST /topapi/processinstance/get` | process_instance_id → status(RUNNING/COMPLETED/TERMINATED), result(agree/refuse), form_component_values, operation_records |
| 工作通知 | `POST /topapi/message/corpconversation/asyncsend_v2` | agent_id, userid_list, msg{msgtype:oa, oa{head,body,message_url}} |
| 审批待办 | `POST /topapi/process/workrecord/task/create`（自有审批待办） | agentid, processInstanceId, userid |
| 待办任务(新版) | `POST /v1.0/todo/users/{unionId}/tasks` | unionId, subject, executorIds, detailUrl（需待办写权限） |
| 事件订阅 | Stream：`/v1.0/gateway/connections/open`（SDK 封装） | subscriptions: EVENT topic=`*` |
| 审批事件 | `bpms_instance_change` | type=start/finish/terminate；finish 时 result=agree/refuse；带 processInstanceId/processCode/staffId/url |
| 通讯录离职 | `user_leave_org` | userId[]（钉钉 userid 数组），企业内部应用支持 Stream 推送 |

> comm_public_basic 的 `DingTalkApiClient.postToOldApiWithUrlToken`（`/topapi/...` 通用调用器）是可直接照搬的封装范式；dashboard-server `dingtalk-token.js` 的「提前 5 分钟刷新」缓存范式也可借鉴。

---

## 7. 钉钉开发者后台配置清单（前置）

1. **应用**：确认/新建企业内部应用（建议独立 `asset-oa` 应用，与 comm_public_basic 登录应用分离，符合仓库红线）：
   - 记录 `appKey / appSecret / agentId / corpId`；
   - **权限申请**：成员信息读权限、企业调用接口执行审批操作的权限、审批流数据管理权限、工作通知、待办写权限、通讯录读权限。
2. **审批模板**（管理后台→审批，或代码 `process/template/save`）：
   - 领用申请 / 借用申请 / 调拨申请 /（归还申请）/ 离职退库确认单；
   - 从模板 URL 取 `process_code`；**表单字段按 §5 设计，审批人不要在模板里配（由系统 approvers_v2 传入）**。
3. **事件订阅**：应用「事件与回调」→ 启用 **Stream 模式**；订阅事件：审批（`bpms_instance_change`）+ 通讯录用户变更（`user_leave_org`）。
4. **发布**：H5 微应用需配置应用首页地址（公网或钉钉内网穿透工具）并发布。

---

## 8. 实施计划

> 细化的分阶段任务清单、接口契约、类设计与验收标准见《钉钉OA审批集成实施计划.md》。

| 阶段 | 内容 | 产出 |
|---|---|---|
| P0 前置 | 钉钉后台应用/权限/模板/Stream 订阅；理清凭据 | 配置清单、process_code 列表 |
| P1 基建 | `app.dingtalk.*` 配置 + DingTalkClient(access_token Redis 缓存) + Stream 监听骨架 | 可收到/打印审批事件 |
| P2 发起联动 | `approval_instance` 表(Flyway) + 单据 `dingtalk_instance_id` 列 + 入口 A：三单据 create 后推钉钉审批实例 | 系统发起→钉钉收到审批 |
| P3 回调落地 | 入口 A finish 落地（复用 approve/reject）+ 入口 B start 建单锁资产 + 超时对账 | 钉钉审批完成→系统资产联动 |
| P4 通知外发 | `NotificationService` 追加工作通知 + 审批待办 | 审批人/申请人收到钉钉消息 |
| P5 离职联动 | `user_leave_org` 事件 → 清退接口 + 离职退库确认单兜底 | 离职自动清算 |
| P6 可选 | 钉钉 H5 微应用 + 移动端响应式 | 钉钉内全流程 |

---

## 9. 风险与对策

1. **凭据两套并存**（comm_public_basic 硬编码 vs yml）——asset 用独立应用规避，不与 comm_public_basic 共享凭据。
2. **access_token 无缓存**——asset 自建 Redis 缓存（提前刷新）。
3. **事件重复/丢失**——唯一键幂等 + `LATER` 重推 + 超时对账兜底。
4. **共享池单据无指定审批人**——决策点：限制指定 or 留站内。
5. **审批人未绑定钉钉**——发起时校验阻断并引导绑定。
6. **Stream 需公网出网**——确认部署环境可访问公网（仅出站）。
7. **移动端适配工作量**——列为 P6，不阻塞主链路。

---

## 10. 参考

### 钉钉官方文档
- 发起审批实例：https://open.dingtalk.com/document/orgapp-server/initiate-approval
- 审批实例详情：https://open.dingtalk.com/document/development/get-details-single-approval-instance
- 审批事件回调：https://open.dingtalk.com/document/orgapp-server/approval-events
- Stream 模式：https://open.dingtalk.com/document/development/stream
- 发送工作通知：https://open.dingtalk.com/document/orgapp/asynchronous-sending-of-enterprise-session-messages
- 创建待办任务：https://open.dingtalk.com/document/orgapp-server/add-dingtalk-to-do-task
- 自有审批待办：https://open.dingtalk.com/document/app/initiate-an-approval-process
- 通讯录用户离职事件：https://open.dingtalk.com/document/development/address-book-user-resignation

### GitHub 成功案例
- **open-dingtalk/h5app-approval-of-store-revenue-process-demo**（官方，最贴合）：后端 SpringBoot 实现获取 token、免登、创建审批实例、**接收审批回调事件**、模板管理，含内网穿透启动脚本
- **open-dingtalk/h5app-approval-to-do-demo**（官方）：创建审批实例 + 待办消息打通，审批人无需切换系统
- **gitee usdc/dingTalk-OA**：SpringBoot 集成钉钉 OA 审批流程，accessToken Redis 缓存、待审批/已审批/实例详情
- **gitee tuanligo/ding-approval**：审批对接平台框架，`BusinessDataService` 抽象多业务数据提取，模板/实例映射思路可借鉴
- 本地参考：`asset-management/references/ciyo-itasset/.../DingTalkSendMsgHandle.java`（工作通知 OA 消息完整写法，被注释可取消）
