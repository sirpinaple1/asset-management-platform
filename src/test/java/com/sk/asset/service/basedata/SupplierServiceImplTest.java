package com.sk.asset.service.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.basedata.Supplier;
import com.sk.asset.mapper.basedata.SupplierMapper;
import com.sk.asset.service.basedata.impl.SupplierServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    @Mock
    private SupplierMapper supplierMapper;

    @InjectMocks
    private SupplierServiceImpl supplierService;

    @Test
    void listBy_shouldReturnFilteredSuppliers() {
        // Given
        Supplier s1 = new Supplier();
        s1.setId(1L);
        s1.setName("供应商A");

        when(supplierMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(s1));

        // When
        List<Supplier> result = supplierService.listBy("供应商", 1);

        // Then
        assertEquals(1, result.size());
        verify(supplierMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void page_shouldReturnPagedResult() {
        // Given
        Page<Supplier> pageResult = new Page<>(1, 20);
        pageResult.setRecords(Arrays.asList());
        pageResult.setTotal(0);

        when(supplierMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
            .thenReturn(pageResult);

        // When
        IPage<Supplier> result = supplierService.page(1, 20, null, null);

        // Then
        assertSame(pageResult, result);
        verify(supplierMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void save_shouldCallMapperInsert() {
        // Given
        Supplier supplier = new Supplier();
        supplier.setName("新供应商");

        when(supplierMapper.insert(supplier)).thenReturn(1);

        // When
        supplierService.save(supplier);

        // Then
        verify(supplierMapper, times(1)).insert(supplier);
    }

    @Test
    void updateById_shouldCallMapperUpdate() {
        // Given
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("更新供应商");

        when(supplierMapper.updateById(supplier)).thenReturn(1);

        // When
        supplierService.updateById(supplier);

        // Then
        verify(supplierMapper, times(1)).updateById(supplier);
    }

    @Test
    void deleteById_shouldDeleteWhenNotReferenced() {
        // Given
        Supplier s1 = new Supplier();
        s1.setId(1L);
        s1.setName("供应商A");

        when(supplierMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(s1));
        when(supplierMapper.countAssetRefs(1L)).thenReturn(0L);
        when(supplierMapper.deleteById(1L)).thenReturn(1);

        // When
        supplierService.deleteById(1L);

        // Then
        verify(supplierMapper, times(1)).deleteById(1L);
    }

    @Test
    void deleteById_shouldThrow409WhenReferencedByAsset() {
        // Given
        Supplier s1 = new Supplier();
        s1.setId(1L);
        s1.setName("供应商A");

        when(supplierMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(s1));
        when(supplierMapper.countAssetRefs(1L)).thenReturn(2L);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class,
            () -> supplierService.deleteById(1L));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("供应商A"));
        verify(supplierMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteByIds_shouldDeleteAllWhenNoneReferenced() {
        // Given
        Supplier s1 = new Supplier();
        s1.setId(1L);
        s1.setName("供应商A");
        Supplier s2 = new Supplier();
        s2.setId(2L);
        s2.setName("供应商B");

        when(supplierMapper.selectBatchIds(List.of(1L, 2L))).thenReturn(Arrays.asList(s1, s2));
        when(supplierMapper.countAssetRefs(1L)).thenReturn(0L);
        when(supplierMapper.countAssetRefs(2L)).thenReturn(0L);

        // When
        supplierService.deleteByIds(Arrays.asList(1L, 2L));

        // Then
        verify(supplierMapper, times(1)).deleteById(1L);
        verify(supplierMapper, times(1)).deleteById(2L);
    }

    @Test
    void deleteByIds_shouldRejectAllWhenAnyReferenced() {
        // Given：id=2 被引用 → 整体拒绝，id=1 也不得删除
        Supplier s1 = new Supplier();
        s1.setId(1L);
        s1.setName("供应商A");
        Supplier s2 = new Supplier();
        s2.setId(2L);
        s2.setName("供应商B");

        when(supplierMapper.selectBatchIds(List.of(1L, 2L))).thenReturn(Arrays.asList(s1, s2));
        when(supplierMapper.countAssetRefs(1L)).thenReturn(0L);
        when(supplierMapper.countAssetRefs(2L)).thenReturn(3L);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class,
            () -> supplierService.deleteByIds(Arrays.asList(1L, 2L)));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("供应商B"));
        verify(supplierMapper, never()).deleteById(anyLong());
    }

    @Test
    void restoreById_shouldReturnMapperResult() {
        // Given
        when(supplierMapper.restoreById(1L)).thenReturn(1);

        // When
        int rows = supplierService.restoreById(1L);

        // Then
        assertEquals(1, rows);
        verify(supplierMapper, times(1)).restoreById(1L);
    }
}
