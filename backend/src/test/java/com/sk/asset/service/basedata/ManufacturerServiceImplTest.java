package com.sk.asset.service.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.basedata.Manufacturer;
import com.sk.asset.mapper.basedata.ManufacturerMapper;
import com.sk.asset.service.basedata.impl.ManufacturerServiceImpl;
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
class ManufacturerServiceImplTest {

    @Mock
    private ManufacturerMapper manufacturerMapper;

    @InjectMocks
    private ManufacturerServiceImpl manufacturerService;

    @Test
    void listBy_shouldReturnFilteredManufacturers() {
        // Given
        Manufacturer m1 = new Manufacturer();
        m1.setId(1L);
        m1.setName("厂商A");

        when(manufacturerMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(m1));

        // When
        List<Manufacturer> result = manufacturerService.listBy("厂商", 1);

        // Then
        assertEquals(1, result.size());
        verify(manufacturerMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void page_shouldReturnPagedResult() {
        // Given
        Page<Manufacturer> pageResult = new Page<>(1, 20);
        pageResult.setRecords(Arrays.asList());
        pageResult.setTotal(0);

        when(manufacturerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
            .thenReturn(pageResult);

        // When
        IPage<Manufacturer> result = manufacturerService.page(1, 20, null, null);

        // Then
        assertSame(pageResult, result);
        verify(manufacturerMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void save_shouldCallMapperInsert() {
        // Given
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName("新厂商");

        when(manufacturerMapper.insert(manufacturer)).thenReturn(1);

        // When
        manufacturerService.save(manufacturer);

        // Then
        verify(manufacturerMapper, times(1)).insert(manufacturer);
    }

    @Test
    void updateById_shouldCallMapperUpdate() {
        // Given
        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setId(1L);
        manufacturer.setName("更新厂商");

        when(manufacturerMapper.updateById(manufacturer)).thenReturn(1);

        // When
        manufacturerService.updateById(manufacturer);

        // Then
        verify(manufacturerMapper, times(1)).updateById(manufacturer);
    }

    @Test
    void deleteById_shouldDeleteWhenNotReferenced() {
        // Given
        Manufacturer m1 = new Manufacturer();
        m1.setId(1L);
        m1.setName("厂商A");

        when(manufacturerMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(m1));
        when(manufacturerMapper.countModelRefs(1L)).thenReturn(0L);
        when(manufacturerMapper.deleteById(1L)).thenReturn(1);

        // When
        manufacturerService.deleteById(1L);

        // Then
        verify(manufacturerMapper, times(1)).deleteById(1L);
    }

    @Test
    void deleteById_shouldThrow409WhenReferencedByModel() {
        // Given
        Manufacturer m1 = new Manufacturer();
        m1.setId(1L);
        m1.setName("厂商A");

        when(manufacturerMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(m1));
        when(manufacturerMapper.countModelRefs(1L)).thenReturn(2L);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class,
            () -> manufacturerService.deleteById(1L));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("厂商A"));
        verify(manufacturerMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteByIds_shouldDeleteAllWhenNoneReferenced() {
        // Given
        Manufacturer m1 = new Manufacturer();
        m1.setId(1L);
        m1.setName("厂商A");
        Manufacturer m2 = new Manufacturer();
        m2.setId(2L);
        m2.setName("厂商B");

        when(manufacturerMapper.selectBatchIds(List.of(1L, 2L))).thenReturn(Arrays.asList(m1, m2));
        when(manufacturerMapper.countModelRefs(1L)).thenReturn(0L);
        when(manufacturerMapper.countModelRefs(2L)).thenReturn(0L);

        // When
        manufacturerService.deleteByIds(Arrays.asList(1L, 2L));

        // Then
        verify(manufacturerMapper, times(1)).deleteById(1L);
        verify(manufacturerMapper, times(1)).deleteById(2L);
    }

    @Test
    void deleteByIds_shouldRejectAllWhenAnyReferenced() {
        // Given：id=2 被引用 → 整体拒绝，id=1 也不得删除
        Manufacturer m1 = new Manufacturer();
        m1.setId(1L);
        m1.setName("厂商A");
        Manufacturer m2 = new Manufacturer();
        m2.setId(2L);
        m2.setName("厂商B");

        when(manufacturerMapper.selectBatchIds(List.of(1L, 2L))).thenReturn(Arrays.asList(m1, m2));
        when(manufacturerMapper.countModelRefs(1L)).thenReturn(0L);
        when(manufacturerMapper.countModelRefs(2L)).thenReturn(3L);

        // When & Then
        BusinessException ex = assertThrows(BusinessException.class,
            () -> manufacturerService.deleteByIds(Arrays.asList(1L, 2L)));
        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("厂商B"));
        verify(manufacturerMapper, never()).deleteById(anyLong());
    }

    @Test
    void restoreById_shouldReturnMapperResult() {
        // Given
        when(manufacturerMapper.restoreById(1L)).thenReturn(1);

        // When
        int rows = manufacturerService.restoreById(1L);

        // Then
        assertEquals(1, rows);
        verify(manufacturerMapper, times(1)).restoreById(1L);
    }
}
