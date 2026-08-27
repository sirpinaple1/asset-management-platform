package com.sk.asset.dto.receipt;

import com.sk.asset.enums.receipt.ReceiptStatus;
import com.sk.asset.enums.receipt.ReceiptType;
import lombok.Data;

import java.time.LocalDate;

/**
 * 领用/借用单列表查询条件（GET /api/v1/receipts）。
 */
@Data
public class ReceiptQuery {

    private ReceiptType type;

    private ReceiptStatus status;

    /** 申请人 ID */
    private Long userId;

    /** 指定处理人 ID（精确匹配；B1 定向待办） */
    private Long assigneeUserId;

    /** true=仅共享池单据（assignee_user_id IS NULL） */
    private Boolean unassigned;

    /** 申请日期（yyyy-MM-dd，按 created_at 当天过滤） */
    private LocalDate date;
}
