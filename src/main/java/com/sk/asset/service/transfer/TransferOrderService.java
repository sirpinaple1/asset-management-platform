package com.sk.asset.service.transfer;

import com.sk.asset.dto.transfer.TransferApplyReq;
import com.sk.asset.dto.transfer.TransferQuery;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.enums.transfer.TransferSource;

import java.util.List;

/**
 * 调拨单服务（M05，ATR 单）。
 * 状态机：PENDING → COMPLETED（调入方确认，更新资产归属 + 持有关系转移 + 状态联动 + 写日志）
 *                    / CANCELLED（发起方撤销，资产不变）
 *                    / REJECTED（调入方拒绝，资产不变）。
 * 确认终态一致性（避免"在用却无人持有"矛盾）：
 * 填使用人 → 人持有（TRANSFER）→ IN_USE；只填部门 → 部门持有（user_id=null）→ IN_USE；
 * 只填区域 → 调拨回库（闭环旧持有 + 持有人归零）→ IDLE。
 */
public interface TransferOrderService {

    /**
     * 发起调拨（状态 PENDING，source=MANUAL）。
     * 校验：资产存在且非报废、无待确认调拨单占用、无待审批领用/借用单占用、
     * 调入位置存在；调入区域与调入部门至少一项。不锁定资产状态。
     */
    TransferOrder create(TransferApplyReq req, Long applicantUserId, String applicantName);

    /**
     * 发起调拨（显式来源，M07 盘点差异触发时传 INVENTORY_TRIGGERED + 任务 ID）。
     * 其余校验与行为同 {@link #create(TransferApplyReq, Long, String)}；
     * stocktakeId 写入 transfer_order 供反查来源与防重复生成。
     */
    TransferOrder create(TransferApplyReq req, Long applicantUserId, String applicantName,
                         TransferSource source, Long stocktakeId);

    /**
     * 入口 B（钉钉原生表单发起）建单：校验主体与 create 一致（资产/占用/位置），
     * 但不发布 OA 同步事件（单据源自钉钉，避免回推循环）。
     * 调拨在入口 A 契约中钉钉审批人 = 调入方（toUserId），故调用方（import 服务）
     * 应以钉钉实例 tasks 解析的审批人作为 toUserId 传入 req——以钉钉传来的为准。
     */
    TransferOrder createFromDingtalk(TransferApplyReq req, Long applicantUserId, String applicantName);

    /** 列表（status/source/dept/userId/date 筛选，含明细行与位置名称回填） */
    List<TransferOrder> list(TransferQuery query);

    /** 详情（含明细行与位置名称） */
    TransferOrder getById(Long id);

    /**
     * 调入方确认收到：更新资产 location_id/user_id/user_department、
     * 闭环旧持有记录并新建调入方持有记录（asset_allocation.type=TRANSFER，
     * 只填部门时为 user_id=null 的部门持有）、资产状态联动
     * （有新持有 → IN_USE；只填区域回库 → IDLE）、写 asset_log（operation_type=调拨），
     * 状态 → COMPLETED。确认人不能是发起人。
     */
    TransferOrder confirm(Long id, Long confirmerUserId, String confirmerName);

    /** 调入方拒绝接收（资产不变，记录拒绝原因），状态 → REJECTED。拒绝人不能是发起人。 */
    TransferOrder reject(Long id, String reason, Long confirmerUserId, String confirmerName);

    /** 发起方撤销（仅 PENDING 状态可撤，资产不变），状态 → CANCELLED。仅发起人可撤销。 */
    TransferOrder cancel(Long id, Long operatorUserId);
}
