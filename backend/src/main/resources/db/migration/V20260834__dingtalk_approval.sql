-- M10 钉钉 OA 审批联动（ADR-0007 / 《钉钉OA审批集成方案》2026-08-28 已确认决策）
-- approval_instance：系统单据 ⇄ 钉钉审批实例映射（outbox + 事件审计）
-- 单据表追加 dingtalk_instance_id 冗余列（审批中心区分站内审批 / 钉钉审批展示用）

CREATE TABLE approval_instance (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type              VARCHAR(20)  NOT NULL COMMENT 'RECEIVE/BORROW/TRANSFER/CHANGE（LEAVE_RETURN 预留）',
    biz_id                BIGINT       NOT NULL COMMENT '业务单据 id（receive_receipt/transfer_order/change_order）',
    process_code          VARCHAR(64)  NOT NULL COMMENT '钉钉审批模板唯一码',
    process_instance_id   VARCHAR(64)  NULL COMMENT '钉钉审批实例 id，创建成功后回填',
    title                 VARCHAR(255) NULL COMMENT '审批标题（钉钉事件回传）',
    originator_dd_user_id VARCHAR(64)  NULL COMMENT '发起人钉钉 userid（提交时快照）',
    sync_status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SYNCED/FAILED',
    sync_error            VARCHAR(500) NULL COMMENT '同步/回调失败原因（排查 + 重推依据）',
    status                VARCHAR(20)  NULL COMMENT '钉钉实例状态：RUNNING/COMPLETED/TERMINATED',
    result                VARCHAR(20)  NULL COMMENT '钉钉审批结果：agree/refuse',
    callbacks             JSON         NULL COMMENT '事件回调明细（审计，按次追加）',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_approval_instance (process_instance_id),
    KEY idx_approval_biz (biz_type, biz_id)
) COMMENT '钉钉审批实例↔系统单据映射（M10）';

ALTER TABLE receive_receipt ADD COLUMN dingtalk_instance_id VARCHAR(64) NULL COMMENT '钉钉审批实例id（NULL=站内审批）' AFTER status;
ALTER TABLE transfer_order   ADD COLUMN dingtalk_instance_id VARCHAR(64) NULL COMMENT '钉钉审批实例id（NULL=站内审批）' AFTER status;
ALTER TABLE change_order     ADD COLUMN dingtalk_instance_id VARCHAR(64) NULL COMMENT '钉钉审批实例id（NULL=站内审批）' AFTER status;
