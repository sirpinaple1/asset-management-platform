-- =====================================================================
-- V20260832__category_tree_and_asset_spec.sql
-- 依据《资产规范.docx》：
--   1. 资产分类重构为规范两级结构：4 大类（房屋建筑/办公设施/运输设备/生产设备）
--      + 子类。复用现有 7 个与规范重合的分类行挂到新建大类下（避免存量资产
--      大换绑），新增缺失子类（清洗设备/移印设备/家具）与空大类
--      （房屋建筑/运输设备，子类留给用户页面维护）。
--   2. 补齐所有分类 barcode_prefix（SK + 大类码 + 子类码，沿用 SKSCDM 风格）：
--      大类 SKFW/SKBG/SKYS/SKSC；子类 SKBGIT/SKBGKT/SKBGFZ/SKBGJJ、
--      SKSCDM/SKSCQX/SKSCYX/SKSCHX/SKSCJC/SKSCFZ/SKSCXN。
--   3. 「A资产」（测试垃圾分类）下的资产引用迁至「辅助设备」后逻辑删除。
--   4. asset 表新增 spec（细则）字段：同品牌型号下的配置差异（如内存大小）。
-- 排序约定：大类 sort_order 1-4；子类 = 大类序号 * 10 + 大类内顺序号
-- （办公设施 21-24，生产设备 41-47），前端按 parentId 组树、层级内有序。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. asset 新增细则字段
-- ---------------------------------------------------------------------
ALTER TABLE asset
    ADD COLUMN spec VARCHAR(500) NULL COMMENT '细则（同品牌型号的配置差异，如内存大小）' AFTER sn;

-- ---------------------------------------------------------------------
-- 2. 新增四个一级大类（房屋建筑/运输设备暂无子类，页面后续维护）
-- ---------------------------------------------------------------------
INSERT INTO asset_category (name, code, barcode_prefix, parent_id, sort_order, remark) VALUES
('房屋建筑', 'BUILDING',   'SKFW', NULL, 1, '固定资产大类（《资产规范》），子类待页面维护'),
('办公设施', 'OFFICE',     'SKBG', NULL, 2, '固定资产大类（《资产规范》）'),
('运输设备', 'TRANSPORT',  'SKYS', NULL, 3, '固定资产大类（《资产规范》），子类待页面维护'),
('生产设备', 'PRODUCTION', 'SKSC', NULL, 4, '固定资产大类（《资产规范》）');

-- ---------------------------------------------------------------------
-- 3. 新增缺失子类：家具→办公设施；清洗设备/移印设备→生产设备
-- ---------------------------------------------------------------------
INSERT INTO asset_category (name, code, barcode_prefix, parent_id, sort_order) VALUES
('家具', 'FURNITURE', 'SKBGJJ',
 (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'OFFICE' AND deleted = 0) t), 24),
('清洗设备', 'CLEANING', 'SKSCQX',
 (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'PRODUCTION' AND deleted = 0) t), 42),
('移印设备', 'PAD_PRINTING', 'SKSCYX',
 (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'PRODUCTION' AND deleted = 0) t), 43);

-- ---------------------------------------------------------------------
-- 4. 现有分类挂到大类下，补齐前缀与排序
--    注：IT_DIGITAL 前缀由 SKBGDN 调整为 SKBGIT（子类码规范），
--    只影响新生成编码，历史资产编码不变。
-- ---------------------------------------------------------------------
-- 办公设施（OFFICE）子类
UPDATE asset_category SET parent_id = (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'OFFICE' AND deleted = 0) t),
       barcode_prefix = 'SKBGIT', sort_order = 21
WHERE code = 'IT_DIGITAL' AND deleted = 0;

UPDATE asset_category SET parent_id = (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'OFFICE' AND deleted = 0) t),
       barcode_prefix = 'SKBGKT', sort_order = 22
WHERE name = '空调' AND parent_id IS NULL AND deleted = 0;

UPDATE asset_category SET parent_id = (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'OFFICE' AND deleted = 0) t),
       barcode_prefix = 'SKBGFZ', sort_order = 23
WHERE name = '辅助办公设备' AND parent_id IS NULL AND deleted = 0;

-- 生产设备（PRODUCTION）子类
UPDATE asset_category SET parent_id = (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'PRODUCTION' AND deleted = 0) t),
       barcode_prefix = 'SKSCDM', sort_order = 41
WHERE code = 'COATING' AND deleted = 0;

UPDATE asset_category SET parent_id = (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'PRODUCTION' AND deleted = 0) t),
       barcode_prefix = 'SKSCHX', sort_order = 44
WHERE name = '烘箱设备' AND parent_id IS NULL AND deleted = 0;

UPDATE asset_category SET parent_id = (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'PRODUCTION' AND deleted = 0) t),
       barcode_prefix = 'SKSCJC', sort_order = 45
WHERE code = 'TESTING' AND deleted = 0;

UPDATE asset_category SET parent_id = (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'PRODUCTION' AND deleted = 0) t),
       barcode_prefix = 'SKSCFZ', sort_order = 46
WHERE code = 'AUXILIARY' AND deleted = 0;

UPDATE asset_category SET parent_id = (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'PRODUCTION' AND deleted = 0) t),
       barcode_prefix = 'SKSCXN', sort_order = 47
WHERE name = '虚拟资产（盘点不看）' AND parent_id IS NULL AND deleted = 0;

-- ---------------------------------------------------------------------
-- 5. 「A资产」分类（测试垃圾分类，实际被 7 条资产引用）：
--    资产/型号引用迁至「辅助设备」（AUXILIARY），分类本身逻辑删除
-- ---------------------------------------------------------------------
UPDATE asset
SET category_id = (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'AUXILIARY' AND deleted = 0) t)
WHERE category_id IN (SELECT id FROM (SELECT id FROM asset_category WHERE name = 'A资产' AND deleted = 0) t2);

UPDATE asset_model
SET category_id = (SELECT id FROM (SELECT id FROM asset_category WHERE code = 'AUXILIARY' AND deleted = 0) t)
WHERE category_id IN (SELECT id FROM (SELECT id FROM asset_category WHERE name = 'A资产' AND deleted = 0) t2);

UPDATE asset_category
SET deleted = 1, remark = '测试垃圾分类，V20260832 废弃（引用已迁至辅助设备）'
WHERE name = 'A资产' AND deleted = 0;
