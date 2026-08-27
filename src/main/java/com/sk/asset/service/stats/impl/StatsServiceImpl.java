package com.sk.asset.service.stats.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sk.asset.dto.stats.StatsOverviewResp;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.stocktake.Stocktake;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.enums.change.ChangeStatus;
import com.sk.asset.enums.receipt.ReceiptStatus;
import com.sk.asset.enums.stocktake.StocktakeStatus;
import com.sk.asset.enums.transfer.TransferStatus;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.change.ChangeOrderMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptMapper;
import com.sk.asset.mapper.stocktake.StocktakeMapper;
import com.sk.asset.mapper.transfer.TransferOrderMapper;
import com.sk.asset.service.stats.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台统计聚合实现（B3）。四类口径全部走索引列的等值/NULL 组合 count，
 * 无join无子查询；实体 @TableLogic deleted 由 MyBatis-Plus 自动过滤。
 */
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final AssetMapper assetMapper;
    private final ReceiveReceiptMapper receiptMapper;
    private final TransferOrderMapper transferMapper;
    private final ChangeOrderMapper changeMapper;
    private final StocktakeMapper stocktakeMapper;

    @Override
    public StatsOverviewResp overview(Long userId) {
        StatsOverviewResp resp = new StatsOverviewResp();
        resp.setAssetStatusCounts(assetStatusCounts());
        resp.setMyTodoCount(myTodoCount(userId));
        resp.setMyHoldingCount(myHoldingCount(userId));
        resp.setInProgressStocktakeCount(inProgressStocktakeCount(userId));
        return resp;
    }

    /** 资产状态分布：GROUP BY status，枚举全集补 0 */
    private Map<String, Long> assetStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (AssetStatus status : AssetStatus.values()) {
            counts.put(status.name(), 0L);
        }
        List<Map<String, Object>> rows = assetMapper.selectMaps(new QueryWrapper<Asset>()
                .select("status", "COUNT(*) AS cnt")
                .groupBy("status"));
        for (Map<String, Object> row : rows) {
            counts.put(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue());
        }
        return counts;
    }

    /**
     * 待我处理单据数（三单合计，与审批中心"待我处理"口径一致）：
     * 领用/借用/调拨：PENDING 且申请人≠我（requireNotApplicant，操作人不可是发起人）
     *   且（assignee=我 或 assignee IS NULL 共享池）；
     * 变更：PENDING 且（assignee=我 或共享池）——M06 允许发起人自审，不排除我发起的。
     */
    private long myTodoCount(Long userId) {
        long todo = 0;
        todo += receiptMapper.selectCount(new LambdaQueryWrapper<ReceiveReceipt>()
                .eq(ReceiveReceipt::getStatus, ReceiptStatus.PENDING.name())
                .ne(ReceiveReceipt::getApplicantUserId, userId)
                .and(w -> w.eq(ReceiveReceipt::getAssigneeUserId, userId)
                        .or().isNull(ReceiveReceipt::getAssigneeUserId)));
        todo += transferMapper.selectCount(new LambdaQueryWrapper<TransferOrder>()
                .eq(TransferOrder::getStatus, TransferStatus.PENDING.name())
                .ne(TransferOrder::getApplicantUserId, userId)
                .and(w -> w.eq(TransferOrder::getAssigneeUserId, userId)
                        .or().isNull(TransferOrder::getAssigneeUserId)));
        todo += changeMapper.selectCount(new LambdaQueryWrapper<ChangeOrder>()
                .eq(ChangeOrder::getStatus, ChangeStatus.PENDING.name())
                .and(w -> w.eq(ChangeOrder::getAssigneeUserId, userId)
                        .or().isNull(ChangeOrder::getAssigneeUserId)));
        return todo;
    }

    /** 我的持有数：asset.user_id = 我 且 status = IN_USE */
    private long myHoldingCount(Long userId) {
        return assetMapper.selectCount(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getUserId, userId)
                .eq(Asset::getStatus, AssetStatus.IN_USE.name()));
    }

    /** 我创建的进行中盘点数：stocktake.creator_user_id = 我 且 status = IN_PROGRESS */
    private long inProgressStocktakeCount(Long userId) {
        return stocktakeMapper.selectCount(new LambdaQueryWrapper<Stocktake>()
                .eq(Stocktake::getCreatorUserId, userId)
                .eq(Stocktake::getStatus, StocktakeStatus.IN_PROGRESS.name()));
    }
}
