package com.sk.asset.service.receipt;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.receipt.AllocationQuery;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.receipt.AssetAllocation;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.receipt.AssetAllocationMapper;
import com.sk.asset.service.asset.AssetService;
import com.sk.asset.service.receipt.impl.AllocationServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllocationServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaUpdateWrapper.set 需要 TableInfo 缓存，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Asset.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AssetAllocation.class);
    }

    @Mock
    private AssetAllocationMapper allocationMapper;

    @Mock
    private AssetMapper assetMapper;

    @Mock
    private AssetService assetService;

    @InjectMocks
    private AllocationServiceImpl allocationService;

    private AssetAllocation activeAllocation() {
        AssetAllocation allocation = new AssetAllocation();
        allocation.setId(12L);
        allocation.setAssetId(1L);
        allocation.setUserId(100L);
        allocation.setUserName("张三");
        allocation.setType("RECEIVE");
        allocation.setDepartment("PMC部");
        allocation.setAllocatedAt(LocalDateTime.now().minusDays(3));
        return allocation;
    }

    // ---- return ----

    @Test
    void return_shouldCloseAllocationAndChangeStatus() {
        AssetAllocation allocation = activeAllocation();
        when(allocationMapper.selectById(12L)).thenReturn(allocation);
        when(allocationMapper.update(any(), any())).thenReturn(1);

        allocationService.returnAllocation(12L, "退库", 200L);

        verify(allocationMapper).update(any(), any());
        verify(assetService).changeStatus(eq(1L), eq(AssetStatus.IDLE), eq(200L),
                eq("归还"), contains("张三"));
        // 资产持有人清空（仍为本记录持有人时）
        verify(assetMapper).update(any(), any());
    }

    @Test
    void return_shouldRejectWhenAlreadyReturned() {
        AssetAllocation allocation = activeAllocation();
        allocation.setReturnedAt(LocalDateTime.now().minusDays(1));
        when(allocationMapper.selectById(12L)).thenReturn(allocation);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> allocationService.returnAllocation(12L, null, 200L));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("已归还"));
        verify(assetService, never()).changeStatus(anyLong(), any(), anyLong(), any(), any());
    }

    @Test
    void return_shouldRejectWhenMissing() {
        when(allocationMapper.selectById(99L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> allocationService.returnAllocation(99L, null, 200L));

        assertEquals(404, exception.getCode());
    }

    // ---- list ----

    @Test
    void list_shouldFillAssetNames() {
        AssetAllocation allocation = activeAllocation();
        when(allocationMapper.selectList(any())).thenReturn(List.of(allocation));
        Asset asset = new Asset();
        asset.setId(1L);
        asset.setBarcode("SKSCDM-0001");
        asset.setName("测试资产1");
        asset.setSn("SN-001");
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(asset));

        List<AssetAllocation> result = allocationService.list(new AllocationQuery());

        assertEquals(1, result.size());
        assertEquals("SKSCDM-0001", result.get(0).getAssetBarcode());
        assertEquals("测试资产1", result.get(0).getAssetName());
        assertEquals("SN-001", result.get(0).getAssetSn());
    }
}
