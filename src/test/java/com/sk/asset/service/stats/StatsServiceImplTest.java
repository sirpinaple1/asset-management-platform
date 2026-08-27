package com.sk.asset.service.stats;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sk.asset.dto.stats.StatsOverviewResp;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.stocktake.Stocktake;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.change.ChangeOrderMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptMapper;
import com.sk.asset.mapper.stocktake.StocktakeMapper;
import com.sk.asset.mapper.transfer.TransferOrderMapper;
import com.sk.asset.service.stats.impl.StatsServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaWrapper 列名解析需要 TableInfo 缓存，手动初始化
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Asset.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ReceiveReceipt.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, TransferOrder.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ChangeOrder.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Stocktake.class);
    }

    @Mock
    private AssetMapper assetMapper;
    @Mock
    private ReceiveReceiptMapper receiptMapper;
    @Mock
    private TransferOrderMapper transferMapper;
    @Mock
    private ChangeOrderMapper changeMapper;
    @Mock
    private StocktakeMapper stocktakeMapper;

    @InjectMocks
    private StatsServiceImpl statsService;

    @Test
    void overview_shouldAggregateAllMetrics() {
        when(assetMapper.selectMaps(any(QueryWrapper.class)))
                .thenReturn(List.of(Map.of("status", "IDLE", "cnt", 5L), Map.of("status", "IN_USE", "cnt", 3L)));
        when(receiptMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(transferMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(changeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(4L);
        when(assetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(stocktakeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        StatsOverviewResp resp = statsService.overview(100L);

        // 状态分布：库里有 IDLE/IN_USE，无数据的 PENDING_CONFIRM/DISCARD 补 0
        assertEquals(5L, resp.getAssetStatusCounts().get("IDLE"));
        assertEquals(3L, resp.getAssetStatusCounts().get("IN_USE"));
        assertEquals(0L, resp.getAssetStatusCounts().get("PENDING_CONFIRM"));
        assertEquals(0L, resp.getAssetStatusCounts().get("DISCARD"));
        assertEquals(4, resp.getAssetStatusCounts().size());
        // 三单待办合计 + 持有 + 进行中盘点
        assertEquals(7L, resp.getMyTodoCount());
        assertEquals(3L, resp.getMyHoldingCount());
        assertEquals(1L, resp.getInProgressStocktakeCount());
    }

    @Test
    void overview_shouldReturnZeroesWhenNoData() {
        when(assetMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of());
        when(receiptMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(transferMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(changeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(assetMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(stocktakeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        StatsOverviewResp resp = statsService.overview(100L);

        assertEquals(0L, resp.getMyTodoCount());
        assertEquals(0L, resp.getMyHoldingCount());
        assertEquals(0L, resp.getInProgressStocktakeCount());
        // 空库时仍返回枚举全集（全 0），前端免判空
        assertEquals(0L, resp.getAssetStatusCounts().get("IDLE"));
        assertEquals(4, resp.getAssetStatusCounts().size());
    }
}
