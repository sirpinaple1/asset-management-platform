-- 退还单（入口 B）资产明细快照：导入时把解析命中的资产 id 落库（逗号分隔），
-- 供审批中心"我发起的"列表展示退还资产明细（此前仅终审执行时才解析，列表无资产信息）
ALTER TABLE approval_instance
    ADD COLUMN asset_ids VARCHAR(512) NULL COMMENT '关联资产id（退还单导入时快照，逗号分隔）' AFTER result;
