-- =====================================================================
-- V20260826__change_order_target_values.sql
-- M06 实物信息变更单（AOC）：姓名快照列 + 变更后目标值列。
--
-- 依据 ADR-0004：asset 库不存用户/权限主数据（用户在 comm_public_basic），
-- 变更单列表/详情需展示发起人、确认人姓名，无法 JOIN 本库取到；
-- 沿用 M04/M05 快照模式（V20260823 / V20260825）：
--   1. change_order.applicant_name —— 发起变更时写入（getAuth 返回的 name）
--   2. change_order.confirmer_name  —— 确认执行时写入
--
-- 一单 = N 台资产 + 一组统一的新值（M06 模块文档："指定资产列表 + 各字段新值"），
-- 变更后目标值存主表（明细行 change_order_item 只存变更前/后展示值供对比与打印）：
--   3. new_user_id / new_user_name / new_user_department —— 变更后使用人（null = 不变更）
--   4. new_location_id / new_location_detail —— 变更后位置（null = 不变更）
--   5. new_company_id —— 变更后归属公司（null = 不变更）
-- 约定：所有 new_* 列 null = 该字段不变更（清空类操作走资产编辑，不在变更单语义内）。
--
-- 另：asset_allocation.type 枚举扩至 CHANGE —— 确认执行且使用人变化时，
-- 闭环旧持有记录 + 新建新使用人持有记录（避免"在用报废悬死持有"同类脱节问题）。
-- 历史数据（M08 迁移）names 为空，由迁移脚本按需回填。
-- =====================================================================

ALTER TABLE change_order
    ADD COLUMN applicant_name     VARCHAR(100)    NULL COMMENT '发起人姓名（提交时快照，comm_public_basic 用户）' AFTER applicant_user_id,
    ADD COLUMN confirmer_name     VARCHAR(100)    NULL COMMENT '确认人姓名（确认执行时快照）' AFTER confirmer_user_id,
    ADD COLUMN new_user_id        BIGINT UNSIGNED NULL COMMENT '变更后使用人ID（null=不变更）' AFTER reason,
    ADD COLUMN new_user_name      VARCHAR(100)    NULL COMMENT '变更后使用人姓名（发起时快照）' AFTER new_user_id,
    ADD COLUMN new_user_department VARCHAR(100)   NULL COMMENT '变更后使用人部门（null=不变更）' AFTER new_user_name,
    ADD COLUMN new_location_id    BIGINT UNSIGNED NULL COMMENT '变更后位置（asset_location.id，null=不变更）' AFTER new_user_department,
    ADD COLUMN new_location_detail VARCHAR(200)   NULL COMMENT '变更后存放位置明细（null=不变更）' AFTER new_location_id,
    ADD COLUMN new_company_id     BIGINT UNSIGNED NULL COMMENT '变更后归属公司（company.id，null=不变更）' AFTER new_location_detail;

ALTER TABLE asset_allocation
    MODIFY COLUMN type VARCHAR(20) NOT NULL DEFAULT 'RECEIVE' COMMENT '类型：RECEIVE-领用 BORROW-借用 TRANSFER-调拨 CHANGE-变更';
