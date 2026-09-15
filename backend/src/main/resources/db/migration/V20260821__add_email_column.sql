-- =====================================================================
-- V20260821__add_email_column.sql
-- 修复：Manufacturer/Supplier 实体与 DTO 契约含 email 字段，
-- 但 V20260819 建表时遗漏该列，导致 selectList 报 Unknown column 'email'。
-- 补齐两表 email 列（可空），对齐 API 契约。
-- =====================================================================

ALTER TABLE manufacturer
    ADD COLUMN email VARCHAR(100) NULL COMMENT '电子邮箱' AFTER phone;

ALTER TABLE supplier
    ADD COLUMN email VARCHAR(100) NULL COMMENT '电子邮箱' AFTER phone;
