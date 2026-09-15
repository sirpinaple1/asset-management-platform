-- =====================================================================
-- V20260819__create_core_tables.sql（M01-A）
-- 资产管理系统核心表结构：一次性建完 19 张核心表。
--
-- 设计依据：ADR-0006（字段级定义以此为准）+ docs/modules/M02~M09 模块文档
--           + M08 历史数据迁移规则 + Snipe-IT 字段参考。
--
-- 约定：
--   * 逻辑外键：不建物理 FOREIGN KEY，跨表引用只建索引（迁移/分库友好）
--   * 公共列：created_at / updated_at 由数据库默认值填充；deleted 逻辑删除
--   * 状态列使用 VARCHAR 枚举，值域见列注释（与 Java 枚举一一对应）
--   * utf8mb4 + InnoDB，排序规则 utf8mb4_general_ci（兼容 MariaDB 与 MySQL 8）
--   * 种子数据不在此脚本：M02 使用晚于本版本号的 seed 脚本（避免 Flyway 撞号）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 基础数据（M02）
-- ---------------------------------------------------------------------

CREATE TABLE company (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(50)     NOT NULL COMMENT '公司编码（如 SK / SF）',
    name       VARCHAR(200)    NOT NULL COMMENT '公司名称',
    remark     VARCHAR(500)    NULL COMMENT '备注',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '公司主体';

CREATE TABLE asset_category (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(200)    NOT NULL COMMENT '分类名称',
    code       VARCHAR(50)     NULL COMMENT '分类编码（可空，唯一）',
    parent_id  BIGINT UNSIGNED NULL COMMENT '父分类ID（空=顶级，支持二级）',
    sort_order INT             NOT NULL DEFAULT 0 COMMENT '排序号（小的在前）',
    remark     VARCHAR(500)    NULL COMMENT '备注',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_code (code),
    KEY idx_category_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '资产分类（支持二级树）';

CREATE TABLE asset_location (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(200)    NOT NULL COMMENT '位置名称',
    code       VARCHAR(50)     NULL COMMENT '位置编码（可空，唯一）',
    parent_id  BIGINT UNSIGNED NULL COMMENT '父位置ID（空=顶级）',
    path       VARCHAR(500)    NOT NULL DEFAULT '/' COMMENT '物化路径（如 /1/5/12/，前缀匹配查子树）',
    sort_order INT             NOT NULL DEFAULT 0 COMMENT '排序号（小的在前）',
    remark     VARCHAR(500)    NULL COMMENT '备注',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_location_code (code),
    KEY idx_location_parent (parent_id),
    KEY idx_location_path (path)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '区域位置树（materialized path）';

CREATE TABLE manufacturer (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(200)    NOT NULL COMMENT '厂商名称（谁生产）',
    contact    VARCHAR(100)    NULL COMMENT '联系人',
    phone      VARCHAR(100)    NULL COMMENT '联系电话',
    address    VARCHAR(500)    NULL COMMENT '地址',
    remark     VARCHAR(500)    NULL COMMENT '备注',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_manufacturer_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '厂商（asset_model 关联）';

CREATE TABLE supplier (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(200)    NOT NULL COMMENT '供应商名称（从谁购买）',
    contact    VARCHAR(100)    NULL COMMENT '联系人',
    phone      VARCHAR(100)    NULL COMMENT '联系电话',
    address    VARCHAR(500)    NULL COMMENT '地址',
    remark     VARCHAR(500)    NULL COMMENT '备注',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_supplier_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '供应商（asset 关联）';

-- 字段集与 ADR-0006 D5 保持一致
CREATE TABLE depreciation_rule (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name               VARCHAR(200)    NOT NULL COMMENT '规则名称',
    method             VARCHAR(20)     NOT NULL COMMENT '折旧方法：STRAIGHT_LINE-直线法（初期仅此一种）',
    useful_life_months INT             NOT NULL COMMENT '使用年限（月）',
    salvage_rate       DECIMAL(5, 2)   NOT NULL DEFAULT 0 COMMENT '残值率（%），如 5.00 表示 5%',
    deleted            TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    company_id         BIGINT UNSIGNED NULL COMMENT '归属公司（company.id）',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_dep_rule_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '折旧规则（结构预留，计算逻辑 M09/Phase 4 实现）';

-- 字段集与 ADR-0006 D1 保持一致
CREATE TABLE asset_model (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(200)    NOT NULL COMMENT '型号名称',
    model_number    VARCHAR(100)    NULL COMMENT '型号编号',
    category_id     BIGINT UNSIGNED NULL COMMENT '分类（asset_category.id）',
    manufacturer_id BIGINT UNSIGNED NULL COMMENT '厂商（manufacturer.id）',
    depreciation_id BIGINT UNSIGNED NULL COMMENT '折旧规则（depreciation_rule.id，可空）',
    eol_months      INT             NULL COMMENT '保废周期（月，可空）',
    notes           VARCHAR(500)    NULL COMMENT '备注/规格说明',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    company_id      BIGINT UNSIGNED NULL COMMENT '归属公司（company.id）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_model_category (category_id),
    KEY idx_model_manufacturer (manufacturer_id),
    KEY idx_model_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '资产型号（asset 与分类/厂商之间的中间层，ADR-0006 D1）';

-- ---------------------------------------------------------------------
-- 2. 资产主数据（M03）
-- ---------------------------------------------------------------------

CREATE TABLE asset (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    barcode          VARCHAR(100)    NOT NULL COMMENT '资产编码（条码，唯一，如 SKSCDM-xxxx）',
    name             VARCHAR(200)    NOT NULL COMMENT '资产名称',
    sn               VARCHAR(100)    NULL COMMENT '序列号（SN）',
    status           VARCHAR(20)     NOT NULL DEFAULT 'IDLE' COMMENT '状态：IDLE-闲置 IN_USE-在用 DISCARD-报废 PENDING_CONFIRM-待确认',
    category_id      BIGINT UNSIGNED NULL COMMENT '分类（asset_category.id）',
    model_id         BIGINT UNSIGNED NULL COMMENT '型号（asset_model.id）',
    supplier_id      BIGINT UNSIGNED NULL COMMENT '供应商（supplier.id，从谁购买）',
    location_id      BIGINT UNSIGNED NULL COMMENT '当前位置（asset_location.id）',
    home_location_id BIGINT UNSIGNED NULL COMMENT '应归放位置（asset_location.id，可空，ADR-0006 D3）',
    location_detail  VARCHAR(200)    NULL COMMENT '存放位置明细（楼层/房间等）',
    user_id          BIGINT UNSIGNED NULL COMMENT '使用人ID（comm_public_basic 用户，仅存 ID 引用）',
    user_department  VARCHAR(100)    NULL COMMENT '使用人部门（快照）',
    admin_user_id    BIGINT UNSIGNED NULL COMMENT '资产管理员ID（comm_public_basic 用户）',
    company_id       BIGINT UNSIGNED NULL COMMENT '归属公司（company.id）',
    purchase_date    DATE            NULL COMMENT '购入日期',
    amount           DECIMAL(12, 2)  NULL COMMENT '购入金额（元，历史数据多为 0，M09 折旧前提）',
    remark           VARCHAR(500)    NULL COMMENT '备注',
    deleted          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_asset_barcode (barcode),
    KEY idx_asset_status (status),
    KEY idx_asset_category (category_id),
    KEY idx_asset_model (model_id),
    KEY idx_asset_location (location_id),
    KEY idx_asset_user (user_id),
    KEY idx_asset_company (company_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '资产主表';

-- 字段集与 ADR-0006 D4 保持一致（当前持有关系快照，区别于审批单据）
CREATE TABLE asset_allocation (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    asset_id     BIGINT UNSIGNED NOT NULL COMMENT '资产（asset.id）',
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '持有人ID（comm_public_basic 用户）',
    department   VARCHAR(100)    NULL COMMENT '持有人部门（快照）',
    allocated_at DATETIME        NOT NULL COMMENT '发放时间',
    returned_at  DATETIME        NULL COMMENT '归还时间（空=持有中）',
    note         VARCHAR(500)    NULL COMMENT '备注',
    company_id   BIGINT UNSIGNED NULL COMMENT '归属公司（company.id）',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_allocation_asset (asset_id, returned_at),
    KEY idx_allocation_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '资产持有关系快照（查"现在在谁手里"）';

CREATE TABLE asset_log (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    asset_id         BIGINT UNSIGNED NOT NULL COMMENT '资产（asset.id）',
    operation_type   VARCHAR(50)     NOT NULL COMMENT '操作类型：新增/领用/归还/调拨/实物信息变更/盘点处理/报废',
    operator_user_id BIGINT UNSIGNED NULL COMMENT '操作人ID（comm_public_basic 用户，迁移数据匹配不到时为空）',
    operator_label   VARCHAR(200)    NULL COMMENT '操作人原始文本（如"PMC部 SK10086 张三"，M08 迁移保底）',
    content          VARCHAR(1000)   NOT NULL COMMENT '操作内容（对齐现有系统"【字段】由【旧值】变更为【新值】"格式）',
    diff_json        JSON            NULL COMMENT '字段变更明细 JSON（M08 历史日志迁移产物）',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间（日志不可变，无更新列）',
    PRIMARY KEY (id),
    KEY idx_log_asset (asset_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '资产操作日志（service 层内置写入，非独立模块）';

-- ---------------------------------------------------------------------
-- 3. 领用单 ARE（M04）
-- ---------------------------------------------------------------------

CREATE TABLE receive_receipt (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    serial_no        VARCHAR(50)     NOT NULL COMMENT '单号：ARE + yyyyMMdd + 4位序号',
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待审批 APPROVED-已批准 REJECTED-已拒绝',
    applicant_user_id BIGINT UNSIGNED NOT NULL COMMENT '申请人ID（comm_public_basic 用户）',
    department       VARCHAR(100)    NULL COMMENT '领用部门',
    reason           VARCHAR(500)    NULL COMMENT '领用事由',
    approver_user_id BIGINT UNSIGNED NULL COMMENT '审批人ID',
    approve_time     DATETIME        NULL COMMENT '审批时间',
    approve_remark   VARCHAR(500)    NULL COMMENT '审批意见 / 拒绝原因',
    company_id       BIGINT UNSIGNED NULL COMMENT '归属公司（company.id）',
    deleted          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_receipt_serial_no (serial_no),
    KEY idx_receipt_status (status),
    KEY idx_receipt_applicant (applicant_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '领用单主表（ARE 单，对应 346 条历史数据）';

CREATE TABLE receive_receipt_item (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    receipt_id BIGINT UNSIGNED NOT NULL COMMENT '领用单（receive_receipt.id）',
    asset_id   BIGINT UNSIGNED NOT NULL COMMENT '资产（asset.id，一单对多资产）',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_receipt_item_receipt (receipt_id),
    KEY idx_receipt_item_asset (asset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '领用单明细';

-- ---------------------------------------------------------------------
-- 4. 调拨单 ATR（M05 / M07 盘点触发）
-- ---------------------------------------------------------------------

CREATE TABLE transfer_order (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    serial_no        VARCHAR(50)     NOT NULL COMMENT '单号：ATR + yyyyMMdd + 4位序号',
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待确认 COMPLETED-已完成 CANCELLED-已撤销 REJECTED-已拒绝',
    source           VARCHAR(20)     NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL-手动调拨 INVENTORY_TRIGGERED-盘点触发',
    applicant_user_id BIGINT UNSIGNED NOT NULL COMMENT '发起人ID（调出方）',
    from_location_id BIGINT UNSIGNED NULL COMMENT '调出位置（asset_location.id）',
    from_user_id     BIGINT UNSIGNED NULL COMMENT '调出方管理员ID',
    to_location_id   BIGINT UNSIGNED NULL COMMENT '调入位置（asset_location.id）',
    to_department    VARCHAR(100)    NULL COMMENT '调入部门',
    to_user_id       BIGINT UNSIGNED NULL COMMENT '调入方负责人ID',
    reason           VARCHAR(500)    NULL COMMENT '调拨原因',
    confirmer_user_id BIGINT UNSIGNED NULL COMMENT '确认人ID（调入方）',
    confirm_time     DATETIME        NULL COMMENT '确认时间',
    company_id       BIGINT UNSIGNED NULL COMMENT '归属公司（company.id）',
    deleted          TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transfer_serial_no (serial_no),
    KEY idx_transfer_status (status),
    KEY idx_transfer_applicant (applicant_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '调拨单主表（ATR 单，对应 56 条历史数据）';

CREATE TABLE transfer_order_item (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id   BIGINT UNSIGNED NOT NULL COMMENT '调拨单（transfer_order.id）',
    asset_id   BIGINT UNSIGNED NOT NULL COMMENT '资产（asset.id，一单对多资产）',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_transfer_item_order (order_id),
    KEY idx_transfer_item_asset (asset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '调拨单明细';

-- ---------------------------------------------------------------------
-- 5. 实物信息变更单 AOC（M06）
-- ---------------------------------------------------------------------

CREATE TABLE change_order (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    serial_no         VARCHAR(50)     NOT NULL COMMENT '单号：AOC + yyyyMMdd + 4位序号',
    status            VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待确认 CONFIRMED-已执行 CANCELLED-已撤销',
    applicant_user_id BIGINT UNSIGNED NOT NULL COMMENT '发起人ID',
    reason            VARCHAR(500)    NULL COMMENT '变更原因',
    confirmer_user_id BIGINT UNSIGNED NULL COMMENT '确认人ID',
    confirm_time      DATETIME        NULL COMMENT '确认执行时间',
    company_id        BIGINT UNSIGNED NULL COMMENT '归属公司（company.id）',
    deleted           TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_change_serial_no (serial_no),
    KEY idx_change_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '实物信息变更单主表（AOC 单）';

-- 字段集与 M06 模块文档保持一致（每台资产的每个变更字段一行）
CREATE TABLE change_order_item (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id     BIGINT UNSIGNED NOT NULL COMMENT '变更单（change_order.id）',
    asset_id     BIGINT UNSIGNED NOT NULL COMMENT '资产（asset.id）',
    field_name   VARCHAR(50)     NOT NULL COMMENT '变更字段名（如 user_id / location_id）',
    field_label  VARCHAR(50)     NOT NULL COMMENT '字段展示名（如 使用人 / 区域）',
    value_before VARCHAR(500)    NULL COMMENT '变更前值',
    value_after  VARCHAR(500)    NULL COMMENT '变更后值',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_change_item_order (order_id),
    KEY idx_change_item_asset (asset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '变更单明细（变更前/后记录）';

-- ---------------------------------------------------------------------
-- 6. 盘点（M07）
-- ---------------------------------------------------------------------

CREATE TABLE stocktake (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(200)    NOT NULL COMMENT '盘点任务名称',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待开始 IN_PROGRESS-进行中 COMPLETED-已完成 CANCELLED-已取消',
    location_id     BIGINT UNSIGNED NULL COMMENT '盘点范围-位置（asset_location.id，空=全库）',
    category_id     BIGINT UNSIGNED NULL COMMENT '盘点范围-分类（asset_category.id，空=全类）',
    creator_user_id BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
    start_time      DATETIME        NULL COMMENT '开始时间',
    complete_time   DATETIME        NULL COMMENT '完成时间',
    remark          VARCHAR(500)    NULL COMMENT '备注',
    company_id      BIGINT UNSIGNED NULL COMMENT '归属公司（company.id）',
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_stocktake_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '盘点任务主表';

CREATE TABLE stocktake_item (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    stocktake_id         BIGINT UNSIGNED NOT NULL COMMENT '盘点任务（stocktake.id）',
    asset_id             BIGINT UNSIGNED NOT NULL COMMENT '资产（asset.id，盘盈资产为扫码后新建的 asset）',
    status               VARCHAR(30)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待盘 MATCHED-账实相符 LOCATION_MISMATCH-位置不符 NOT_FOUND-盘亏 EXTRA-盘盈',
    expected_location_id BIGINT UNSIGNED NULL COMMENT '系统记录位置（asset_location.id）',
    actual_location_id   BIGINT UNSIGNED NULL COMMENT '盘点实际位置（asset_location.id）',
    scanned_at           DATETIME        NULL COMMENT '扫码/确认时间',
    scanned_by_user_id   BIGINT UNSIGNED NULL COMMENT '盘点人ID',
    remark               VARCHAR(500)    NULL COMMENT '备注',
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stocktake_item (stocktake_id, asset_id),
    KEY idx_stocktake_item_asset (asset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '盘点明细（每台资产一行）';

-- ---------------------------------------------------------------------
-- 7. 折旧明细（M09 / Phase 4，结构预留）
-- ---------------------------------------------------------------------

-- 字段集与 M09 模块文档保持一致
CREATE TABLE asset_depreciation (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    asset_id     BIGINT UNSIGNED NOT NULL COMMENT '资产（asset.id）',
    rule_id      BIGINT UNSIGNED NOT NULL COMMENT '折旧规则（depreciation_rule.id）',
    period       VARCHAR(7)      NOT NULL COMMENT '折旧期间（yyyy-MM，如 2026-08）',
    book_value   DECIMAL(12, 2)  NOT NULL COMMENT '期初账面净值',
    depreciation DECIMAL(12, 2)  NOT NULL COMMENT '本期折旧额',
    net_value    DECIMAL(12, 2)  NOT NULL COMMENT '期末账面净值',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_depreciation (asset_id, period)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '资产月度折旧明细（M09/Phase 4 实现计算）';
