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

    /** 资产 ID（查某台资产的变更历史，按明细行反查主表） */
    private Long assetId;

    /** 申请日期（yyyy-MM-dd，按 created_at 当天过滤） */
    private LocalDate date;
}
