package com.sk.asset.dto.transfer;

import com.sk.asset.enums.transfer.TransferSource;
import com.sk.asset.enums.transfer.TransferStatus;
import lombok.Data;

import java.time.LocalDate;

/**
 * 调拨单列表查询条件（GET /api/v1/transfers）。
 */
@Data
public class TransferQuery {

    private TransferStatus status;

    /** 来源：MANUAL-手动调拨 INVENTORY_TRIGGERED-盘点触发 */
    private TransferSource source;

    /** 发起人 ID */
    private Long userId;

    /** 指定处理人 ID（精确匹配；B1 定向待办） */
    private Long assigneeUserId;

    /** true=仅共享池单据（assignee_user_id IS NULL） */
    private Boolean unassigned;

    /** 调入部门（模糊匹配） */
    private String dept;

    /** 申请日期（yyyy-MM-dd，按 created_at 当天过滤） */
    private LocalDate date;
}
