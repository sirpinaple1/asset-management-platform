package com.sk.asset.dto.change;

import com.sk.asset.enums.change.ChangeStatus;
import lombok.Data;

import java.time.LocalDate;

/**
 * 变更单列表查询条件（GET /api/v1/change-orders）。
 */
@Data
public class ChangeQuery {

    private ChangeStatus status;

    /** 发起人 ID */
    private Long userId;

    /** 指定处理人 ID（精确匹配；B1 定向待办） */
    private Long assigneeUserId;

    /** true=仅共享池单据（assignee_user_id IS NULL） */
    private Boolean unassigned;

    /** 资产 ID（查某台资产的变更历史，按明细行反查主表） */
    private Long assetId;

    /** 申请日期（yyyy-MM-dd，按 created_at 当天过滤） */
    private LocalDate date;
}
