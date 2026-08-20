package com.sk.asset.service.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.service.basedata.impl.LocationServiceImpl;
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
class LocationServiceImplTest {

    @Mock
    private LocationMapper locationMapper;

    @InjectMocks
    private LocationServiceImpl locationService;

    @Test
    void list_shouldReturnAllLocationsOrderedByPath() {
        // Given
        Location l1 = new Location();
        l1.setId(1L);
        l1.setName("森科");
        l1.setPath("/1/");

        Location l2 = new Location();
        l2.setId(2L);
        l2.setName("物料仓");
        l2.setParentId(1L);
        l2.setPath("/1/2/");

        when(locationMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(l1, l2));

        // When
        List<Location> result = locationService.list();

        // Then
        assertEquals(2, result.size());
        verify(locationMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void listChildren_shouldReturnChildrenWhenParentExists() {
        // Given
        Location parent = new Location();
        parent.setId(1L);
        parent.setPath("/1/");

        Location child = new Location();
        child.setId(2L);
        child.setParentId(1L);
        child.setPath("/1/2/");

        when(locationMapper.selectById(1L)).thenReturn(parent);
        when(locationMapper.selectByParentPath("/1/")).thenReturn(Arrays.asList(parent, child));

        // When
        List<Location> result = locationService.listChildren(1L);

        // Then
        assertEquals(2, result.size());
        verify(locationMapper, times(1)).selectByParentPath("/1/");
    }

    @Test
    void listChildren_shouldReturnEmptyWhenParentNotExists() {
        // Given
        when(locationMapper.selectById(999L)).thenReturn(null);

        // When
        List<Location> result = locationService.listChildren(999L);

        // Then
        assertTrue(result.isEmpty());
        verify(locationMapper, never()).selectByParentPath(any());
    }

    @Test
    void save_shouldCallMapperInsert() {
        // Given
        Location location = new Location();
        location.setName("新位置");

        when(locationMapper.insert(location)).thenReturn(1);

        // When
        locationService.save(location);

        // Then
        verify(locationMapper, times(1)).insert(location);
    }
}
