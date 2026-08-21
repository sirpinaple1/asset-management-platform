-- =====================================================================
-- V20260827__receipt_location.sql
-- M04 补充：领用/借用申请增加领用区域（必填），审批通过时资产位置
-- 更新为领用区域（盘点按位置扫资产的依据；此前审批只写人/部门、
-- 不碰 location_id，导致领用后资产位置滞留在原存放点）。
--
-- 存量单据 location_id 为 NULL（审批时跳过位置更新，兼容过渡）；
-- 新申请由 DTO @NotNull 强制必填。
-- =====================================================================

ALTER TABLE receive_receipt
    ADD COLUMN location_id BIGINT UNSIGNED NULL COMMENT '领用区域（审批通过后资产位置更新至此）' AFTER department;
