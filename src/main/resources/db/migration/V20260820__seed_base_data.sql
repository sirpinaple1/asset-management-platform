-- =====================================================================
-- V20260820__seed_base_data.sql（M02 种子数据）
-- 插入核心基础数据：1 条公司、4 个资产分类、5 个区域位置。
-- 完整历史数据迁移由 M08 负责。
-- =====================================================================

-- 1. 公司主体（1 条）
INSERT INTO company (code, name, remark) VALUES
('SK', '森科五金(深圳)有限公司', '主体公司');

-- 2. 资产分类（4 个核心分类，顶级节点）
INSERT INTO asset_category (name, code, sort_order) VALUES
('镀膜设备',          'COATING',    10),
('辅助设备',          'AUXILIARY',  20),
('检测设备',          'TESTING',    30),
('IT设备、数码产品',   'IT_DIGITAL', 40);

-- 3. 区域位置（5 个顶级节点，path 简化为 /id/，M08 迁移时补全层级）
INSERT INTO asset_location (name, code, path, sort_order) VALUES
('森科物料仓',   'MATERIAL_WH',     '/1/', 10),
('森科设备仓',   'EQUIPMENT_WH',    '/2/', 20),
('IT部在用仓',   'IT_INUSE_WH',     '/3/', 30),
('IT部闲置仓',   'IT_IDLE_WH',      '/4/', 40),
('设备维护仓',   'MAINTENANCE_WH',  '/5/', 50);
