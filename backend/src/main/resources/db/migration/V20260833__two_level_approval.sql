-- 领用/借用单两级审批链（部门主管 → 领料仓管理员，依据《资产领用与借用操作流程指导》）：
-- 一、approval_config 审批链配置表（仅 systemAdmin 可管理）：
--    config_type = DEPT_SUPERVISOR 时 config_key = 部门路径字符串（发起人 sys_user.dept 实时取）；
--    config_type = WAREHOUSE_KEEPER 时 config_key = 领用区域 id（asset 库 asset_location.id，字符串存储）。
--    解析顺序：一级按发起人部门（精确优先，逐级向上回退），二级按单据领用区域。
-- 二、receive_receipt 加两级审批快照列：
--    提交时解析两级审批人并冻结快照（后续人员调动不影响在途单据）；
--    approval_step：1=待部门主管审，2=待领料仓管理员审（两级同一人时直接置 2，合并为一次审批）；
--    存量数据回填：approval_step=1，有 assignee 的单据 approval_step1_user_id=assignee（approval_step2_user_id 为 NULL
--    → 走旧单层审批语义，assignee/共享池一次审批终态，兼容过渡）。
CREATE TABLE approval_config (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_type      VARCHAR(30)  NOT NULL COMMENT '配置类型：DEPT_SUPERVISOR-部门主管 WAREHOUSE_KEEPER-领料仓管理员',
    config_key       VARCHAR(255) DEFAULT NULL COMMENT 'DEPT_SUPERVISOR=部门路径字符串；WAREHOUSE_KEEPER=位置id（字符串）；逻辑删除时置 NULL 释放唯一键',
    approver_user_id BIGINT UNSIGNED NOT NULL COMMENT '审批人ID（comm_public_basic sys_user）',
    remark           VARCHAR(500) DEFAULT NULL COMMENT '备注',
    deleted          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_approval_config_type_key (config_type, config_key),
    KEY idx_approval_config_approver (approver_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审批链配置（两级审批人路由，仅超管管理）';

ALTER TABLE receive_receipt
    ADD COLUMN approval_step INT NOT NULL DEFAULT 1 COMMENT '当前审批层级：1=待部门主管审 2=待领料仓管理员审（两级同一人时提交即置2合并）' AFTER assignee_user_id,
    ADD COLUMN approval_step1_user_id BIGINT UNSIGNED DEFAULT NULL COMMENT '一级审批人ID（部门主管，提交时解析冻结快照）' AFTER approval_step,
    ADD COLUMN approval_step1_name VARCHAR(100) DEFAULT NULL COMMENT '一级审批人姓名（提交时快照）' AFTER approval_step1_user_id,
    ADD COLUMN approval_step1_at DATETIME DEFAULT NULL COMMENT '一级审批通过时间' AFTER approval_step1_name,
    ADD COLUMN approval_step1_remark VARCHAR(500) DEFAULT NULL COMMENT '一级审批意见（预留，当前 approve 接口无意见入参）' AFTER approval_step1_at,
    ADD COLUMN approval_step1_source_key VARCHAR(255) DEFAULT NULL COMMENT '一级解析依据快照（命中的部门路径配置键，审计追溯）' AFTER approval_step1_remark,
    ADD COLUMN approval_step2_user_id BIGINT UNSIGNED DEFAULT NULL COMMENT '二级审批人ID（领料仓管理员，提交时解析冻结快照；NULL=存量单走旧单层审批）' AFTER approval_step1_source_key,
    ADD COLUMN approval_step2_name VARCHAR(100) DEFAULT NULL COMMENT '二级审批人姓名（提交时快照）' AFTER approval_step2_user_id,
    ADD COLUMN approval_step2_source_key VARCHAR(255) DEFAULT NULL COMMENT '二级解析依据快照（命中的位置id配置键，审计追溯）' AFTER approval_step2_name,
    ADD KEY idx_receipt_step2_user (approval_step2_user_id);

-- 存量兼容：approval_step 默认已 1；有指定处理人的存量单把 assignee 冻结为一级审批人快照（二级留 NULL 保持旧语义）
UPDATE receive_receipt
   SET approval_step1_user_id = assignee_user_id
 WHERE assignee_user_id IS NOT NULL;
