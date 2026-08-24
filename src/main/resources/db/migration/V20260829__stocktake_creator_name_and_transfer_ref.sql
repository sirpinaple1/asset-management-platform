-- M07 盘点：
-- 1) stocktake 加创建人姓名快照（对齐 M04/M05/M06 姓名/部门快照模式，ADR-0004 asset 库不存用户主数据）
-- 2) transfer_order 加 stocktake_id 关联（盘点差异触发 source=INVENTORY_TRIGGERED 调拨单时写入，
--    用于调拨单反查盘点来源 + 同一盘点任务防重复生成调拨单）
ALTER TABLE stocktake
    ADD COLUMN creator_name VARCHAR(100) NULL COMMENT '创建人姓名（提交时快照）' AFTER creator_user_id;

ALTER TABLE transfer_order
    ADD COLUMN stocktake_id BIGINT UNSIGNED NULL COMMENT '关联盘点任务（stocktake.id，盘点触发调拨时写入）' AFTER source,
    ADD KEY idx_transfer_stocktake (stocktake_id);
