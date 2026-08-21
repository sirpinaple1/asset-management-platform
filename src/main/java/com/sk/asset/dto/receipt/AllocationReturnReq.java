package com.sk.asset.dto.receipt;

import lombok.Data;

/**
 * 归还请求（POST /api/v1/allocations/{id}/return）。note 可空。
 */
@Data
public class AllocationReturnReq {

    private String note;
}
