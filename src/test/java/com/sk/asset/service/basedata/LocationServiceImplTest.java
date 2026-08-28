package com.sk.asset.service.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.common.BusinessException;
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

    @Test
    void save_shouldRejectDuplicateNameInSameLevel() {
        Location parent = new Location();
        parent.setId(1L);
        parent.setName("森科");
        parent.setPath("/1/");

        Location location = new Location();
        location.setName("森科物料仓");
        location.setParentId(1L);

        // validateParent 先于重名校验执行，需打桩父节点存在
        when(locationMapper.selectById(1L)).thenReturn(parent);
        when(locationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> locationService.save(location));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("同级已存在同名位置"));
        verify(locationMapper, never()).insert(any(Location.class));
    }

    @Test
    void save_shouldGenerateCodeFromIdWhenNotSpecified() {
        Location location = new Location();
        location.setName("新位置");

        // insert 回填主键（模拟 MyBatis-Plus IdType.AUTO 行为）
        when(locationMapper.insert(location)).thenAnswer(inv -> {
            location.setId(7L);
            return 1;
        });

        locationService.save(location);

        assertEquals("LOC7", location.getCode());
        assertEquals("/7/", location.getPath());
        verify(locationMapper, times(1)).updateById(location);
    }

    @Test
    void update_shouldRejectDuplicateNameInSameLevel() {
        Location existing = new Location();
        existing.setId(2L);
        existing.setName("物料仓");
        existing.setParentId(1L);
        existing.setPath("/1/2/");

        Location update = new Location();
        update.setId(2L);
        update.setName("设备仓");
        update.setParentId(1L);

        when(locationMapper.selectById(2L)).thenReturn(existing);
        // selectCount：同级重名校验（排除自己后仍有 1 条同名）
        when(locationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> locationService.updateById(update));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("同级已存在同名位置"));
        verify(locationMapper, never()).updateById(any(Location.class));
    }

    @Test
    void update_shouldKeepExistingCodeWhenRequestCodeNull() {
        Location existing = new Location();
        existing.setId(2L);
        existing.setName("物料仓");
        existing.setParentId(1L);
        existing.setPath("/1/2/");
        existing.setCode("MATERIAL_WH");

        Location update = new Location();
        update.setId(2L);
        update.setName("物料仓B");
        update.setParentId(1L);

        when(locationMapper.selectById(2L)).thenReturn(existing);
        when(locationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        locationService.updateById(update);

        // 前端契约不提交 code：库中原编码保持
        assertEquals("MATERIAL_WH", update.getCode());
        verify(locationMapper, times(1)).updateById(update);
    }

    @Test
    void delete_shouldRejectWhenHasChildren() {
        Location existing = new Location();
        existing.setId(1L);
        existing.setName("森科");

        when(locationMapper.selectById(1L)).thenReturn(existing);
        when(locationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> locationService.deleteById(1L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("存在子位置"));
        verify(locationMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void delete_shouldRejectWhenReferencedByAsset() {
        Location existing = new Location();
        existing.setId(1L);
        existing.setName("森科物料仓");

        when(locationMapper.selectById(1L)).thenReturn(existing);
        when(locationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(locationMapper.countAssetRefs(1L)).thenReturn(5L);

        BusinessException ex = assertThrows(BusinessException.class, () -> locationService.deleteById(1L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("已被资产引用"));
        verify(locationMapper, never()).deleteById(any(Long.class));
    }
}
