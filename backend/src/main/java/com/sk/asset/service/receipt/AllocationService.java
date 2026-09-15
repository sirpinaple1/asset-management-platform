package com.sk.asset.service.receipt;

import com.sk.asset.dto.receipt.AllocationQuery;
import com.sk.asset.entity.receipt.AssetAllocation;

import java.util.List;

/**
 * 资产持有关系服务（M04）：审批通过发放时写入，归还时闭环。
 */
public interface AllocationService {

    /** 持有关系列表（含资产编码/名称/序列号回填），支持 assetId/userId/type/active 筛选 */
    List<AssetAllocation> list(AllocationQuery query);

    /**
     * 归还：returned_at 置当前时间 → 资产 IN_USE → IDLE（写日志）→
     * 资产持有人仍为本记录持有人时清空。
     *
     * @param id            asset_allocation 主键
     * @param note          归还备注（可空）
     * @param operatorUserId 操作人（UserContext）
     */
    void returnAllocation(Long id, String note, Long operatorUserId);
}
