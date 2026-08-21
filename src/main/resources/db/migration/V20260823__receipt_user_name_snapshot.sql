-- =====================================================================
-- V20260823__receipt_user_name_snapshot.sql
-- M04 领用/借用单：用户姓名快照列。
--
-- 依据 ADR-0004：asset 库不存用户/权限主数据（用户在 comm_public_basic），
-- 单据列表/详情需展示申请人、审批人、持有人姓名，无法 JOIN 本库取到；
-- 采用业务时点快照（对齐既有快照模式：asset.user_department、
-- asset_log.operator_label、receive_receipt.department）：
--   1. receive_receipt.applicant_name —— 提交申请时写入（getAuth 返回的 name）
--   2. receive_receipt.approver_name —— 审批（批准/拒绝）时写入
--   3. asset_allocation.user_name     —— 审批通过发放时写入
-- 历史数据（M08 迁移 346 条 ARE 单）names 为空，由迁移脚本按需回填。
-- =====================================================================

ALTER TABLE receive_receipt
    ADD COLUMN applicant_name VARCHAR(100) NULL COMMENT '申请人姓名（提交时快照，comm_public_basic 用户）' AFTER applicant_user_id,
    ADD COLUMN approver_name  VARCHAR(100) NULL COMMENT '审批人姓名（审批时快照）' AFTER approver_user_id;

ALTER TABLE asset_allocation
    ADD COLUMN user_name VARCHAR(100) NULL COMMENT '持有人姓名（发放时快照）' AFTER user_id;
