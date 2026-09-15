---
name: "asset-approval-audit"
description: "资产审批单据复查（只读）：拉取钉钉资产审批实例，与系统 approval_instance/资产/持有/仓位交叉核对，输出异常报告。每日定时复查或用户要求检查审批单同步、归还执行情况时调用。"
---

# 资产审批单据复查（只读审计）

把钉钉侧资产审批实例与本地系统数据交叉核对，发现"漏导入 / 状态不一致 / 归还未执行 / 位置落错"等异常。**只报告，不修改。**

## 0. 硬红线

- 数据库操作**只允许 SELECT**，禁止任何 UPDATE / INSERT / DELETE / DDL
- 发现异常**不得修复**，逐项列出让用户决策
- 无异常也必须输出简要报告（每日复查的"平安确认"）

## 1. 连接信息（由调用方提供，勿硬编码）

| 项 | 说明 |
|---|---|
| 服务器 SSH | 例 `ssh root@<SERVER_IP>`（已配 BatchMode 免密） |
| 资产库 | `docker exec asset-mysql mysql -uroot -p<PWD> --default-character-set=utf8mb4 db_asset` |
| 用户库 | 同容器 `db_comm_public_basic` |
| 钉钉应用 | 生产 appkey / appsecret（gettoken 换 access_token，有效期 2h） |
| 模板编码 | SSH 读 `/srv/docker-compose.yml` 的 `APP_DINGTALK_PROCESSCODES_{RECEIVE,RETURN,BORROW,TRANSFER,CHANGE}`；逗号分隔 = 同类型多模板（如 RETURN = 正式版 + 简化版）；未配置的类型跳过 |

注意：库内时间已统一北京时间；钉钉 API 返回的 create_time/finish_time 也是北京时间字符串，可直接比较。

## 2. 拉取钉钉实例（窗口：近 7 天）

1. `GET https://oapi.dingtalk.com/gettoken?appkey=<AK>&appsecret=<AS>` 取 token
2. 逐模板列实例 ID（分页翻完）：
   `POST https://oapi.dingtalk.com/topapi/processinstance/listids?access_token=<T>`
   body：`{"process_code":"<CODE>","start_time":<ms>,"end_time":<ms>,"size":20,"offset":0}`
3. 逐实例取详情：
   `POST https://oapi.dingtalk.com/topapi/processinstance/get`
   body：`{"process_instance_id":"<ID>"}` → 取 `status / result / create_time / finish_time / title / originator_user_id / tasks / form_data`
4. 若 listids 接口不可用，降级：从 `dingtalk_event_log.payload`（近 7 天）提取 distinct `processInstanceId` 逐个 get 详情

## 3. 交叉核对规则

对每个钉钉实例（模板类型从其 process_code 归类）：

1. **漏导入**：`approval_instance` 无同 `process_instance_id` 行，且单据创建超过 30 分钟 → 异常「未导入系统」
2. **状态不一致**（系统 status/result vs 钉钉 status/result）：
   - 钉钉 `COMPLETED+agree`，系统非 COMPLETED/agree → 异常
   - 钉钉 `RUNNING/NEW`，系统已 COMPLETED → 异常
   - 钉钉 `TERMINATED`（撤销），系统仍 RUNNING → 异常
3. **归还执行核验**（RETURN 且终审 agree）：
   - `asset`：status=IDLE 且 user_id IS NULL，否则异常「归还未执行干净」
   - `asset_allocation`：asset_ids 中每台资产的活跃持有（returned_at IS NULL）应不存在 / 已闭环行 returned_at 非空
   - **位置**：资产名称含 笔记本/台式/主机/一体机/显示器/显示屏/电脑/Mac（不分大小写）→ location 应为 IT部闲置仓（查 `asset_location` 按名取 id，勿硬编码数字）；**非电脑类资产只报告当前仓位名，提示人工过目**（约定：非电脑资产一件件人工跟进）
4. **领用执行核验**（RECEIVE 且 agree）：asset 为 IN_USE、user_id=领用人、存在活跃持有（returned_at IS NULL）
5. **系统孤儿**：`approval_instance` 仍 RUNNING，但钉钉侧实例已不存在（listids 无且创建超 7 天）→ 提示核实

## 4. 通道健康

- `dingtalk_event_log` 近 24h 按 status 计数；FAILED>0 → 列出 event_type 与条数
- 近 24h 零事件 → 警告「Stream 疑似断连」（需结合当天是否有 OA 活动综合判断，勿单独定论）

## 5. 报告格式

**有异常**（逐项，明确"未修改，待人工处理"）：

```
【复查发现异常】共 N 项 —— 均未修改，等待人工处理
1. <实例ID / 标题 / 发起人>
   问题：<漏导入|状态不一致|归还未执行|位置待确认|通道异常|...>
   详情：<两侧关键数据对照（钉钉值 vs 系统值）>
```

**无异常**：

```
今日复查：共 M 笔资产审批单，全部正确处理，无异常
- 分类明细：领用 x 笔 / 归还 y 笔（正式版 a、简化版 b）/ 在途 RUNNING k 笔
- 事件通道：正常（24h 接收 K 条，失败 0）
- 非电脑类归还包括：<条数与仓位名列表，供人工过目>
```

## 6. 边界

- 只覆盖已配置模板的实例；非资产模板事件属正常流入，不属本复查范围
- 历史遗留数据（如导入台账的 location_before 口径）不算异常，除非影响归还落位
- 需要修复时，由用户逐单指示后再另行操作，本 skill 永不修数
