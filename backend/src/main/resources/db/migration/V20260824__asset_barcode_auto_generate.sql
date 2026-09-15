-- =====================================================================
-- V20260824__asset_barcode_auto_generate.sql
-- M03 补充：新增资产编码改为系统自动生成（分类前缀-日期-序号）。
--
-- 前缀沿用旧系统（yideamobile）编码约定（见 M08-历史数据迁移.md）：
--   SKSCDM → 镀膜设备（COATING）
--   SKSCFZ → 辅助设备（AUXILIARY）
--   SKSCJC → 检测设备（TESTING）
--   SKBGDN → IT设备/数码产品（IT_DIGITAL）
-- 新生成格式：前缀-yyyyMMdd-4位序号（如 SKSCDM-20260821-0001），
-- 与旧资产编码（前缀-序号，无日期段）命名空间隔离，M08 迁移 upsert 不冲突。
-- 分类未配置前缀时回退通用前缀 SK。
-- =====================================================================

ALTER TABLE asset_category
    ADD COLUMN barcode_prefix VARCHAR(20) NULL COMMENT '资产编码前缀（新增资产自动生成编码用，空则回退 SK）' AFTER code;

UPDATE asset_category SET barcode_prefix = 'SKSCDM' WHERE code = 'COATING';
UPDATE asset_category SET barcode_prefix = 'SKSCFZ' WHERE code = 'AUXILIARY';
UPDATE asset_category SET barcode_prefix = 'SKSCJC' WHERE code = 'TESTING';
UPDATE asset_category SET barcode_prefix = 'SKBGDN' WHERE code = 'IT_DIGITAL';
