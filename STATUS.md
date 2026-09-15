# STATUS.md — 项目进度看板

> 任何阶段性进展完成后必须更新本文件。最后更新时间见文末。
>
> **修订记录**：2026-08-18 经 REVIEW-001 审阅后修订，架构降级为单模块三层 + 鉴权复用 comm_public_basic。

## 当前阶段

**M10 已上线生产 + 日常运营维护**：
- 后端：B1/B2/B3 + M07 盘点 + CI 自动部署 + B4 两级审批链均已落地提交（B4/V20260832/V20260833 已随 d65787d 入库）；**M10 钉钉 OA 审批双向集成全链路上线**（入口 A 推送 / 入口 B 导入 / Stream 回传 / 退还对接，版本 0.1.3 = commit ba1ac99，后经 bfdd63c 终态同步修复、649e2e7 抄送通知增补，均已部署 192.0.2.1）
- 前端：main 已上线组织架构管理页（审批链配置）+ 帮助面板版本抽屉 + 通知/审批中心"抄送我的"视图（ef8bb44）
- 环境：192.0.2.1 已由演练沙箱转为**生产环境**（钉钉连接器=示例科技有限公司）；生产数据已导入（仓位/审批链/资产台账），日常以 SQL 直写 + 钉钉全链路运营
- 待执行：M08 历史数据迁移（POST /api/v1/migration/run，按需）；钉钉侧建议补开通 `qyapi_aflow_execute` 权限（API 代审批，便于自动化回归）
- **M10 钉钉 OA 审批双向集成**（2026-08-31 设计完成，已上线生产，开发细节存档）——[ADR-0007](./docs/adr/0007-钉钉OA审批双向集成.md)（企业内部应用 + 新版 processInstances API + Stream 事件订阅，配置化实验/生产切换）+ [M10 模块 spec](./docs/modules/M10-钉钉OA集成.md)（三条链路：系统建单自动生成钉钉 OA、钉钉审批实时回传推进状态机、钉钉人工发起受理含退库单；oa_instance/oa_event_log 两表 outbox + 幂等）。**入口 A + 事件回传（入口 B 回传侧）代码已落地**（2026-08-31）：V20260834（approval_instance 表 + 三单据 dingtalk_instance_id 列）+ dingtalk 包（DingtalkProperties/DingTalkApiClient/DingTalkTokenClient/DingtalkStreamBootstrap）+ ApprovalSyncServiceImpl（AFTER_COMMIT outbox，降级站内审批）+ ApprovalCallbackServiceImpl（task/instance 事件推进状态机，两级链映射）+ 三单据 create 发布 OaSyncRequestedEvent；编译 + 449 单测全绿。**回传链路验证已补齐**（2026-08-31）：ApprovalCallbackServiceImplTest 19 用例（agree 逐级推进/二级终态/refuse 拒绝/terminate 撤销分发/instance:finish 终态兜底/eventId 幂等/corpId 跨企业校验/操作人无法解析/基础设施异常不外抛）+ ApprovalSyncServiceImplTest 13 用例（总开关关闭/模板未配置静默跳过/审批链未绑定钉钉 FAILED 告警/两级同人合并单节点/两级不同人顺序节点/API 失败降级/已 SYNCED 幂等跳过）；ApprovalCallbackServiceImpl 增加事件到达 INFO 日志（排查 instance:finish 事件未达）；ApprovalSyncServiceImpl 三入口（领用/调拨/变更）增加 alreadySynced 前置幂等检查（模板未配置先短路）；全量 481 单测 BUILD SUCCESS。**实验公司联调全链路打通**（2026-08-31，领用单）：实验公司凭证（appKey=ding10gsbdibezs5kkfv，corpId=dingd7404a40804add6ccecc981432f595ea，agentId=4926800774）+ 领用模板 processCode（PROC-688751E6-...FAC6）配置后，Stream 建连成功；系统建单 ARE202608310003 → outbox 同步 → 钉钉审批创建（5 契约字段正确落入表单：单据编号/资产编号/资产名称/领用区域/事由，模板经三轮调整去掉表格字段改为 5 个独立字段）→ 钉钉 App 审批同意 → `bpms_task_change(agree)` + `bpms_instance_change(agree)` 事件回传 → 状态机推进：单据 PENDING→APPROVED、资产 待确认→IN_USE（user_id 落持有）、approval_instance RUNNING→COMPLETED(result=agree)、站内通知（审批人/发起人）全链路验证通过。**剩余**：钉钉侧建议补开通 `qyapi_aflow_execute` 权限（API 代审批，便于自动化回归）；入口 B 导入代码已完成（见本节末），钉钉原生发起 → 系统自动导入的实机联调验证待做。**四模板全链路联调完成**（2026-08-31）：borrow（PROC-3019F7BA-...）/transfer（PROC-023368F2-...）/change（PROC-4BE72E22-...）模板建好并配置，三单（BOR202608310001/ATR202608310002/AOC202608310002）经 API 建单→outbox 同步→钉钉审批→同意→事件回传→状态机推进全部验证通过（借用单 APPROVED+资产 IN_USE；调拨单 COMPLETED+资产归属转移至调入仓；变更单 CONFIRMED+资产位置明细更新）。**联调发现并修复一处缺陷**：调拨单 instance:finish 终审兜底用调入人 confirm，当发起人=调入人时被"不能自确认"业务校验拦截且异常中断导致 approval_instance 终态不落库（停在 RUNNING）；修复为 handleInstanceFinish 内兜底 try-catch（业务拦截记 WARN，终态照常落库，业务推进交由 task:finish 事件，本次实测 task 事件随后正确补位），新增测试 instanceFinishAgree_兜底被业务拦截_终态仍落库，全量 482 单测 BUILD SUCCESS。另确认实验环境数据现状：李四(691)/王五(762) 共用同一 dd_user_id（单人替审手段），findByDdUserId 解析顺序依赖此绑定。**入口 B（钉钉原生表单发起 → 系统自动导入）代码完成**（2026-08-31，工作区待提交，497 单测 BUILD SUCCESS）：回调链路收到本地无映射的实例事件且 processCode 命中四模板之一时，InboundApprovalImportService 回查实例详情自动建单；**审批人一律以钉钉实例 tasks 为准**（M10 决策：钉钉创建的单据自带审批人时系统不覆盖）——领用/借用 → approval_step1/2 快照冻结（两级链/同人合并单），调拨 → toUserId（入口 A 契约：审批人=调入方），变更 → assigneeUserId（审批人=处理人）；四单据 Service 新增 createFromDingtalk（doCreate 重构共用建单主体，跳过 OA 回推事件防死循环）；表单按入口 A 契约解析（资产编号/区域名称/事由等，区域名称精确匹配、使用人姓名精确唯一匹配兼容钉钉 userid）；导入失败（解析失败/审批人未绑定/业务校验拦截）→ 告警 systemAdmin 人工补建，不外抛不阻断钉钉侧审批；InboundApprovalImportServiceTest 12 用例（两级审批人快照/合并单/幂等/调拨变更导入/审批人未绑定/使用人不存在/资产不存在/业务拦截/非配置模板/导入晚于钉钉终审的两级链与调拨终态落地）。

2026-08-31 17:00 入口 B 首单联调排查（businessId 202608311643000078967 未显示）：根因①`/topapi/processinstance/get` 新版返回包裹在 `process_instance` 而非 `result`（DingTalkApiClient 已兼容两者）；根因②导入触发可能晚于终态事件消费——导入服务现按详情 `status/result` 直接落终态（agree→两级链推进/调拨 confirm/变更 confirm；refuse/TERMINATED→对应拒绝/取消），与回调层终审兜底幂等互备；根因③表单填的区域名"IT仓位"不在系统 `asset_location`（需与系统位置名完全一致，如"IT部在用仓"）。服务已重启（17:11），待重发单据验证。

2026-08-31 17:20 入口 B 填表体验闭环（钉钉官方模板不支持动态数据源，填表时无法实时查系统空闲资产/仓位——硬限制）：**导入失败现在同时通知发起人本人**（此前仅告警管理员，发起人在钉钉侧看到审批通过、系统无单，完全无感知；发起人未绑定时仍仅告警管理员）；**区域名对不上时附相近仓位提示**（包含匹配 + 前 2 字前缀匹配，最多 3 个候选，如"IT仓位"→提示"IT部在用仓、IT部闲置仓"），发起人可自行修正重发。模板侧建议（钉钉设计器人工操作）："领用区域/调入区域/变更后位置"改单选组件（选项=系统仓位名，仓位变更需同步模板）；资产编号字段说明写明"从系统资产列表复制"。InboundApprovalImportServiceTest 15 用例，全量 500+ BUILD SUCCESS，服务已重启（17:19）。

2026-09-01 08:40 领用自审实例预检（审批人=申请人）：系统红线"审批人与申请人不能是同一人"在 approve 时 403 而建单时不校验——钉钉允许自审实例，若放行入口 B 会建出永远无法推进的卡死单（回传事件全部 403、终态兜底也被拦）。修复：importReceipt 在建单前预检 chain.step1/step2 == applicant → 直接拒绝导入（400，含"请在钉钉模板中调整审批人后重新发起"提示），发起人+管理员双通知。注：一级/二级审批人**同人**不是错误——合并为一次审批（step=2，MERGED_STEP_REMARK 审计），入口 B 去重逻辑已覆盖。入口 A 无此问题：系统建单报错则事务回滚、OA 事件不发布，钉钉不会建单。新增自审拒绝单测（16 用例），全量测试 BUILD SUCCESS，服务已重启。

2026-09-01 08:50 联调脏数据清理（用户确认全清）：删除 8-31 起全部测试数据——领用/借用单 8 张+明细、调拨 2 张+明细、变更 2 张+明细、approval_instance 10 条、sys_notification 31 条（含 16:43/16:44 六条失败告警）、asset_allocation 8 条（id 6796-6803）；资产回置：2346 回示例科技物料仓(调拨前位置)、2347/2412/3598 置 IDLE 清持有人。注：今早 ARE202609010001（入口 B 首张导入成功单）一并清除，若钉钉侧已审批其回传事件将按"未知实例"记日志（无害）。备份：/tmp/backup-dirty-20260901.sql + /tmp/backup-dirty-alloc-20260901.sql。另：入口 B 导入的建单未回写 receive_receipt.dingtalk_instance_id（回调走 approval_instance 映射不受影响，仅列表展示缺实例号，待后续顺手补）。

2026-09-01 08:57 修复入口 B 实例号回填：InboundApprovalImportService.insertRecord 建映射后新增 backfillInstanceId——领用/借用/调拨/变更四类单据按 bizType 回填 dingtalk_instance_id（单据源自钉钉，创建即已知实例号；与入口 A ApprovalSyncServiceImpl 同步后回填对齐，审批中心可统一区分站内/钉钉通道展示）。既有导入测试补回填断言，全量测试 BUILD SUCCESS，服务已重启（08:56，Stream 就绪）。

2026-09-01 下午 **钉钉退还审批对接上线**（commit ba1ac99，版本 0.1.3 已部署 192.0.2.1）：解析钉钉退还模板资产明细表（兼容两种格式）+ 校验持有状态，终审 agree 自动执行归还（闭环 allocation + 位置回置快照），拒绝/撤销仅通知发起人；服务器 compose 补充 `APP_DINGTALK_PROCESSCODES_RETURN`；全量 507 单测通过（含修复测试静态块缺少三张单据实体 TableInfo 缓存导致的 5 个既有用例失败）。同日后端另有两笔提交：bfdd63c（fix：多级主管审批链下单据终态与钉钉实例严格同步，不再提前关单）、649e2e7（feat：抄送我的——钉钉审批终态后按模板抄送人逐一站内通知）；前端 main 同步上线组织架构管理页/帮助面板版本抽屉/通知与审批中心"抄送我的"视图（ef8bb44，均已推送 origin/main）。

2026-09-01 **生产公司切换 + 真实数据导入**（运维操作，无代码改动）：钉钉连接器由实验公司切换至生产公司**示例科技有限公司**（appKey=ding_example_app_key，compose 已配置）；沙箱 192.0.2.1 定位转为生产环境。数据侧：清空 asset 库 23 张业务表（保留 comm_public_basic）→ 验证 22 位使用人钉钉↔sys_user 映射 → 创建"研发在用仓/自动化部在用仓"等仓位及 26 条审批链 → 导入 41 条资产 + 笔记本台账 128 条电脑显示器（新增 8 个 sys_user，密码统一 a123456）；comm_public_basic 380 个默认密码用户批量改为 a123456（解锁"默认密码用户不得进入业务系统"拦截），用户挂 asset-基础可见(351) 角色后可登录；服务器侧配置 `APP_USER_DIRECTORY_URL`（db_comm_public_basic 的 asset_ro 只读账号）修复资产列表使用人姓名不显示。

2026-09-02 台账增量维护（新表单《示例科技笔记本台账20260901》，4 个编码，SQL 直写无代码改动）：新建 sys_user 张侃(818/SK5909，治具设计课，dd_user_id 经钉钉手机号反查)、汤昌茂(819/SK9911，自动化设计课)，均挂 asset-基础可见；资产修正 3 台——SKBGDN309 位置改谢永丽(799)、SKBGDN220 持有人王冰→张侃(818)、SFBGIT3542 持有人韦必耀→汤昌茂(819)；新增 SKBGIT3377（Macbook，IT部在用仓，持有人李四 691，管理人丘碧玲入 remark）。

2026-09-02 **全量资产初始化导入**（运维操作，SQL 直写无代码改动，备份 /root/backup_db_asset_20260902_1635.sql.gz）：《资产台账_公司所有电脑显示器(1).xlsx》6799 条（含既有 129 条）全量导入。**组织架构断代处理**：历史部门（研发中心/职能中心/生产PVD 等 33 个中间层级）已从钉钉树消失，资产位置树按钉钉现架构重建——新建 15 个组织节点（IT科/工程科/生产科/资源科/质量科/示例科技工程课/设备维修组/示例科技资源课/量产课/示例科技采购科/示例科技财务科/示例科技A商务科等）+ 5 个新有效仓位，10 个旧组织节点软删；**映射不了的历史仓位（PVD 各层/设备维护各层/生产部楼层仓等 23 个）新建即软删（deleted=1，不可再被新单据选择）**，其资产 location_detail 写【历史仓】前缀保持可见，由使用人提实物变更单逐步纠正，迁空后可删仓；7 个现有仓位同步软删（PMC在用/品质部在用/生产部3F/设备维护2F3F/2楼捡包/锐鑫智能在用）；"示例科技"根 233 条维修专用资产挂软删历史仓。用户新建 11 个（SK77788~98）；钉钉查无者挂对应管理人（黄志攀→李苗、彭安辉/李一平→谢雨欣）或置空 remark 注明。示例丰公司实体新建（id=2，1019 条资产）。

2026-09-03 人事排查台账修正（《示例科技笔记本台账20260901.xlsx》129 条笔记本真实归属，备份 /root/backup_asset_20260903_1024_before_nbfix.sql.gz）：43 条不变 + 44 条改既有账号 + 33 条新建账号（共 37 个新号 831~867，密码 a123456 挂 asset-基础可见）；安吉 6 条调拨资产挂张三名下并备注；林昆(RX01)/胡爽(RX15) 建号挂锐鑫智能部门、金今花(SF0001) 挂示例丰；129 条 user_department 全量对齐 sys_user.dept（钉钉权威路径）。李莉新账号释放旧已删账号 username 唯一键（改 SK9897_del783）。

2026-09-03 部门主管核对与审批链核正：拉取钉钉 73 部门全量主管，10 条 DEPT_SUPERVISOR 配置与钉钉一致 9 条（示例科技采购科保留明超，钉钉主管郭磊，用户拍板不改）；确认解析链路"逐级向上回退"天然覆盖 8 个未设主管部门（NPI课/PD课/生产科/IE课/EHS科/东莞研发/体系组/SQE组），不填临时主管；示例丰/锐鑫智能主管指定李四（待配置）。

2026-09-03 **B5 钉钉多级主管审批链方案定稿**（方案见对话存档）：领用/借用审批链从"部门主管+仓管员两级"改为**张三（固定一级）→ 发起人部门逐级向上主管**，终态抄送李四/张三/王五。核心设计：系统快照仅记 step1=张三、step2=直接主管，快照外审批节点操作记入 approval_instance.callbacks 日志；单据终态与钉钉实例严格同步（COMPLETED+agree 才关单，已由 bfdd63c 落地）；钉钉审批同意站内自动推进/关单（回调侧零改动，已就绪）。改动集中推送侧：新表 dingtalk_dept（部门树+主管本地缓存，启动全量+每日刷新）+ 多级链解析器（未设主管向上回退；示例丰/锐鑫智能→李四）+ 入口A approvers_v2 组装 + 建单快照；入口B 读钉钉 tasks[0]/tasks[1] 天然兼容（需实测 tasks 时序）。站内降级链同步改为新语义（仓管级退出领用/借用）；DEPT_SUPERVISOR/WAREHOUSE_KEEPER 配置保留（调拨/变更/历史单据仍依赖）。

2026-09-03 **B5 多级主管审批链开发完成**（版本 0.1.7，工作区待提交，540 单测 BUILD SUCCESS）：V20260903（dingtalk_dept 表：dept_id/name/parent_id/manager_dd_user_ids，软删）+ DingtalkDept entity/mapper（upsert ON DUPLICATE KEY + 软删）+ DingTalkApiClient 新增 listSubDepts/getDeptManagerIds（v2 部门接口）+ DingtalkDeptSyncService（启动 ApplicationReadyEvent 全量 BFS + 每日 02:40 定时刷新，根 dept_id=1 不落库，钉钉侧已删部门软删；失败仅告警不阻断启动）+ **DeptManagerChainResolver**（多级链解析：sys_user.dept 原始路径精确匹配 → 逐级去末级回退（与两级链 deptFallbackChain 规则一致）→ 沿 parent 向上收集主管去重去本人；示例丰/锐鑫智能 special-dept-managers 配置兜底（路径段从深到浅匹配）；快照 step1=固定一级、step2=链上第一个绑定账号的主管，直接主管=固定一级时 merged 合并单且钉钉节点去重；未设主管一律向上回退不填临时主管；兜底 400：固定一级未配置/未绑定、申请人无部门、链上无可绑定主管、固定一级=申请人死单防御）+ 入口A ApprovalSyncServiceImpl 改用 tryResolveMultiLevel 取 allDdUserIds 组装完整审批节点（解析失败 FAILED 降级站内审批）+ 建单 ReceiveReceiptServiceImpl create/previewApprovalChain 切换新解析器（预览展示全链）+ 回调侧零改动（bfdd63c 终态同步已就绪）+ 入口B 零改动（resolveApproversFromTasks 取 tasks 去重前两人 = 固定一级→直接主管，天然兼容）。配置：app.dingtalk.fixed-first-approver-dd-user-id（张三 user_example_001）/ special-dept-managers（示例丰、锐鑫智能→李四 user_example_002）/ cc-user-ids（李四、张三、王五 user_example_003）/ dept-sync-cron。新增 DeptManagerChainResolverTest 14 用例（全路径/回退/合并/去重/剔除本人/未绑定跳级/横线路径/特殊部门/四类兜底报错）。

2026-09-03 **B5 已部署生产**（commits acbb439 + 150b5ef，541 单测全绿）：服务器 compose 已补 APP_DINGTALK_FIXEDFIRSTAPPROVERDDUSERID（张三）/ APP_DINGTALK_SPECIALDEPTMANAGERSTEXT（示例丰、锐鑫智能→李四，扁平文本形态）/ APP_DINGTALK_CCUSERIDS_0_~2_（李四/张三/王五）；部署联调修复一处：**Spring Boot 环境变量无法承载中文 Map 键**（APP_DINGTALK_SPECIALDEPTMANAGERS_示例丰 的非 ASCII 键在属性名转换中丢失导致启动崩溃），DingtalkProperties 新增 special-dept-managers-text 扁平文本配置 @PostConstruct 解析合并。部署验证通过：Flyway 20260903 落库、钉钉部门树启动同步完成（73 部门/59 设主管/0 失败）、环境变量注入正常、探活 UP。待办：实机联调验证钉钉多节点 tasks 时序与回调推进。

2026-09-03 **站内审批→钉钉代执行双向同步上线**（版本 0.1.8，546 单测全绿，实验公司端到端验证通过）：修复联调反馈"站内同意后钉钉表单仍待办，审批人需操作两次"。实现：OaTaskExecuteRequestedEvent（审批事务内发布，AFTER_COMMIT 消费）+ DingTalkApiClient.executeApprovalTask（v1.0 `POST /v1.0/workflow/processInstances/execute`，工作流实例写权限，实验公司已开通）+ ApprovalSyncServiceImpl.onTaskExecuteRequested（回查实例详情找该审批人 RUNNING 任务代执行 agree/refuse；实例已终态或无待办则幂等跳过；失败仅告警）；ReceiveReceiptServiceImpl approve/reject/advanceToStep2 三出口发布事件（单据有 dingtalk_instance_id 才发）；回调侧 applyRefuse 加 BusinessException 容忍（站内先拒→代执行后 refuse 事件回传时单据已终态，被拦截记 WARN 属预期，终态照常落库）。实验公司验证（合并单场景：李四发起，固定一级=王五兼 IT科主管）：站内同意后 1 秒内钉钉任务自动 agree、实例 COMPLETED、资产 IN_USE；站内拒绝同理（refuse + 资产回 IDLE + 回传事件幂等拦截 WARN 可见）。防环：回调推进调用的 approve 不再触发代执行（该审批人任务已 COMPLETED 查无待办自然跳过）。

## 已完成

- [x] 创建项目目录 `asset-management/`
- [x] 开源调研：Snipe-IT、Shelf.nu、西柚 Ciyo、chyinan/Asset-Management-System、若依固定资产
- [x] 候选项目 clone 到 `references/`（西柚 GPL-3.0 仅参考业务模型；chyinan 仅参考工程实践）
- [x] 调研 comm_public_basic 源码：Redis Token（非 JWT）+ 多系统接入（PortalProject）+ 钉钉登录已就绪
- [x] 调研 Ciyo ciyo-asset 源码：若依风格传统三层（非 DDD），15+ 业务实体
- [x] 方案可实施性审阅：[docs/REVIEW-001](./docs/REVIEW-001-方案可实施性审阅.md)（识别 5 处偏差，给出调整建议）
- [x] 架构决策修订：
  - [ADR-0004](./docs/adr/0004-鉴权复用comm_public_basic与SpringBoot3解耦.md)：鉴权复用 comm_public_basic + Spring Boot 3.x + HTTP 解耦 + 钉钉
  - [ADR-0005](./docs/adr/0005-架构降级为单模块三层.md)：单模块三层（Superseded ADR-0002 的 DDD 6 模块）
- [x] 工程规范修订：[docs/ENGINEERING.md](./docs/ENGINEERING.md)（新增 R8 鉴权红线，P1-P4 适配单模块三层）
- [x] AGENTS.md 修订（结构 + 鉴权接入要点）
- [x] **M01-A 后端骨架**（2026-08-19）：
  - `asset-backend/` 单 Maven 模块：Spring Boot 3.5.16 + JDK 17 + MyBatis Plus 3.5.17 + Flyway + Spring Security（permitAll 占位，M01-B 替换）
  - 包骨架 `com.sk.asset`（config/common/auth/controller/service/mapper/entity/dto/enums，package-info 注明边界，上下文子包随 M02+ 落地）
  - Flyway `V20260819__create_core_tables.sql` 一次建齐 19 张核心表（逻辑外键 + utf8mb4，字段设计依据 ADR-0006 + 模块文档 + M08 迁移规则）
  - API 文档：SpringDoc 2.8.17 端点 + knife4j-openapi3-ui 静态 webjar（/doc.html）
  - 验证：`mvn clean compile` 通过；本地启动 Flyway 成功落 19 表；doc.html / v3/api-docs 200
- [x] **M01-B 前置处理**（2026-08-19，AuthPort/TokenFilter 之外的三项安全基建）：
  - OpenApiConfig 注册 BearerAuth SecurityScheme（HTTP bearer，非 JWT）+ 全局 security 引用 → /doc.html 出现 Authorize 按钮，M01-B 后可直接手动测受保护接口
  - SecurityConfig 增加 CORS：`CorsConfigurationSource` bean（origin 白名单 `app.cors.allowed-origins`，默认 `http://localhost:5173` 即 M-FE01 Vite 开发端口）+ 链上 `.cors(withDefaults())`；allowCredentials=true 显式 origin
  - permitAll 占位链加 `@Profile("local")`：非 local 环境回退 Spring Boot 默认安全配置（全部请求需认证，fail-closed）
  - 验证：白名单 origin 预检 200 + 正确回显 Allow-Origin/Credentials；非白名单 origin 预检 403；prod profile 无认证访问 /doc.html、/v3/api-docs 均 401
- [x] **M01-B 前置阻塞项解除**（2026-08-19，grillme 会话核实）：
  - **comm_public_basic 接口契约核实完成**（origin/main 与本地私改版逐项比对，契约层一致）：响应统一 `{code, message, data}`，code=200 成功；token 一律 `Authorization: Bearer` 头；`/login/userSystemAuth` 需额外携带请求头 `systemCode: asset`；**无效/缺失 token 时三接口（checkToken/getAuth/userSystemAuth）均经 TokenException 全局处理返回 HTTP 401 + {code:401}**（ExceptionConfig:148，实测确认）
  - **auth-center-frontend token 传递方式确认**：登录成功后跳转子系统时 token 通过 **URL query 参数**传递；子系统前端取参后以 `Authorization: Bearer` 头访问后端 → M-FE01 落地接收拦截器
- [x] **M01-B 鉴权接入**（2026-08-19）：
  - `auth/` 包：`AuthPort` 接口 + `CommPublicBasicAuthPort`（RestClient 三连回源 checkToken→getAuth→userSystemAuth + Caffeine 30s 短缓存仅存成功结果 + 401/403/503 语义映射）、`TokenAuthFilter`（Bearer 提取 → SecurityContext + UserContext 注入，finally 双清理）、`AuthContext`/`UserContext`
  - `SecurityConfig`：STATELESS + TokenAuthFilter 替换 permitAll 占位；文档端点 fail-closed（仅 local `app.security.docs-permit-all=true` 放行）；CORS 复用前置处理
  - `GET /api/v1/me` 验证接口（MeResp：用户信息 + asset 角色 + 按钮权限码）
  - 配置 `app.auth.*`（base-url 不入库，application-local.yml + .example 模板）
  - 本地 comm_public_basic 库 `basic_portal_project` 注册 asset 项目（id=22，web_url=localhost:5173，**is_visible=0 暂隐藏**，M-FE01 前端就绪后置 1）
  - **契约修复**：comm_public_basic 无效 token 返回 HTTP 401，最初实现误按"服务不可用 503"处理 → 新增 `translateResponseError` 映射（401→401"token 无效或已过期"，其余→503 fail-closed）
  - 测试：**单测 19 个全过**（AuthPort 11 / TokenFilter 6 / MeController 2），`mvn clean test` BUILD SUCCESS
  - **全链路实测**（本地 comm_public_basic:6002 + asset-backend:6006）：admin 登录取 token（密码 RSA 公钥加密）→ 无 token 401 / 伪造 token 401（修复后语义正确）/ 有效 token + 空 asset 角色 403"尚未分配 asset 系统角色"（admin 为默认密码用户，被 comm_public_basic"默认密码用户不得进入业务系统"策略拦截，判定语义正确）。**200 成功路径由单测覆盖**（有效token_三接口全通过_返回上下文并写缓存）；live 200 需非默认密码 + 有角色的用户，待 M-FE01 联调或正式用户就绪时补验

- [x] **M-FE01 前端骨架**（2026-08-19）：
  - `asset-frontend/`：Vue 3.5 + Vite + TypeScript + Element Plus + Pinia + Axios + Vue Router 4
  - token 接收：路由守卫 `beforeEach` 从 URL query 取 token → 存 localStorage → `stripAuthParamsFromUrl` 清洗 URL（history 路由）
  - axios 拦截器：请求自动附加 `Authorization: Bearer` + `systemCode: asset`；响应 401 清 token + 跳回 auth-center 登录页（防抖 `redirectingToLogin`）
  - `/dashboard` 工作台页：调 `GET /api/v1/me` 展示用户姓名/部门/岗位/角色，全链路打通（auth-center → asset-frontend → asset-backend → comm_public_basic）
  - **全链路实测**（assetfe / SK9802 两账号）：auth-center 登录 → 点击 asset 卡片 → 跳转 asset-frontend → 工作台正常渲染用户信息
  - **redirectingToLogin 防抖修复**：`resetLoginRedirectFlag()` 导出 + 路由守卫 `beforeEach` 每次导航重置，防止整页跳转被中断后标志卡死、二次 401 无法再跳登录
  - **懒验证设计落档**：路由守卫只校验 token 存在性不预验有效性（有意为之），过期 token 由页面首个 API 401 兜底驱逐；约定见 M-FE01 spec"鉴权验证策略"节 + ENGINEERING.md P5
- [x] **asset 项目注册 comm_public_basic**（2026-08-19）：
  - `basic_portal_project` 注册 asset（id=22，project_en=asset，web_url=localhost:5173）
  - **is_visible=0, is_enabled=0**（comm_public_basic 约定：**0=显示/启用，1=隐藏/禁用**，与前端直觉相反——应用中心按 0/0 过滤展示）
  - 联调账号：assetfe（`Asset@2026`）/ SK9802（`Sk9802@2026`），均已分配 role_id=350 asset-资产管理员

- [x] **M02 基础数据模块**（2026-08-20）：
  - Company Entity/Mapper/Service/Controller（只读接口：GET list + GET detail）
  - Manufacturer 完整 CRUD（Entity/Mapper/Service/Controller/DTO）
  - Supplier 完整 CRUD（Entity/Mapper/Service/Controller/DTO）
  - AssetCategory 树形结构（Entity/Mapper/Service/Controller/DTO，扁平列表）
  - AssetLocation 树形结构（Entity/Mapper/Service/Controller/DTO，materialized path，?parentId 子树查询）
  - DepreciationRule 骨架（Entity/Mapper/空 Service，Phase 4 实现）
  - AssetModel 关联查询（Entity/Mapper/Service/Controller/DTO，含关联对象名称，?categoryId 筛选）
  - 种子数据脚本 V20260820（company/asset_category/asset_location 初始数据）
  - 测试覆盖：**71 个测试全部通过**（Service 层单元测试 24 个 + Controller 层集成测试 28 个 + M01 鉴权测试 19 个），0 失败
  - API 端点：GET/POST/PUT/DELETE /api/v1/manufacturers、/api/v1/suppliers、/api/v1/locations、/api/v1/models；GET /api/v1/categories（只读，d40a2b5 移除写端点）；GET /api/v1/companies（只读）
  - **待跟进**：V20260820 种子数据脚本已写入 src/main/resources/db/migration/，按项目约定需在测试库 172.16.5.247 上手动执行
  - **代码质量审查**（2026-08-20，REVIEW-M02，实测 `mvn clean test` 71/71 通过）：
    - **通过项**：分层严格遵守 R1（entity 经 toEntity/updateEntity/from DTO 三件套转换不外泄）；命名符合 N1（XxxReq/XxxResp/构造器注入版 Controller）；契约符合 P3（统一 Result + /api/v1/ + Swagger 注解齐全）；参数化查询无 SQL 注入；@TableLogic 逻辑删除 + 自动填充统一；测试 mock 边界正确（service mock mapper、controller mock service）
    - **P1 待修（M03 前处理）**：① 注入方式不统一——Location/AssetModel 的 Controller+Service 已改构造器注入（b114b52），但 Manufacturer/Supplier/Category/Company 四组仍是 @Autowired 字段注入，且对应 4 个 Controller 测试被迫用反射注入 mock，应统一为构造器注入；② Location 树完整性无服务端保障——LocationReq.path 由客户端任意指定（materialized path 应服务端按 parentId 派生）、parentId 无存在性校验（可插悬挂节点）、移动节点不重算子树 path
    - **P2 待修（M03 时一并处理）**：③ 删除无引用完整性检查（manufacturer/supplier/location/model 被删除时不检查是否被 asset_model/asset 引用，M03 有数据后将产生悬挂引用）；④ AssetModelReq 外键 ID（categoryId/manufacturerId/depreciationId/companyId）无存在性校验；⑤ AssetModel 详情接口不带关联名称（列表 JOIN 带、详情不带，不一致）
    - **P3 minor**：⑥ Manufacturer/Supplier/Category Controller 测试未覆盖 404 分支（Category 仅 1 个测试，getById 未测）；⑦ email 字段缺 @Email 格式校验；⑧ CompanyServiceImpl.list() 缺排序（其余 list 均有 orderBy）；⑨ Service 单测多为转发验证型，M03 起复杂业务应测业务规则

- [x] **DevOps 基建：远端 + CI + 健康端点**（2026-08-20）：
  - 公司 GitLab 远端建立：`gitlab.example.com/sirpinaple/asset-backend`（私有，push-to-create）；本机 `~/.ssh/config` 映射 `id_rsa_gitlab`，push 直连免密
  - GitLab CI（`.gitlab-ci.yml`）：push 触发 `mvn clean verify`（纯单测无 DB 依赖），Maven 仓库跨流水线缓存；**共享 runner 是否接单待网页确认**（pipeline 页 pending=无 runner 需找管理员或自注册）
  - Actuator 健康端点：只暴露 `/actuator/health`（含 liveness/readiness 探针，容器健康检查就绪），其余 actuator 端点 fail-closed
  - **修复存量 bug**：docs-permit-all 失效——TokenAuthFilter 的 401 先于授权层发生，local 下 `/doc.html` 实测无法免 token 访问（401）。引入公共路径旁路机制：`shouldNotFilter` 旁路 + 授权层 permitAll 由 SecurityConfig 同一份 publicPaths 派生，永不脱节
  - 测试 75/75（TokenAuthFilterTest 新增 4 个旁路用例：健康端点/子端点/业务路径不旁路/文档端点旁路）

- [x] **M02.5 原型对齐 + REVIEW-M02 修复**（2026-08-22，前端原型 asset-system-preview.html 对齐）：
  - 迁移脚本 `V20260822__align_prototype_adjustments.sql`：manufacturer/supplier 加 status（1-启用 0-停用）；receive_receipt/asset_allocation 加 type（RECEIVE-领用 BORROW-借用，M04 共用单据流）
  - 厂商/供应商：分页（`PageResp` 统一契约 + MybatisPlusConfig 分页拦截器 + mybatis-plus-jsqlparser 依赖）+ keyword 模糊 + status 筛选 + 批量删除（引用检查任一被引用整体 409 拒绝）+ EasyExcel 导出端点（`/export`）
  - REVIEW-M02 P1①：Manufacturer/Supplier/Category/Company 全部统一构造器注入（测试去反射）
  - REVIEW-M02 P1②：Location path 服务端按 parentId 派生（物化路径不可客户端指定）+ 父节点存在性校验
  - REVIEW-M02 P2④⑤：AssetModel 外键存在性校验（category/manufacturer/depreciation/company）+ 详情 JOIN 关联名称（与列表一致）
  - GlobalExceptionHandler 补 `MethodArgumentNotValidException`→400（此前参数校验失败落入兜底 500）与 `IllegalArgumentException`→400（非法枚举参数）
  - 测试 111/111（厂商/供应商分页+导出+批量删除+409 引用链路全覆盖）

- [x] **M03 资产主表 CRUD + 状态机**（2026-08-22）：
  - `enums/asset/AssetStatus`：IDLE/IN_USE/PENDING_CONFIRM/DISCARD + `canTransitionTo` 状态机（DISCARD 为终态；非法流转 409）
  - Asset/AssetLog Entity + AssetMapper（详情 JOIN 六关联名称）/AssetLogMapper
  - `AssetServiceImpl`：save（barcode 唯一 409 + 六外键存在性 400 + 状态强制 IDLE + 写"新增"日志）、updateById（状态不随编辑变更）、`changeStatus(assetId, newStatus, operatorId, note)`（M04/M05 统一流转入口，写"【状态】由【旧值】变更为【新值】"格式日志）、discard、page/listBy（7 维筛选 + 批量名称回填）、listLogs
  - `AssetController`：GET 分页 / GET /export / GET 详情 / POST 新增 / PUT 编辑 / POST /{id}/discard / GET /{id}/logs（状态无编辑端点，只能业务流转）
  - DTO：AssetReq/AssetResp（含 statusLabel）/AssetQuery/AssetLogResp/AssetExportRow/AssetDiscardReq
  - 测试 147/147（AssetStatusTest 状态机全路径 7 + AssetServiceImplTest 15 + AssetControllerTest 13）；`POST /api/v1/assets/import` 批量导入留待 M08 历史迁移一并实现

- [x] **M04 领用/借用单（ARE/BOR）审批流**（2026-08-21，前端契约对齐）：
  - 迁移 `V20260823__receipt_user_name_snapshot.sql`：receive_receipt 加 applicant_name/approver_name、asset_allocation 加 user_name（ADR-0004 asset 库不存用户主数据 → 姓名业务时点快照，对齐 department/operator_label 快照模式）
  - `enums/receipt`：ReceiptType（RECEIVE/BORROW + 单号前缀 ARE/BOR）/ ReceiptStatus（PENDING/APPROVED/REJECTED）
  - `entity/mapper/service/controller/dto` 的 receipt 上下文：ReceiveReceipt（主表）+ ReceiveReceiptItem（明细，回填资产编码/名称/序列号）+ AssetAllocation（持有关系）
  - `ReceiveReceiptServiceImpl`：create（资产存在性 404 + 待审批占用 409 领用借用互斥 + serial_no 锁定读取号 + 明细写入 + 资产→PENDING_CONFIRM）、approve（PENDING 校验 409 + 审批人≠申请人 403 + 资产→IN_USE + asset 持有人更新 + asset_allocation 写入）、reject（资产→IDLE + approveRemark 记录原因）、list/getById（明细批量回填）；全部 @Transactional，任一资产流转失败整体回滚
  - serial_no 生成：`likeRight + orderByDesc + LIMIT 1 FOR UPDATE` 锁定读串行取号（当天按前缀分别自增），uk_receipt_serial_no 兜底
  - `AllocationServiceImpl`：list（assetId/userId/type/active 筛选 + 资产名称回填，为前端"退库/归还"提供 allocation id 查询渠道）+ returnAllocation（持有关系闭环 + 资产→IDLE + 持有人仍为本记录持有人时清空）
  - `AssetService.changeStatus` 增加显式操作类型重载（M04 日志区分「领用/借用/归还」；原签名行为不变）
  - 端点：POST/GET /api/v1/receipts、GET /api/v1/receipts/{id}、POST /{id}/approve、POST /{id}/reject、GET /api/v1/allocations、POST /api/v1/allocations/{id}/return
  - 测试 185/185（新增 38：ReceiveReceiptServiceImplTest 16 + AllocationServiceImplTest 4 + ReceiveReceiptControllerTest 13 + AllocationControllerTest 5）；前端 M04 页面（worktree stash）可按此契约联调

- [x] **M03 补充：资产编码改为系统自动生成**（2026-08-21，用户验收反馈）：
  - 迁移 `V20260824__asset_barcode_auto_generate.sql`：asset_category 加 barcode_prefix（种子对齐旧系统前缀：SKSCDM 镀膜/SKSCFZ 辅助/SKSCJC 检测/SKBGDN IT数码，见 M08 迁移文档）
  - 生成规则：`{分类前缀}-{yyyyMMdd}-{4位序号}`（如 SKSCDM-20260821-0001）；前缀缺省回退 SK；取号复用 ARE/BOR 单号模式（likeRight + orderByDesc + LIMIT 1 FOR UPDATE，uk_asset_barcode 兜底）
  - 日期段与旧系统编码（前缀-序号）命名空间隔离 → M08 历史 563 条 upsert 不冲突
  - API 契约：POST /assets 传 barcode 即忽略（兼容旧前端）；PUT 不传 barcode 保持原编码、传则改码（唯一校验照旧）
  - 前端待适配：新增弹窗隐藏资产编码输入框，保存后从响应取生成编码展示

- [x] **修复：在用报废悬死持有关系**（2026-08-21，用户验收反馈 SFBGIT2528 场景）：
  - 根因：IN_USE→DISCARD 合法但 asset_allocation 无人闭环；退库因 DISCARD→IDLE 非法被 409 卡死，领用&退库页永远显示"持有中"（Ciyo 参考系统同样缺陷且更糟：退库会静默覆盖报废状态）
  - 修复：changeStatus 落 DISCARD 联动闭环持有中 allocation（returned_at=now + note 记报废原因）+ 清空资产持有人 + 报废日志追加原持有人；PENDING_CONFIRM→DISCARD 本就被状态机阻止，待审批单据不受影响
  - 测试 190/190（新增 3：在用报废闭环回归 + 闲置报废跳过 + InUse 无持有更新计数）

- [x] **M05 调拨单（ATR）审批流**（2026-08-21）：
  - 迁移 `V20260825__transfer_order_name_snapshot.sql`：transfer_order 加姓名快照列（applicant_name/from_user_name/to_user_name/confirmer_name，对齐 V20260823 M04 快照模式）+ reject_reason（拒绝原因）；asset_allocation.type 枚举扩至 TRANSFER（已本地库实测应用）
  - `enums/transfer`：TransferStatus（PENDING/COMPLETED/CANCELLED/REJECTED）+ TransferSource（MANUAL/INVENTORY_TRIGGERED，M07 盘点触发预留）
  - `entity/mapper/service/controller/dto` 的 transfer 上下文：TransferOrder（主表）+ TransferOrderItem（明细，回填资产编码/名称/序列号 + 调出/调入位置名称）
  - `TransferOrderServiceImpl`：create（调入区域/部门至少一项 400 + 资产存在 404 + 报废资产 409 + 待确认调拨占用 409 + 待审批领用/借用占用 409（与 M04 互斥）+ 调入位置存在性 400 + ATR 单号锁定读串行取号；**调拨不锁定资产状态**，调出位置取首台资产当前位置）、confirm（调入方确认：资产 location/user_id/user_department 更新 + 旧持有关系闭环 + 新建调入方持有记录 type=TRANSFER + 写"调拨"日志（【位置】/【部门】/【使用人】变更明细，不改状态）+ 待确认期间资产报废 409 拦截；确认人≠发起人 403）、reject（资产不变，记录拒绝原因）、cancel（仅发起人可撤 403，资产不变）；全部 @Transactional
  - `AssetService` 新增 `writeLog`（不改状态写日志，M06 实物信息变更复用）
  - M04 反向占用校验：领用/借用 create 增加待确认调拨单占用检查（双向互斥，防止两单据同时操作同一资产）
  - 端点：POST/GET /api/v1/transfers、GET /api/v1/transfers/{id}、POST /{id}/confirm、POST /{id}/reject、POST /{id}/cancel（列表支持 status/source/userId/dept/date 筛选）
  - 测试 229/229（新增 39：TransferOrderServiceImplTest 22 + TransferOrderControllerTest 14 + M04 反向占用 1 + writeLog 2）；前端 M05 调拨页面（菜单已预留 comingSoon）可按此契约开发
  - **待跟进**：V20260825/V20260828 需在测试库 172.16.5.247 手动执行；前端持有关系列表（领用&退库/借用&归还按 type 过滤）不显示 TRANSFER 持有记录，需前端"全部"视图或后端 type 参数扩展（M05 持有经调拨产生的用户退库场景）

- [x] **修复：调拨持有一致性**（2026-08-21，用户验收反馈"在用却无人持有"矛盾）：
  - 根因：confirm 原逻辑只在指定负责人时建持有，且只填区域时旧持有闭环后持有人字段不清零——终态既非"有持有"也非"无持有"
  - 修复（终态二选一）：填使用人 → 人持有（TRANSFER）→ IN_USE；只填部门 → 部门持有（user_id=NULL, department=新部门）→ IN_USE；只填区域 → 调拨回库（闭环旧持有 + user_id/user_department 归零）→ IDLE；状态联动走 changeStatus 状态机（同态跳过，DISCARD 前置拦截不受影响）
  - 迁移 `V20260828__allocation_user_id_nullable.sql`：asset_allocation.user_id 改可空（NULL=部门持有，M04 人持有不受影响），已本地库实测应用
  - E2E 三场景实测通过（部门持有 IDLE→IN_USE / 部门持有回库闭环归零 IDLE / 人持有回库闭环归零 IDLE），持有记录零悬死；日志含"（回库）"标记与【状态】联动条目
  - 测试 272/272（surefire 全量口径，新增 3：部门持有 + 回库 + IDLE→IN_USE 联动）

- [x] **M06 实物信息变更单（AOC）**（2026-08-21）：
  - 迁移 `V20260826__change_order_target_values.sql`：change_order 加姓名快照列（applicant_name/confirmer_name，对齐 M04/M05 快照模式）+ 变更后目标值列（new_user_id/new_user_name/new_user_department/new_location_id/new_location_detail/new_company_id，**null = 不变更**，清空类操作走资产编辑）；asset_allocation.type 枚举扩至 CHANGE（已本地库实测应用，flyway 至 v20260826）
  - `enums/change`：ChangeStatus（PENDING/CONFIRMED/CANCELLED）+ ChangeField（变更字段白名单：user_id/user_department/location_id/location_detail/company_id，不含编码/分类等固有属性）
  - `entity/mapper/service/controller/dto` 的 change 上下文：ChangeOrder（主表，一单 = N 台资产 + 一组统一新值）+ ChangeOrderItem（明细，**每台资产的每个实际变化字段一行**，value_before/value_after 存展示值——位置/公司存名称、使用人存姓名，供变更前/后对比与 AOC 打印格式）
  - `ChangeOrderServiceImpl`：create（至少一项变更字段 400 + 资产存在 404 + 报废 409 + 待审批领用/借用占用 409 + 待确认调拨占用 409 + 待确认变更单自身互斥 409 + 变更后位置/公司存在性 400 + AOC 单号锁定读串行取号 + 明细行仅记实际变化字段、指定字段与当前值一致 400"无变更内容"）；confirm（更新 asset 归属字段 + **使用人变化时同步持有关系**：闭环旧 allocation + 新建 type=CHANGE 持有（避免"在用报废悬死持有"同类脱节）+ 写"实物信息变更"日志（明细行 before/after 拼装，与单据严格一致）+ 待确认期间资产报废 409 拦截；**信息修正单据允许发起人自己确认**——区别于 M04/M05 审批流，制单与执行常为同一资产管理员）；cancel（仅发起人可撤 403，资产不变）；全部 @Transactional
  - **M04/M05 反向占用校验**：领用/借用与调拨的 create 均增加待确认变更单占用检查（三向互斥闭环，防止单据同时操作同一资产）
  - 端点：POST/GET /api/v1/change-orders、GET /api/v1/change-orders/{id}、POST /{id}/confirm、POST /{id}/cancel（列表支持 status/userId/assetId/date 筛选，**assetId 按明细行反查该资产的变更历史**）
  - 测试 267/267（新增 40：ChangeOrderServiceImplTest 24 + ChangeOrderControllerTest 12 + M04/M05 反向占用 2）；`Map.of()` 空集 NPE 坑：不可 null-key 查询，名称批量查询空集返回 HashMap
  - **待跟进**：V20260826 需在测试库 172.16.5.247 手动执行；旧系统 AOC 打印格式（Excel File 5）前端打印预览待 M-FE 对接；本地 6006 端口若跑着 M06 之前的旧实例需重启后才含变更单端点

- [x] **M04 补充：领用区域必填 + 审批更新资产位置**（2026-08-21，用户验收反馈"领用后位置不变，盘点会错"）：
  - 迁移 `V20260827__receipt_location.sql`：receive_receipt 加 location_id（存量单 NULL，审批跳过位置更新兼容过渡）
  - 申请契约：`locationId` @NotNull 必填（缺省 400"领用区域不能为空"）+ create 校验位置存在性（400）
  - 审批联动：approve 时资产 `location_id` 更新为领用区域（与持有人同一 update），日志追加"领用区域：XX"；列表/详情回填 `locationName`；home_location_id（归属位置）不动，归还/盘亏可追溯"家"位置
  - 测试 270/270（新增 3：位置缺失 400 ×2 + 存量单跳过位置更新 + 位置 SET 断言）；前端待适配：申请弹窗加"领用区域"必选下拉（GET /api/v1/locations 取数）

- [x] **M07 盘点（Stocktake）**（2026-08-24）：
  - 迁移 `V20260829__stocktake_creator_name_and_transfer_ref.sql`：stocktake 加 creator_name（对齐 M04/M05/M06 快照模式）；transfer_order 加 stocktake_id（盘点触发调拨关联 + 防重复生成 + 反查来源）
  - `enums/stocktake`：StocktakeStatus（PENDING→IN_PROGRESS→COMPLETED/CANCELLED）+ StocktakeItemStatus（PENDING/MATCHED/LOCATION_MISMATCH/NOT_FOUND/EXTRA）
  - `entity/mapper/service/controller/dto` 的 stocktake 上下文：Stocktake（主表，范围=位置含子树/分类，统计计数回填）+ StocktakeItem（明细，expected=创建时账面位置快照）
  - `StocktakeServiceImpl`：create（范围校验 400 + 报废排除 + 空范围 400 + 明细快照批量写入）、start（PENDING 409 校验）、scanItem（提交实际位置判定 MATCHED/LOCATION_MISMATCH，notFound 标记盘亏；已盘不可重盘 409）、scanByBarcode（PDA 入口：任务内资产更新明细/范围外已登记资产记盘盈 EXTRA/未登记 404/报废 409）、complete（剩余待盘批量记盘亏 + 逐台写"盘点处理"日志）、report（五态汇总 + 差异明细，进行中可看实时统计）、createTransfers（位置不符按实际位置分组生成 source=INVENTORY_TRIGGERED 调拨单，任务须 COMPLETED + stocktake_id 防重 409；EXTRA 盘盈不自动调拨，人工处置）、cancel（仅 PENDING/IN_PROGRESS 且创建人 403）；全部 @Transactional
  - `TransferOrderService` 新增 create 重载（显式 source + stocktakeId，原方法委托 source=MANUAL 不变）；TransferOrder/TransferResp 透传 stocktakeId
  - 端点：POST/GET /api/v1/stocktakes、GET /{id}、POST /{id}/start、POST /{id}/cancel、GET /{id}/items?status=、POST /{id}/items/{itemId}/scan、POST /{id}/scan（PDA 按条码）、POST /{id}/complete、GET /{id}/report、POST /{id}/transfer
  - 统计用 SQL groupBy（selectMaps 别名规避 map-underscore 配置差异）；复用 M06 教训：Map.of() 空 map 不可 null-key 查询（范围/位置为 null 时先判空再 get）
  - 测试 314/314（新增 44：StocktakeServiceImplTest 28 + StocktakeControllerTest 15 + TransferOrderServiceImplTest 1 盘点触发重载）
  - **待跟进**：V20260829 需在测试库 172.16.5.247 手动执行；本地 6006 重启后 Flyway 自动落地；PDA 扫码前端（uni-app）Phase 4 独立会话实现

- [x] **B1 定向待办（assignee 模型）**（2026-08-27，commit 8afbe18，审批中心方案 §3-B1）：
  - 迁移 `V20260830__approval_assignee.sql`：receive_receipt/transfer_order/change_order 加 assignee_user_id（NULL=共享池，存量数据不动）+ 索引
  - 三单提交体/列表 Query/Resp 透传 assigneeUserId；service 校验收紧：提交时 assignee 不能是申请人自己（死单防御）+ 存在性校验（comm_public_basic sys_user）；approve/reject/confirm 操作人必须 = assignee（NULL 保持非发起人即可）
  - `auth/UserDirectory`：独立 JDBC 只读 sys_user（`app.user-directory.*` 配置，与 M08 `app.migration.auth-db-*` 刻意分离——生命周期/权限/降级语义独立）；未配置时存在性校验降级跳过、搜索 503
  - `GET /api/v1/users?keyword=&page=&size=`：用户搜索（工号/姓名模糊，分页），F3 选人器数据源
  - 测试：UserDirectoryTest + 三单 service 测试（assignee 校验分支 + 存量 NULL 回归）

- [x] **B2 通知中心**（2026-08-27，审批中心方案 §3-B2）：
  - 迁移 `V20260831__notification.sql`：sys_notification 表（user_id/type/title/biz_type/biz_id/read_flag + user_id,read_flag,id 索引）
  - `notification/` 上下文：SysNotification 实体 + NotificationType 枚举（DOC_SUBMITTED/APPROVED/REJECTED/COMPLETED）+ NotificationService（notify 与业务同事务写入；接收人空静默跳过；标题超长截断 200）
  - 端点：GET /api/v1/notifications（分页，unread=true 仅未读）、GET /unread-count、POST /{id}/read（404 防越权）、POST /read-all
  - 写入点：三单定向提交→通知 assignee；审批通过/拒绝→通知申请人（M04 领用借用 + M05 调拨）；变更确认→通知申请人（M06）；共享池单据无定向接收人不通知
  - 测试：NotificationServiceImplTest 9 + 三单 service 集成断言

- [x] **B3 统计聚合**（2026-08-27，审批中心方案 §3-B3）：
  - `GET /api/v1/stats/overview`：assetStatusCounts（GROUP BY status，AssetStatus 枚举全集补 0）/ myTodoCount（三单 PENDING 定向我或共享池；领用借用调拨排除我发起，变更允许自审含我发起——与审批中心"待我处理"口径一致）/ myHoldingCount（asset.user_id=我 且 IN_USE）/ inProgressStocktakeCount（我创建的进行中盘点，用户确认口径）
  - `stats/` 上下文：StatsService 纯 count 聚合，无缓存；简单索引列等值查询
  - 测试 372/372（`mvn test` 全量通过）；Flyway V20260830/V20260831 本地库实测落地

- [x] **CI/CD：push 自动部署演练沙箱 + 一键回滚**（2026-08-28，commit 44b52e8）：
  - `.gitlab-ci.yml` 扩展为两阶段：`verify`（mvn clean verify，纯 Mockito/MockMvc 单测无 DB 依赖）→ `deploy`（任意分支 push 自动触发）
  - 部署目标：演练沙箱 **192.0.2.1**（Lighthouse）；流程 = mvn package → 备份沙箱当前 jar 到 `/srv/backups/asset-backend.jar.prev` → scp 推新 jar → `docker compose up -d --build asset-backend` 重建容器 → 探活（sleep 20s + `/actuator/health` UP 才算成功）
  - **一键回滚**：`rollback` 任务 `when: manual`（Pipeline 页手动触发），恢复 `.jar.prev` 并重建容器；首次部署前无备份时明确报错退出
  - Runner 为 sirpinaple 本机 shell executor（Mac），复用本机 Maven 仓库；非交互 shell 统一补齐 PATH（brew/sdkman/nvm）+ JAVA_HOME；SSH 免密 `id_ed25519`
  - **注意**：任意分支 push 都会部署沙箱——功能未完成时慎 push（或接受沙箱短暂处于半成品状态，可 rollback 回退）

- [x] **方案文档两份**（docs/，2026-08-27/28）：
  - [资产台账OA完整解决方案.md](./docs/资产台账OA完整解决方案.md)：责任到人的全生命周期台账 OA 方案 v1.0（持有关系双轨制/离职对账/双链路留痕等核心设计），对外讲解与评审用总纲
  - [资产数据流转说明.md](./docs/资产数据流转说明.md)：领用/退库/调拨的完整数据流转（依据 M04/M05/B1/B2 实际代码，含状态机与两条不变式），前后端联调与测试用对照文档
  - 两文档均为未跟踪文件，随下批业务改动一并提交

- [x] **分类两级结构重构 + 资产"细则"字段**（2026-08-28，依据《资产规范.docx》，工作区待提交）：
  - 迁移 `V20260832__category_tree_and_asset_spec.sql`（本地库已实测应用）：
    - 分类重构为两级：4 大类（房屋建筑 SKFW / 办公设施 SKBG / 运输设备 SKYS / 生产设备 SKSC，sort 1-4）+ 子类（办公设施：IT数码 SKBGIT / 空调 SKBGKT / 辅助办公 SKBGFZ / 家具 SKBGJJ；生产设备：镀膜 SKSCDM / 清洗 SKSCQX / 移印 SKSCYX / 烘箱 SKSCHX / 检测 SKSCJC / 辅助 SKSCFZ / 虚拟资产 SKSCXN）；复用原 9 行中 8 行挂树（566 条资产零换绑），新增清洗/移印/家具 3 子类；「A资产」7 条资产引用迁至辅助设备后逻辑删除；全类前缀覆盖（含大类，资产直挂大类不再回退 SK）
    - asset 表新增 `spec`（细则，varchar(500)）：同品牌型号的配置差异（如内存大小）；Asset 实体/AssetReq(@Size 500)/AssetResp/AssetExportRow（导出列"细则"）全链贯通
  - `generateBarcode` 前缀向上继承：分类自身无 prefix 沿 parent 链取最近非空值（防回退 SK）
  - CategoryServiceImpl 健壮性（页面维护分类的护栏）：名称必填 + 同级重名 400 + 父分类必须存在且为一级（两级上限）+ 不得把自己设为父（防环）+ 带子分类不得再挂父 + code 冲突友好 400（预检 + DuplicateKeyException 兜底，逻辑删除行占用也覆盖）+ 删除保护（有子分类/被资产引用 400，删除时释放 code 供重建）；CategoryController 补 POST/PUT/DELETE 端点（前端 M02 页面契约早已调用）；CategoryReq 补 barcodePrefix
  - 验证：mvn compile 通过；测试 392/392（CategoryServiceImplTest 重写 17 个覆盖全部校验分支 + CategoryControllerTest 补 7 个增删改用例）；本地库迁移后核对：分类树两级 15 行活跃、资产 category_id 悬空引用 0、前缀全覆盖、辅助设备 100 条（93+7 迁入）
  - **待跟进**：V20260832 需在测试库 172.16.5.247 手动执行；本地 6006 需重启加载新代码（spec 字段 + 分类写端点）；前端适配已完成（feat/m02-basedata：资产弹窗/列表"细则"字段 + 分类页两级树）
- [x] **前端 feat/m02-basedata 契约对齐**（2026-08-28 第二批，工作区待提交）：
  - 分类：code 后端自动生成（`CAT{id}`，前端契约不提交 code）；update 修复误清存量 code（请求 code 为空时保持库中原值，如 V20260832 的 COATING）
  - 位置：LocationServiceImpl 补同级重名拦截（save/update，400 带原因）；删除保护错误码 409→400 对齐前端契约（有子位置/被资产当前位置或应归放位置引用，countAssetRefs 已覆盖 location_id OR home_location_id）；code 后端自动生成（`LOC{id}`）；LocationReq.sortOrder 已有无需改
  - 资产 spec：上批已全链贯通（Req/Resp/导出"细则"列），AssetQuery 不支持 spec 搜索（前端仅展示，符合预期）
  - 验证：全量测试 400/400（位置服务 +6 用例、分类服务 +2 用例）

- [x] **资产使用人/管理员姓名实时反查**（2026-08-28 第三批，工作区待提交，测试 404/404）：
  - 背景：前端资产列表/详情"使用人"显示原始 ID（asset 库不存用户主数据，ADR-0004 跨库无法 JOIN）
  - UserDirectory 补 `namesByIds(Collection<Long>)`：一条 JDBC `SELECT id,username,name,dept FROM sys_user WHERE id IN (...) AND deleted=0`，IN 分批上限 1000；降级语义对齐约定——未配置连接返回空 Map（名称留空不阻塞列表）、查询失败 500 上抛
  - AssetResp 新增 `userName`/`adminUserName`；填充位置：fillRelations（page/listBy，userId+adminUserId 合并一次批量反查）+ getById（selectByIdWithRelations JOIN 后单独反查）；未命中/被删除用户 name=null（前端兜底 —）
  - **修复潜在 NPE**：`Map.of().get(null)` 抛 NPE——闲置资产 userId/adminUserId 为 null 时未配置降级路径会炸；namesByIds 空返回改 `Collections.emptyMap()`（null-key 安全）+ fillUserNames 判空守卫，新用例已覆盖
  - **userDepartment 口径决策（已定，告知前端）**：保持业务时点快照（领用/调拨/变更确认时写入），**不做实时化**——与 M04/M05/B1 的 department/operator 快照凭证语义一致；userName 实时、userDepartment 快照，两者可能不一致属预期

- [x] **B4 领用/借用单两级审批链 + 审批链配置**（2026-08-28，依据《资产领用与借用操作流程指导》，工作区待提交）：
  - 迁移 `V20260833__two_level_approval.sql`：
    - 新表 `approval_config`（config_type/config_key/approver_user_id + uk(type,key) + 逻辑删除，删除时置空 key 释放唯一键对齐 Category 模式）
    - receive_receipt 加列：approval_step（1=待部门主管审 2=待领料仓管理员审）+ 两级审批人快照（step1/step2_user_id/name/source_key）+ 一级审批时间/意见（step1_at/step1_remark）；存量单 step 回填 1、链快照 NULL → 按旧共享池语义处理（回归保护测试覆盖）
  - **三个开放问题的后端决策（已告知前端）**：① 两级同人（主管兼仓管）= 合并为一次审批（step 直接 2，一级时间/说明留痕）；② 部门匹配 = 精确优先 + 按路径逐级向上回退（兼容 `/` 与 `-` 两种分隔符，给组织调整留余地）；③ approval_step1_remark 保留（合并留痕已用）
  - `ApprovalChainResolver`（approval 上下文）：提交时解析两级审批人（一级 DEPT_SUPERVISOR+发起人 sys_user.dept 实时取，二级 WAREHOUSE_KEEPER+领用区域 location_id）；兜底 = 400 阻止提交（一级/二级缺失、审批人已失效、审批人=申请人本人死单防御）+ **REQUIRES_NEW 独立事务告警 systemAdmin**（外层事务回滚告警不丢）；解析结果冻结快照写入单据，人员调动不影响在途单
  - `ReceiveReceiptServiceImpl` 改造：create（审批链解析替代手选 assignee，assigneeUserId 已废弃忽略；解析失败 400 + 告警；assignee = 当前层级快照审批人——审批中心"待我处理"语义复用零改动）；approve（链单据按 step 分派：一级通过 → step 推进 2 + assignee 物理推进 + 通知二级审批人与发起人进度；二级/合并 → 现有终态逻辑不动）；reject（任一级拒绝整单 REJECTED，通知带拒绝层级）；撤销/存量单逻辑不变
  - `ApprovalConfigServiceImpl` + `/api/v1/approval-configs` CRUD（**仅 systemAdmin 403 门禁**；校验：类型合法、WAREHOUSE_KEEPER 键=存在的位置 id、审批人存在 UserDirectory、(type,key) 唯一预检 + DuplicateKeyException 兜底转 400"已配置审批人"）；分页 keyword 模糊 + 审批人姓名/位置名称回填（configKeyLabel）
  - `GET /api/v1/receipts/approval-preview?locationId=`：发起预解析（返回两级审批人姓名 + resolvable/message，解析失败不抛 400 供前端弹窗提示）；列表/详情 Resp 透传 approvalStep/两级审批人姓名/step1At 字段
  - 测试 449/449 全量通过（新增 45：ApprovalChainResolverTest 11 + ApprovalConfigServiceImplTest 14 + ApprovalConfigControllerTest 11 + ReceiveReceiptServiceImplTest 扩至 35 含链路/合并/兜底/存量回归）
  - **待跟进**：V20260833 需在测试库 172.16.5.247 手动执行；本地 6006 重启后 Flyway 自动落地；前端分工（配置页/详情抽屉审批链进度/发起弹窗预览）待后端就绪后联调

## 进行中

- [ ] **B5 钉钉多级主管审批链开发**（方案定稿 2026-09-03，改动清单见上方时间线；执行顺序：Flyway 建表+同步器 → 多级链解析器+入口A+建单快照 → 入口B tasks 时序实测 → 全量单测 → 部署验证。注意事项：张三/李四/王五 dd_user_id 从 sys_user 读取勿硬编码；approval_config 两类配置保留不删；调拨/变更单审批契约不变；版本号纪律照常）
- [ ] 示例丰/锐鑫智能 DEPT_SUPERVISOR 主管配置（李四，随 B5 一并落地或 SQL 直写）
- [ ] 钉钉领用模板"领用区域"单选框补 5 个新有效仓位选项（人事行政闲置仓/研发闲置仓/财务部闲置仓/示例科技设备仓（大型资产）/示例科技设备仓（各部门其他闲置资产））——钉钉后台人工操作
- [ ] 新建 37 个账号（831~867）通知本人改密（当前默认 a123456）
- [ ] **M08-A/B 历史数据迁移执行验证**（代码完成 2026-08-24，已提交 76a3604，测试 340/340）：
  - `migration/` 包（一次性工具模块）：MigrationService（编排：基础数据→资产→领用单→调拨单→日志）+ MigrationController（`POST /api/v1/migration/run`，双重门禁：`app.migration.enabled` + `asset-资产管理员` 角色）+ OperatorTextParser（操作人文本→工号/姓名）+ LogContentParser（content→diff_json）+ AuthUserDirectory（sys_user 匹配/建号，独立 JDBC 直连 comm_public_basic 库）
  - **实测源数据规模**（旧系统 2026-08-18 导出）：资产 563 / 领用单 **162**（M08 文档记 346 系含空行口径）/ 调拨单 56 / 日志 **1773**（文档记 1775 偏差 2 条）
  - 幂等策略：资产/单据按 barcode/serial_no upsert；**asset_log 按"时间区段清除+重灌"**（created_at < 2026-08-19 的日志只可能来自迁移，run 开始时清除后重写，含 563 条"迁移导入"标记日志）；持有关系仅补建（已有持有中跳过）
  - 用户匹配：工号（SK\d+）→ 姓名唯一命中 → 重名取最小 id（警告）→ 未命中**自动创建 sys_user 账号**（SK 工号段顺延，BCrypt 默认密码 123456，remark 标记来源；默认密码用户被 comm_public_basic 拦在业务系统外，改密后可登录）
  - 关键映射决策：报废/闲置资产清使用人（对齐 M04 归还/M03 报废联动不变量）；在用资产补建 asset_allocation（type=RECEIVE + note 标记）；领用单/调拨单导出**无资产明细列**只迁主表（items 空，待审批单在新系统无明细可审批——数据局限，见 MigrationService 类注释）；规格型号并入 remark（"规格：xxx"）；操作人含"+盘点"后缀的调拨单 source=INVENTORY_TRIGGERED；位置树按 Excel 区域路径逐级建节点，叶子段同名顶级种子节点收养归位（不产生双节点）
  - **有意偏离 M08 文档**："通过 service 层方法写入"改为直写 mapper——业务方法会重新生成编码、强制状态、以当前时间写日志，与迁移保真冲突
  - 配置：`app.migration.*`（enabled/xlsx-dir/auth-db-*，默认关闭；本地已配 ~/Downloads + 本地 comm_public_basic 库）；AssetLog 实体补 diffJson 字段（V20260819 建表已有列）
  - 测试 340/340（新增 26：OperatorTextParser 8 + LogContentParser 7 + AuthUserDirectory 8 + 状态映射 3）
  - **待执行**：重启本地 6006（新代码已编译进 target/classes）→ Redis 管理员 token（如 `73075822b7a34ab8976b9634f1f56d52`）或前端登录态调 `POST /api/v1/migration/run` → 按结果 warnings 复核；注意本地测试资产 SFBGIT2528/SFBGIT2563 在 Excel 中，迁移会将其重置为历史基线（预期行为）

## 待办（按模块分阶段，详见 docs/modules/）

### Phase 1 — 骨架（所有模块的前提）
1. ~~**M01-A** 后端骨架~~（已完成，2026-08-19）
2. ~~**M01-B** 鉴权接入：AuthPort + TokenFilter + UserContext + `/api/v1/me` 验证接口~~（已完成，2026-08-19）
3. ~~**M-FE01** 前端骨架~~（已完成，2026-08-19）

### Phase 2 — 基础数据（M03+ 的外键依赖）
6. ~~**M02** 基础数据 CRUD：company / asset_category / asset_location(树) / manufacturer / supplier / asset_model~~（已完成，2026-08-20）

### Phase 3 — 核心业务（按依赖顺序）
7. ~~**M03** 资产主表 CRUD + 状态机（IDLE/IN_USE/DISCARD/PENDING_CONFIRM）~~（已完成，2026-08-22）
8. ~~**M04** 领用/借用单（ARE/BOR）审批流：申请→审批→资产状态联动（`receive_receipt.type` 区分领用/借用，共用单据流）~~（已完成，2026-08-21）
9. ~~**M05** 调拨单（ATR）审批流：调出→调入确认→归属更新~~（已完成，2026-08-21）
10. ~~**M06** 实物信息变更单（AOC）：变更前/后记录 + 确认执行~~（已完成，2026-08-21）
11. ~~**M08-A/B** 历史数据迁移：563条资产 + 162条领用单 + 56条调拨单 + 1773条日志~~（代码完成 2026-08-24，执行验证见"进行中"）

### Phase 4 — 支撑功能（Phase 3 完成后）
12. ~~**M07** 盘点（Stocktake）：扫码核对 + 差异处理 + 触发调拨~~（已完成，2026-08-24）
13. ~~CI 流水线：编译 + 测试~~（已完成，2026-08-20 GitLab CI verify；2026-08-28 扩展为 verify + 自动部署沙箱 + 手动回滚，见"CI/CD"条目）
14. **M09** 折旧（低优先级，需 asset.amount 有真实数据才有意义）
15. 审批中心前端 F3/F4 适配（后端 B1/B2/B3 已就绪：待办列表/通知中心/工作台统计）
16. PDA 扫码前端（uni-app，M07 盘点移动端入口，独立会话实现）

## 关键决策记录

| ADR | 主题 | 状态 |
|---|---|---|
| [0001](./docs/adr/0001-技术栈选型.md) | 技术栈：Spring Boot 3 + MySQL + Vue3 + Element Plus | Accepted（版本由 0004 修订为 3.x） |
| [0002](./docs/adr/0002-DDD分层架构与Maven多模块.md) | DDD 分层 + Maven 多模块 | **Superseded by 0005** |
| [0003](./docs/adr/0003-多Agent上下文传承机制.md) | AGENTS.md + ADR + STATUS 分层传承 | Accepted |
| [0004](./docs/adr/0004-鉴权复用comm_public_basic与SpringBoot3解耦.md) | 鉴权复用 comm_public_basic + HTTP 解耦 + 钉钉 | Accepted |
| [0005](./docs/adr/0005-架构降级为单模块三层.md) | 单模块三层（Superseded 0002） | Accepted |
| [0006](./docs/adr/0006-数据库核心表设计.md) | 数据库核心表设计：model中间层/manufacturer+supplier分离/asset_allocation/depreciation_rule | Accepted |
| [0007](./docs/adr/0007-钉钉OA审批双向集成.md) | 钉钉 OA 审批双向集成（outbox + Stream 回传 + 站内兜底） | Accepted |

### B4 资产责任归属编排决策（2026-08-31，用户确认）

1. **归还带回位置**：`asset_allocation` 增加 `location_before` 发放前位置快照（V20260835）；领用/借用发放时冻结资产当前位置（A 区），归还时资产位置回置快照（存量记录 NULL → 回退 `home_location_id`，两者皆空保持不动）；归还接口无位置参数——**带回位置由快照唯一决定，不可手改**。
2. **责任归属语义**：有使用人（领用/借用发起人）→ 保管责任在使用人；无使用人（调拨回库等）→ 责任落到对应区域管理员。`asset.admin_user_id` 废弃静态语义，改为**随位置实时解析**（`ApprovalConfigService.keeperUserIdOf(locationId)`，数据源=WAREHOUSE_KEEPER 审批链配置）：领用/借用审批通过、归还回置、调拨确认、变更单改位置四处联动更新。
3. **审批层级维持两级**（部门主管 + 区域仓管），不加统一经理终审。
4. **变更单保持高维自由更改能力**（位置/使用人/公司/位置明细随意改，不受"归还带回快照"约束——那是归还链路的专属语义）。

### B5 钉钉多级主管审批链决策（2026-09-03，用户确认，方案定稿待开发）

1. **审批链结构**：领用/借用（入口A+入口B）审批链改为「张三（固定一级）→ 发起人所在部门逐级向上主管（直接主管→部门主管→…）」；终态抄送李四、张三、王五三人。
2. **站内快照语义**：系统只记两级快照——step1=张三、step2=发起人直接主管；快照外审批节点（第3级及以上的更上级主管）的钉钉操作**只记入 approval_instance.callbacks 日志**，不落单据字段。
3. **终态严格同步**：单据结束状态与钉钉实例一致——仅当实例 COMPLETED+agree 才关单（bfdd63c 已落地），任何一级 refuse 即拒，terminate 按拒绝语义；钉钉同意后站内自动推进/关单，使用人无需站内重复操作（回调侧零改动，能力已就绪）。
4. **主管数据源**：本地表 dingtalk_dept 缓存钉钉部门树+主管，启动全量同步 + 每日刷新；同步失败仅告警不阻断启动。
5. **未设主管部门**：一律逐级向上回退（如 PD课→项目一科张胜瑶→研发部姚强），不填临时主管；**示例丰/锐鑫智能**（不在钉钉树/无主管）主管指定为李四。
6. **降级一致性**：钉钉推送失败降级站内审批时，站内链同样走「张三+直接主管」新语义（仓管级退出领用/借用）；approval_config 的 DEPT_SUPERVISOR/WAREHOUSE_KEEPER 配置**保留不删**（调拨/变更单及历史在途单据仍依赖）。
7. **不改动的部分**：调拨/变更单审批契约（单审批人）不变；回调侧（状态机/终态同步/日志/并发/幂等）零改动。
8. **既有数据决策**：示例科技采购科 DEPT_SUPERVISOR 保留明超（钉钉主管为郭磊，用户拍板不改）。

## 阻塞 / 待决策

- （无。原两项阻塞已于 2026-08-19 解除，核实结论见"M01-B 前置阻塞项解除"条目）

## 本地开发环境备忘（M01-A/M01-B 实测，2026-08-19；CI/沙箱 2026-08-28 补充）

- **演练沙箱**：192.0.2.1（Lighthouse），`/srv/app/asset-backend/` + `/srv/docker-compose.yml`（容器 asset-backend）；部署与回滚全由 GitLab CI 代劳（push 即部署，Pipeline 页手动 rollback），**不要手工改沙箱 jar**——会被下次 CI 部署覆盖，且丢失备份链
- 后端端口 **6006**；本地库 `db_sk_asset`（127.0.0.1:3306，实际为 MySQL 9.6 Homebrew 版，非 MariaDB）
- `application-local.yml`（gitignore）存连接信息 + comm_public_basic 地址（127.0.0.1:6002），模板 `application-local.yml.example`
- comm_public_basic 本地（私改版）：端口 6002，库 `db_comm_public_basic`（127.0.0.1:3306），token TTL -1 永久；登录 `POST /login/check` 密码需 RSA 公钥加密（PKCS#1 v1.5，公钥见其 application.yml；openssl 命令：`printf '密码' | openssl pkeyutl -encrypt -pubin -inkey pub.pem | base64`，公钥包 X.509 PEM 头尾）
- ⚠️ admin/123456 为**默认密码用户**：comm_public_basic 策略拦截其进入业务系统（userSystemAuth 返回空角色 → asset-backend 403）。联调 200 成功路径需使用改过密码或新建的用户
- asset 已注册 `basic_portal_project`（id=22，**is_visible=0, is_enabled=0**；comm_public_basic 约定 0=显示/启用 1=隐藏/禁用，与前端直觉相反——应用中心按 0/0 过滤展示）
- 联调账号：assetfe（`Asset@2026`）/ SK9802（`Sk9802@2026`），均分配 role_id=350 asset-资产管理员；⚠️ 重置密码会设回默认 123456 → 触发"默认密码用户不得进入业务系统"拦截 → userSystemAuth 返回空角色 → 403，改密码即可恢复
- ⚠️ **M02 种子数据脚本版本号必须晚于 20260819**（V1 已占用 `V20260819`，M02 文档中的 `V20260819__seed_base_data.sql` 需改为实施当日版本号，否则 Flyway 撞号）
- ⚠️ knife4j 只引入 `knife4j-openapi3-ui` 静态 webjar：其 4.5.0 增强 starter 与 springdoc 2.3+ 不兼容（`getGroupConfigs` 移除触发 NoSuchMethodError），勿改回 `knife4j-openapi3-jakarta-spring-boot-starter` + `knife4j.enable=true`
- Flyway 对 MySQL 9.6 报"未测试版本"警告（正常，迁移已成功；如遇问题可显式升级 flyway 版本）

## 参考资料索引

- 业务模型参考：`references/ciyo-itasset/ciyo-asset/`（itam 下的 Device/Categories/Locations/Models/Manufacturers/Suppliers/Accessories/Consumables/Licenses/Stocktakes/Allocations/AssetRequests/Failures/Depreciations）
- 工程规范参考：`references/asset-mgmt-system/`（Flyway V1-V5、Docker、Swagger、测试）
- 鉴权复用对象：`/Users/zhuanzmima0000/Documents/git/comm_public_basic/`（本地私改版，仅读源码理解逻辑；原始版在 GitLab `gitlab.example.com/tt_java/comm_public_basic`）
- 业务标杆：Snipe-IT（GitHub snipe/snipe-it，字段最全）

---

**最后更新**：2026-09-03（**生产运营阶段**：M10 全链路上线生产（0.1.3 = ba1ac99）；全量资产初始化导入完成（6799 条 + 钉钉现架构位置树重建 + 历史仓软删策略）；人事排查台账修正 129 条笔记本归属 + 37 新账号；**B5 钉钉多级主管审批链已部署生产（0.1.7 = acbb439 + 150b5ef，541 单测全绿，部门树 73 部门同步完成，待实机联调）**。后端 main = 150b5ef，前端 main = ef8bb44）
**当前阶段负责人**：待指派
