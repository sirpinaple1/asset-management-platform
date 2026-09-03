-- 钉钉事件日志表（M10 补强）：Stream 事件先落库后处理。
-- 目的：
--   1) eventId 持久化去重——钉钉可能重复推送同一事件（Stream 重连/补推），
--      内存条带锁重启即失，唯一键兜底防重复处理；
--   2) 事件不丢——回调恒回 SUCCESS（防 LATER 无限重推），应用崩溃/重启瞬间
--      到达的事件钉钉不会重发，落表后 FAILED/RECEIVED 滞留行可人工排查回放。
-- 状态流转：RECEIVED（落库）→ PROCESSED（处理成功）/ IGNORED（业务拦截、非本企业、
-- 空数据等预期忽略）/ FAILED（基础设施异常，已告警，需人工介入）。
-- 仅记录 bpms_* 审批事件（Stream 订阅全部事件，其余类型无业务含义不落表）。

CREATE TABLE dingtalk_event_log (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    event_id              VARCHAR(64)   NOT NULL COMMENT '钉钉事件推送唯一id（Stream 头 eventId，去重键）',
    event_type            VARCHAR(100)  NOT NULL COMMENT '事件类型（bpms_task_change / bpms_instance_change）',
    process_instance_id   VARCHAR(64)   NULL COMMENT '审批实例id（从 data 提取，排查索引）',
    corp_id               VARCHAR(64)   NULL COMMENT '事件企业标识',
    payload               TEXT          NULL COMMENT '事件原始 data JSON（审计/回放）',
    status                VARCHAR(20)   NOT NULL DEFAULT 'RECEIVED' COMMENT 'RECEIVED/PROCESSED/IGNORED/FAILED',
    error                 VARCHAR(1000) NULL COMMENT 'IGNORED/FAILED 时的原因说明',
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dingtalk_event_id (event_id),
    KEY idx_dingtalk_event_created (event_type, created_at),
    KEY idx_dingtalk_event_status (status)
) COMMENT '钉钉事件日志（先落库后处理：eventId 幂等去重 + 事件不丢可查）';
