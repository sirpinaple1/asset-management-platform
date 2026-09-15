-- B2 通知中心：站内通知表。
-- 通知与业务同库同事务写入（单据状态流转的方法上加 @Transactional，通知 insert 参与回滚）。
-- 无逻辑删除：通知只追加 + 标记已读，不物理/逻辑删（量级可控，保留审计轨迹）。
CREATE TABLE sys_notification (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '接收人ID（comm_public_basic 用户）',
    type       VARCHAR(32)     NOT NULL COMMENT '类型：DOC_SUBMITTED-单据待处理 DOC_APPROVED-单据已通过 DOC_REJECTED-单据已拒绝 DOC_COMPLETED-单据已完成',
    title      VARCHAR(200)    NOT NULL COMMENT '通知标题（含单据类型/单号/操作人/原因摘要）',
    biz_type   VARCHAR(16)     NOT NULL COMMENT '业务单据类型：RECEIVE-领用 BORROW-借用 TRANSFER-调拨 CHANGE-变更',
    biz_id     BIGINT UNSIGNED NOT NULL COMMENT '业务单据ID（对应各单据主表 id）',
    read_flag  TINYINT         NOT NULL DEFAULT 0 COMMENT '已读标记：0-未读 1-已读',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（通知不可变，无更新列）',
    PRIMARY KEY (id),
    KEY idx_notification_user_read (user_id, read_flag, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '站内通知（B2 通知中心）';
