package com.sk.asset.service.basedata;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.service.basedata.impl.CategoryServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private AssetMapper assetMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @BeforeAll
    static void initTableInfo() {
        // LambdaUpdateWrapper 的 set() 依赖 MP TableInfo 缓存（纯 Mockito 单测无 Spring 容器，需手动注册）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Category.class);
    }

    // -------------------- list --------------------

    @Test
    void list_shouldReturnAllCategoriesOrderedBySortOrder() {
        Category c1 = new Category();
        c1.setId(1L);
        c1.setName("镀膜设备");
        c1.setSortOrder(41);

        Category c2 = new Category();
        c2.setId(2L);
        c2.setName("辅助设备");
        c2.setParentId(13L);
        c2.setSortOrder(46);

        when(categoryMapper.selectList(any())).thenReturn(Arrays.asList(c1, c2));

        List<Category> result = categoryService.list();

        assertEquals(2, result.size());
        verify(categoryMapper, times(1)).selectList(any());
    }

    // -------------------- save --------------------

    @Test
    void save_shouldInsertWhenValid() {
        Category category = buildCategory("清洗设备", "CLEANING", "SKSCQX", 13L);

        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.selectById(13L)).thenReturn(buildTopCategory(13L, "生产设备"));
        when(categoryMapper.insert(category)).thenReturn(1);

        categoryService.save(category);

        verify(categoryMapper, times(1)).insert(category);
    }

    @Test
    void save_shouldRejectBlankName() {
        Category category = buildCategory(" ", null, null, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.save(category));
        assertEquals(400, ex.getCode());
        verify(categoryMapper, never()).insert(any(Category.class));
    }

    @Test
    void save_shouldRejectDuplicateNameInSameLevel() {
        Category category = buildCategory("清洗设备", null, null, 13L);

        when(categoryMapper.selectById(13L)).thenReturn(buildTopCategory(13L, "生产设备"));
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.save(category));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("同级已存在同名分类"));
        verify(categoryMapper, never()).insert(any(Category.class));
    }

    @Test
    void save_shouldAllowSameNameAcrossDifferentLevels() {
        Category category = buildCategory("检测设备", null, null, 13L);

        when(categoryMapper.selectById(13L)).thenReturn(buildTopCategory(13L, "生产设备"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.insert(category)).thenReturn(1);

        assertDoesNotThrow(() -> categoryService.save(category));
    }

    @Test
    void save_shouldRejectNonexistentParent() {
        Category category = buildCategory("清洗设备", null, null, 99L);

        when(categoryMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.save(category));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("父分类不存在"));
    }

    @Test
    void save_shouldRejectSecondLevelParent() {
        Category parent = buildCategory("镀膜设备", "COATING", "SKSCDM", 13L);
        Category category = buildCategory("清洗设备", null, null, 1L);

        when(categoryMapper.selectById(1L)).thenReturn(parent);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.save(category));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("最多两级"));
    }

    @Test
    void save_shouldRejectDuplicateCode() {
        Category category = buildCategory("清洗设备", "CLEANING", "SKSCQX", null);

        // 第一次 selectCount：同级重名校验（0 条）；第二次：code 冲突校验（1 条）
        when(categoryMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.save(category));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("分类编码「CLEANING」已存在"));
        verify(categoryMapper, never()).insert(any(Category.class));
    }

    @Test
    void save_shouldGenerateCodeFromIdWhenNotSpecified() {
        Category category = buildCategory("清洗设备", null, "SKSCQX", 13L);

        when(categoryMapper.selectById(13L)).thenReturn(buildTopCategory(13L, "生产设备"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        // insert 回填主键（模拟 MyBatis-Plus IdType.AUTO 行为）
        when(categoryMapper.insert(category)).thenAnswer(inv -> {
            category.setId(16L);
            return 1;
        });

        categoryService.save(category);

        // code 由后端生成（前端契约不提交）
        assertEquals("CAT16", category.getCode());
        verify(categoryMapper, times(1)).updateById(category);
    }

    // -------------------- update --------------------

    @Test
    void updateById_shouldUpdateWhenValid() {
        Category existing = buildCategory("清洗设备", null, "SKSCQX", 13L);
        existing.setId(15L);
        Category category = buildCategory("清洗设备", "CLEANING", "SKSCQX", 13L);
        category.setId(15L);

        when(categoryMapper.selectById(15L)).thenReturn(existing);
        when(categoryMapper.selectById(13L)).thenReturn(buildTopCategory(13L, "生产设备"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.update(isNull(), any())).thenReturn(1);

        categoryService.updateById(category);

        verify(categoryMapper, times(1)).update(isNull(), any(Wrapper.class));
    }

    @Test
    void updateById_shouldRejectNonexistent() {
        Category category = buildCategory("清洗设备", null, null, null);
        category.setId(99L);

        when(categoryMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.updateById(category));
        assertEquals(404, ex.getCode());
    }

    @Test
    void updateById_shouldKeepExistingCodeWhenRequestCodeNull() {
        // 前端契约不提交 code：请求 code 为空时保持库中原编码（如 V20260832 存量的 COATING）
        Category existing = buildCategory("镀膜设备", "COATING", "SKSCDM", 13L);
        existing.setId(5L);
        Category category = buildCategory("镀膜设备", null, "SKSCDM", 13L);
        category.setId(5L);

        when(categoryMapper.selectById(5L)).thenReturn(existing);
        when(categoryMapper.selectById(13L)).thenReturn(buildTopCategory(13L, "生产设备"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.update(isNull(), any())).thenReturn(1);

        categoryService.updateById(category);

        assertEquals("COATING", category.getCode());
        verify(categoryMapper, times(1)).update(isNull(), any(Wrapper.class));
    }

    @Test
    void updateById_shouldRejectSelfParent() {
        Category existing = buildTopCategory(13L, "生产设备");
        Category category = buildCategory("生产设备", null, "SKSC", 13L);
        category.setId(13L);

        when(categoryMapper.selectById(13L)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.updateById(category));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("父分类不能是分类自身"));
    }

    @Test
    void updateById_shouldRejectParentWithChildrenWhenMovingUnderOther() {
        // 编辑「生产设备」想挂到「办公设施」下，但生产设备下已有子分类
        Category existing = buildTopCategory(13L, "生产设备");
        Category category = buildCategory("生产设备", null, "SKSC", 11L);
        category.setId(13L);

        when(categoryMapper.selectById(13L)).thenReturn(existing);
        when(categoryMapper.selectById(11L)).thenReturn(buildTopCategory(11L, "办公设施"));
        // 第一次 selectCount：hasChildren（1 条，存在子分类）
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.updateById(category));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("不能再挂到其他分类下"));
    }

    @Test
    void updateById_shouldRejectDuplicateNameInSameLevel() {
        Category existing = buildCategory("清洗设备", null, null, 13L);
        existing.setId(15L);
        Category category = buildCategory("移印设备", null, null, 13L);
        category.setId(15L);

        when(categoryMapper.selectById(15L)).thenReturn(existing);
        when(categoryMapper.selectById(13L)).thenReturn(buildTopCategory(13L, "生产设备"));
        // selectCount 调用顺序：①hasChildren（0，无子分类）②同级重名（1，排除自己后仍有同名）
        when(categoryMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.updateById(category));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("同级已存在同名分类"));
    }

    // -------------------- delete --------------------

    @Test
    void deleteById_shouldLogicalDeleteAndReleaseCodeWhenUnreferenced() {
        Category existing = buildCategory("移印设备", "PAD_PRINTING", "SKSCYX", 13L);
        existing.setId(16L);

        when(categoryMapper.selectById(16L)).thenReturn(existing);
        // hasChildren → 0
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(assetMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.update(isNull(), any())).thenReturn(1);

        categoryService.deleteById(16L);

        verify(categoryMapper, times(1)).update(isNull(), any(Wrapper.class));
        verify(categoryMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void deleteById_shouldRejectWhenHasChildren() {
        Category existing = buildCategory("移印设备", null, null, 13L);
        existing.setId(16L);

        when(categoryMapper.selectById(16L)).thenReturn(existing);
        when(categoryMapper.selectCount(any())).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.deleteById(16L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("存在子分类"));
    }

    @Test
    void deleteById_shouldRejectWhenReferencedByAsset() {
        Category existing = buildCategory("移印设备", null, null, 13L);
        existing.setId(16L);

        when(categoryMapper.selectById(16L)).thenReturn(existing);
        // hasChildren → 0
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(assetMapper.selectCount(any(Wrapper.class))).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.deleteById(16L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("已被 3 条资产引用"));
        verify(categoryMapper, never()).update(any(), any());
    }

    @Test
    void deleteById_shouldRejectNonexistent() {
        when(categoryMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.deleteById(99L));
        assertEquals(404, ex.getCode());
    }

    // -------------------- helpers --------------------

    private Category buildCategory(String name, String code, String barcodePrefix, Long parentId) {
        Category category = new Category();
        category.setName(name);
        category.setCode(code);
        category.setBarcodePrefix(barcodePrefix);
        category.setParentId(parentId);
        category.setSortOrder(10);
        return category;
    }

    private Category buildTopCategory(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setParentId(null);
        return category;
    }
}
