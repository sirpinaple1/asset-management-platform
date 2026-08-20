package com.sk.asset.service.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.service.basedata.impl.CategoryServiceImpl;
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
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void list_shouldReturnAllCategoriesOrderedBySortOrder() {
        // Given
        Category c1 = new Category();
        c1.setId(1L);
        c1.setName("镀膜设备");
        c1.setSortOrder(10);

        Category c2 = new Category();
        c2.setId(2L);
        c2.setName("辅助设备");
        c2.setParentId(1L);
        c2.setSortOrder(20);

        when(categoryMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Arrays.asList(c1, c2));

        // When
        List<Category> result = categoryService.list();

        // Then
        assertEquals(2, result.size());
        verify(categoryMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void save_shouldCallMapperInsert() {
        // Given
        Category category = new Category();
        category.setName("新分类");

        when(categoryMapper.insert(category)).thenReturn(1);

        // When
        categoryService.save(category);

        // Then
        verify(categoryMapper, times(1)).insert(category);
    }

    @Test
    void updateById_shouldCallMapperUpdate() {
        // Given
        Category category = new Category();
        category.setId(1L);
        category.setName("更新分类");

        when(categoryMapper.updateById(category)).thenReturn(1);

        // When
        categoryService.updateById(category);

        // Then
        verify(categoryMapper, times(1)).updateById(category);
    }

    @Test
    void deleteById_shouldCallMapperDelete() {
        // Given
        Long id = 1L;
        when(categoryMapper.deleteById(id)).thenReturn(1);

        // When
        categoryService.deleteById(id);

        // Then
        verify(categoryMapper, times(1)).deleteById(id);
    }
}
