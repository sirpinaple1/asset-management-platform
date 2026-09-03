-- 钉钉多级主管审批链改造（v1.0）：钉钉部门树与主管本地缓存表
-- 数据来源：启动全量 + 每日定时刷新（DingtalkDeptSyncService，topapi/v2/department/listsub
-- 递归遍历 + topapi/v2/department/get 取主管）；同步失败仅告警不阻断启动。
-- 根部门 dept_id=1 不落库（无主管属正常，链在顶层部门自然截止）。
-- special-dept（森丰/锐鑫智能等不在钉钉树内的部门）不走本表，经
-- app.dingtalk.special-dept-managers 配置覆盖（环境相关 userid 不入库，R3 红线）。

CREATE TABLE dingtalk_dept (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    dept_id               BIGINT       NOT NULL COMMENT '钉钉部门id',
    name                  VARCHAR(200) NOT NULL COMMENT '部门名称',
    parent_id             BIGINT       NOT NULL DEFAULT 1 COMMENT '父部门id（根=1）',
    manager_dd_user_ids   VARCHAR(1000) NULL COMMENT '主管钉钉userid，逗号分隔（dept_manager_userid_list）',
    deleted               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：钉钉侧已不存在的部门',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dingtalk_dept_id (dept_id),
    KEY idx_dingtalk_dept_parent (parent_id)
) COMMENT '钉钉部门树与主管（本地缓存，启动全量+每日刷新）';
