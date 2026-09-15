package com.sk.asset.dto.receipt;

import com.sk.asset.enums.receipt.ReceiptType;
import lombok.Data;

/**
 * 资产持有关系查询条件（GET /api/v1/allocations）。
 */
@Data
public class AllocationQuery {

    private Long assetId;

    /** 持有人 ID */
    private Long userId;

    private ReceiptType type;

    /** true-持有中（returned_at 为空）false-已归还 null-全部 */
    private Boolean active;
}
