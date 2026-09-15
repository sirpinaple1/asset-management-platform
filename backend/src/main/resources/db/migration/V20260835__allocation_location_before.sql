-- B4 责任归属编排（2026-08-31 决策）：归还带回位置
-- 持有记录增加"发放前位置快照"：领用/借用发放时把资产当前位置（A 区）冻结进快照，
-- 归还时资产位置回置到快照（接口无位置参数，位置不可手改）；存量记录 NULL → 回退 home_location_id
ALTER TABLE asset_allocation
    ADD COLUMN location_before BIGINT NULL COMMENT '发放前位置快照（归还时回置资产位置，不可手改；存量 NULL 回退 home_location_id）' AFTER company_id;
