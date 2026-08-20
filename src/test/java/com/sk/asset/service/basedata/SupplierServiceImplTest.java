package com.sk.asset.service.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    void list_shouldReturnAllSuppliers() {
        // Given
        Supplier s1 = new Supplier();
        s1.setId(1L);
        s1.setName("供应商A");

        when(supplierMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(s1));

        // When
        List<Supplier> result = supplierService.list();

        // Then
        assertEquals(1, result.size());
        verify(supplierMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
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
    void deleteById_shouldCallMapperDelete() {
        // Given
        Long id = 1L;
        when(supplierMapper.deleteById(id)).thenReturn(1);

        // When
        supplierService.deleteById(id);

        // Then
        verify(supplierMapper, times(1)).deleteById(id);
    }
}
