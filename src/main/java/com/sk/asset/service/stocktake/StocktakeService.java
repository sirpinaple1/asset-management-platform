package com.sk.asset.service.stocktake;

import com.sk.asset.dto.stocktake.StocktakeBarcodeScanReq;
import com.sk.asset.dto.stocktake.StocktakeCreateReq;
import com.sk.asset.dto.stocktake.StocktakeQuery;
import com.sk.asset.dto.stocktake.StocktakeReportResp;
import com.sk.asset.dto.stocktake.StocktakeScanReq;
import com.sk.asset.entity.stocktake.Stocktake;
import com.sk.asset.entity.stocktake.StocktakeItem;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.enums.stocktake.StocktakeItemStatus;

import java.util.List;

/**
 * 盘点服务（M07）。状态机：PENDING → IN_PROGRESS → COMPLETED / CANCELLED。
 * 创建时按范围（位置含子树/分类）快照非报废资产生成明细（expected = 账面位置）；
 * 扫码确认判定 相符/位置不符/盘亏，范围外已登记资产记盘盈（EXTRA）；
 * 完成时剩余待盘明细记为盘亏并写资产日志；位置不符明细可批量触发
 * source=INVENTORY_TRIGGERED 调拨单归位（transfer_order.stocktake_id 关联防重）。
 */
public interface StocktakeService {

    /**
     * 创建盘点任务（状态 PENDING）：按范围圈资产（报废排除）快照生成明细。
     * 校验：范围位置/分类存在、范围内有待盘资产。
     */
    Stocktake create(StocktakeCreateReq req, Long creatorUserId, String creatorName);

    /** 列表（status/userId/date 筛选，含范围名称与统计计数回填） */
    List<Stocktake> list(StocktakeQuery query);

    /** 详情（含明细行、范围名称与统计计数回填） */
    Stocktake getById(Long id);

    /** 明细列表（status 可选筛选，含资产与位置名称回填） */
    List<StocktakeItem> listItems(Long stocktakeId, StocktakeItemStatus status);

    /** 开始盘点（PENDING → IN_PROGRESS，记录开始时间） */
    Stocktake start(Long stocktakeId, Long userId);

    /**
     * 明细确认（扫码或人工）：提交实际位置判定 相符/位置不符，或标记盘亏。
     * 任务须 IN_PROGRESS，明细须 PENDING（已盘不可重盘）。
     */
    StocktakeItem scanItem(Long stocktakeId, Long itemId, StocktakeScanReq req, Long userId);

    /**
     * 按条码扫码（PDA 入口）：条码在任务明细中 → 更新该明细；
     * 不在明细中但资产已登记 → 新建盘盈（EXTRA）明细；资产未登记 → 404。
     */
    StocktakeItem scanByBarcode(Long stocktakeId, StocktakeBarcodeScanReq req, Long userId);

    /**
     * 完成盘点：剩余待盘明细记为盘亏（NOT_FOUND）并逐台写资产日志（盘点处理），
     * 状态 → COMPLETED。任务须 IN_PROGRESS。
     */
    Stocktake complete(Long stocktakeId, Long userId);

    /** 盘点报告：各状态汇总计数 + 差异明细（进行中也可查看实时统计） */
    StocktakeReportResp report(Long stocktakeId);

    /**
     * 对位置不符明细批量创建调拨单（source=INVENTORY_TRIGGERED，按实际位置分组，
     * transfer_order.stocktake_id 关联）。任务须 COMPLETED 且未生成过调拨单；
     * 任一资产被待审批单据占用时整体回滚。
     */
    List<TransferOrder> createTransfers(Long stocktakeId, Long operatorUserId, String operatorName);

    /** 取消盘点（仅 PENDING/IN_PROGRESS 且创建人可操作，明细与资产不变） */
    Stocktake cancel(Long stocktakeId, Long userId);
}
