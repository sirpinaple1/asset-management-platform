-- =====================================================================
-- V20260828__allocation_user_id_nullable.sql
-- M05 持有一致性修复：asset_allocation.user_id 改为可空。
--
-- 背景：调拨确认的终态一致性要求（避免"在用却无人持有"矛盾）——
--   只填部门（没填负责人）时需新建"部门持有"记录（user_id=NULL,
--   department=新部门, type=TRANSFER），原 NOT NULL 约束导致插入 500。
-- user_id=NULL 语义：部门级持有（无人指定），与 M04 的人持有（RECEIVE/BORROW，
-- user_id 必填）不冲突；历史数据不受影响。
-- =====================================================================

ALTER TABLE asset_allocation
    MODIFY COLUMN user_id BIGINT UNSIGNED NULL COMMENT '持有人ID（NULL=部门持有，如调拨只指定部门；comm_public_basic 用户）';
