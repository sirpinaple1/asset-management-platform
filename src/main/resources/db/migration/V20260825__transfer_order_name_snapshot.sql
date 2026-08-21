-- =====================================================================
-- V20260825__transfer_order_name_snapshot.sql
-- M05 调拨单（ATR）：用户姓名快照列 + 拒绝原因。
--
-- 依据 ADR-0004：asset 库不存用户/权限主数据（用户在 comm_public_basic），
-- 调拨单列表/详情需展示发起人、调出/调入负责人、确认人姓名，
-- 无法 JOIN 本库取到；沿用 M04 快照模式（V20260823，对齐
-- receive_receipt.applicant_name / approver_name）：
--   1. transfer_order.applicant_name —— 发起调拨时写入（getAuth 返回的 name）
--   2. transfer_order.from_user_name  —— 调出方管理员姓名（快照）
--   3. transfer_order.to_user_name    —— 调入方负责人姓名（发起时快照）
--   4. transfer_order.confirmer_name  —— 调入方确认/拒绝时写入
-- 另：
--   5. transfer_order.reject_reason —— 调入方拒绝原因（对齐 receive_receipt.approve_remark）
--   6. asset_allocation.type 枚举扩至 TRANSFER —— 调拨确认后新建调入方持有记录
--      （M05 确认语义：关闭旧持有 + 新建调入方持有，见 M05 模块文档）
-- 历史数据（M08 迁移 56 条 ATR 单）names 为空，由迁移脚本按需回填。
-- =====================================================================

ALTER TABLE transfer_order
    ADD COLUMN applicant_name VARCHAR(100) NULL COMMENT '发起人姓名（提交时快照，comm_public_basic 用户）' AFTER applicant_user_id,
    ADD COLUMN from_user_name VARCHAR(100) NULL COMMENT '调出方管理员姓名（快照）' AFTER from_user_id,
    ADD COLUMN to_user_name   VARCHAR(100) NULL COMMENT '调入方负责人姓名（发起时快照）' AFTER to_user_id,
    ADD COLUMN confirmer_name VARCHAR(100) NULL COMMENT '确认人姓名（调入方确认/拒绝时快照）' AFTER confirmer_user_id,
    ADD COLUMN reject_reason  VARCHAR(500) NULL COMMENT '拒绝原因（调入方拒绝时记录）' AFTER reason;

ALTER TABLE asset_allocation
    MODIFY COLUMN type VARCHAR(20) NOT NULL DEFAULT 'RECEIVE' COMMENT '类型：RECEIVE-领用 BORROW-借用 TRANSFER-调拨';
