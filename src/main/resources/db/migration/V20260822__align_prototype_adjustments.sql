-- =====================================================================
-- V20260822__align_prototype_adjustments.sql
-- 前端原型对齐（2026-08-20 决策，见 docs/modules/M04-领用单.md 与 STATUS.md）：
-- 1. manufacturer/supplier 加 status 启停列 —— 原型基础设置页"全部/已停用" tabs
--    （停用≠删除：停用仅从下拉选择中排除，数据保留）
-- 2. receive_receipt 加 type 列 —— 借用并入 M04 单据流（RECEIVE-领用 BORROW-借用），
--    serial_no 前缀 ARE/BOR 区分，历史数据默认 RECEIVE
-- 3. asset_allocation 加 type 列 —— 持有性质快照（RECEIVE/BORROW），
--    前端"领用&退库""借用&归还"两菜单共用归还机制，按 type 区分展示
-- =====================================================================

ALTER TABLE manufacturer
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-停用' AFTER address;

ALTER TABLE supplier
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用 0-停用' AFTER address;

ALTER TABLE receive_receipt
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'RECEIVE' COMMENT '类型：RECEIVE-领用 BORROW-借用' AFTER serial_no;

ALTER TABLE asset_allocation
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'RECEIVE' COMMENT '类型：RECEIVE-领用 BORROW-借用' AFTER user_id;
