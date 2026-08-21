package com.sk.asset.service.asset;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sk.asset.dto.asset.AssetQuery;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.asset.AssetLog;
import com.sk.asset.enums.asset.AssetStatus;

import java.util.List;

/**
 * 资产服务。状态变更统一走 changeStatus（写 asset_log），
 * M04/M05 的领用/归还/调拨/盘点处理均通过该方法落地。
 */
public interface AssetService {

    /** 分页查询（含关联名称回填），支持多条件筛选 */
    IPage<Asset> page(long current, long size, AssetQuery query);

    /** 不分页查询（导出场景），含关联名称回填 */
    List<Asset> listBy(AssetQuery query);

    /** 详情（JOIN 一次取齐关联名称） */
    Asset getById(Long id);

    /** 新增（barcode 唯一 + 外键校验，状态固定 IDLE，写"新增"日志） */
    void save(Asset asset, Long operatorUserId);

    /** 编辑基础信息（不含状态变更，barcode 唯一 + 外键校验） */
    void updateById(Asset asset);

    /** 报废（IDLE/IN_USE → DISCARD，写"报废"日志） */
    void discard(Long id, String reason, Long operatorUserId);

    /** 该资产的操作日志（时间倒序） */
    List<AssetLog> listLogs(Long assetId);

    /**
     * 状态机流转（M04/M05 调用入口）：校验合法流转 → 更新状态 → 写 asset_log。
     * 操作类型按目标状态推导（IN_USE/PENDING_CONFIRM→领用，IDLE→归还，DISCARD→报废）。
     *
     * @param assetId     资产 ID
     * @param newStatus   目标状态
     * @param operatorUserId 操作人（comm_public_basic 用户 ID）
     * @param note        备注（拼入日志内容）
     */
    void changeStatus(Long assetId, AssetStatus newStatus, Long operatorUserId, String note);

    /**
     * 状态机流转（显式操作类型）：M04 单据流需区分「领用/借用」语义时使用，
     * 其余与 {@link #changeStatus(Long, AssetStatus, Long, String)} 一致。
     *
     * @param operationType 日志操作类型（如 领用/借用/归还，asset_log.operation_type）
     */
    void changeStatus(Long assetId, AssetStatus newStatus, Long operatorUserId,
                      String operationType, String note);
}
