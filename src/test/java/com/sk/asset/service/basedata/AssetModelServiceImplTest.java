package com.sk.asset.service.basedata;

import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.basedata.AssetModel;
import com.sk.asset.mapper.basedata.AssetModelMapper;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.mapper.basedata.CompanyMapper;
import com.sk.asset.mapper.basedata.DepreciationRuleMapper;
import com.sk.asset.mapper.basedata.ManufacturerMapper;
import com.sk.asset.service.basedata.impl.AssetModelServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetModelServiceImplTest {

    @Mock
    private AssetModelMapper assetModelMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ManufacturerMapper manufacturerMapper;

    @Mock
    private DepreciationRuleMapper depreciationRuleMapper;

    @Mock
    private CompanyMapper companyMapper;

    @InjectMocks
    private AssetModelServiceImpl assetModelService;

    @Test
    void list_shouldCallSelectAllWithRelations() {
        AssetModel m = new AssetModel();
        m.setId(1L);
        m.setName("MacBook Pro");
        m.setCategoryName("IT设备");

        when(assetModelMapper.selectAllWithRelations()).thenReturn(Arrays.asList(m));

        List<AssetModel> result = assetModelService.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("MacBook Pro");
        assertThat(result.get(0).getCategoryName()).isEqualTo("IT设备");
        verify(assetModelMapper, times(1)).selectAllWithRelations();
    }

    @Test
    void listByCategoryId_shouldCallSelectByCategoryIdWithRelations() {
        AssetModel m = new AssetModel();
        m.setId(1L);
        m.setName("MacBook Pro");
        m.setCategoryId(4L);

        when(assetModelMapper.selectByCategoryIdWithRelations(4L)).thenReturn(Arrays.asList(m));

        List<AssetModel> result = assetModelService.listByCategoryId(4L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategoryId()).isEqualTo(4L);
        verify(assetModelMapper, times(1)).selectByCategoryIdWithRelations(4L);
    }

    @Test
    void getById_shouldCallSelectByIdWithRelations() {
        AssetModel m = new AssetModel();
        m.setId(1L);
        m.setName("MacBook Pro");
        m.setCategoryName("IT设备");

        when(assetModelMapper.selectByIdWithRelations(1L)).thenReturn(m);

        AssetModel result = assetModelService.getById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCategoryName()).isEqualTo("IT设备");
        verify(assetModelMapper, times(1)).selectByIdWithRelations(1L);
    }

    @Test
    void save_shouldCallMapperInsert() {
        AssetModel m = new AssetModel();
        m.setName("新型号");

        assetModelService.save(m);

        verify(assetModelMapper, times(1)).insert(m);
    }

    @Test
    void save_shouldRejectWhenCategoryNotExists() {
        AssetModel m = new AssetModel();
        m.setName("新型号");
        m.setCategoryId(99L);

        when(categoryMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> assetModelService.save(m))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("资产分类不存在");
        verify(assetModelMapper, never()).insert(any(AssetModel.class));
    }

    @Test
    void save_shouldRejectWhenManufacturerNotExists() {
        AssetModel m = new AssetModel();
        m.setName("新型号");
        m.setManufacturerId(99L);

        when(manufacturerMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> assetModelService.save(m))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("厂商不存在");
        verify(assetModelMapper, never()).insert(any(AssetModel.class));
    }

    @Test
    void updateById_shouldCallMapperUpdateById() {
        AssetModel m = new AssetModel();
        m.setId(1L);
        m.setName("更新型号");

        assetModelService.updateById(m);

        verify(assetModelMapper, times(1)).updateById(m);
    }

    @Test
    void deleteById_shouldCallMapperDeleteById() {
        AssetModel m = new AssetModel();
        m.setId(1L);
        m.setName("MacBook Pro");

        when(assetModelMapper.selectById(1L)).thenReturn(m);
        when(assetModelMapper.countAssetRefs(1L)).thenReturn(0L);

        assetModelService.deleteById(1L);

        verify(assetModelMapper, times(1)).deleteById(1L);
    }

    @Test
    void deleteById_shouldThrow404WhenNotExists() {
        when(assetModelMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> assetModelService.deleteById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("型号不存在");
        verify(assetModelMapper, never()).deleteById(anyLong());
    }

    @Test
    void deleteById_shouldThrow409WhenReferencedByAsset() {
        AssetModel m = new AssetModel();
        m.setId(1L);
        m.setName("MacBook Pro");

        when(assetModelMapper.selectById(1L)).thenReturn(m);
        when(assetModelMapper.countAssetRefs(1L)).thenReturn(2L);

        assertThatThrownBy(() -> assetModelService.deleteById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被资产引用");
        verify(assetModelMapper, never()).deleteById(anyLong());
    }
}
