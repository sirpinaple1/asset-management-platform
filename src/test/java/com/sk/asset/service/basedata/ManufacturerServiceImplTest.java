package com.sk.asset.service.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
    void list_shouldReturnAllManufacturers() {
        // Given
        Manufacturer m1 = new Manufacturer();
        m1.setId(1L);
        m1.setName("厂商A");

        when(manufacturerMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(m1));

        // When
        List<Manufacturer> result = manufacturerService.list();

        // Then
        assertEquals(1, result.size());
        verify(manufacturerMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
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
    void deleteById_shouldCallMapperDelete() {
        // Given
        Long id = 1L;
        when(manufacturerMapper.deleteById(id)).thenReturn(1);

        // When
        manufacturerService.deleteById(id);

        // Then
        verify(manufacturerMapper, times(1)).deleteById(id);
    }
}
