-- B1 定向待办（assignee 模型）：
-- 三张审批单据主表加"指定处理人"列。NULL = 共享池（任何非发起人可处理），
-- 非 NULL = 仅该用户可审批/确认（service 层校验）。
-- 存量数据全 NULL → 行为与加列前完全一致，前端共享池语义无需变更。
ALTER TABLE receive_receipt
    ADD COLUMN assignee_user_id BIGINT UNSIGNED NULL COMMENT '指定处理人ID（comm_public_basic 用户，NULL=共享池）' AFTER applicant_user_id,
    ADD KEY idx_receipt_assignee (assignee_user_id);

ALTER TABLE transfer_order
    ADD COLUMN assignee_user_id BIGINT UNSIGNED NULL COMMENT '指定处理人ID（comm_public_basic 用户，NULL=共享池）' AFTER applicant_user_id,
    ADD KEY idx_transfer_assignee (assignee_user_id);

ALTER TABLE change_order
    ADD COLUMN assignee_user_id BIGINT UNSIGNED NULL COMMENT '指定处理人ID（comm_public_basic 用户，NULL=共享池）' AFTER applicant_user_id,
    ADD KEY idx_change_assignee (assignee_user_id);
