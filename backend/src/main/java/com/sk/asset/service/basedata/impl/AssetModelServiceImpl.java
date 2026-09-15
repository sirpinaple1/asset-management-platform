package com.sk.asset.service.basedata.impl;

import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.basedata.AssetModel;
import com.sk.asset.mapper.basedata.AssetModelMapper;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.mapper.basedata.CompanyMapper;
import com.sk.asset.mapper.basedata.DepreciationRuleMapper;
import com.sk.asset.mapper.basedata.ManufacturerMapper;
import com.sk.asset.service.basedata.AssetModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资产型号服务实现。
 * 外键存在性校验（REVIEW-M02 P2④）：categoryId/manufacturerId/depreciationId/companyId
 * 任一指向不存在的记录时拒绝保存；详情查询与列表一致返回关联名称（P2⑤）；
 * 删除前检查是否被资产引用（P2③）。
 */
@Service
@RequiredArgsConstructor
public class AssetModelServiceImpl implements AssetModelService {

    private final AssetModelMapper assetModelMapper;
    private final CategoryMapper categoryMapper;
    private final ManufacturerMapper manufacturerMapper;
    private final DepreciationRuleMapper depreciationRuleMapper;
    private final CompanyMapper companyMapper;

    @Override
    public List<AssetModel> list() {
        return assetModelMapper.selectAllWithRelations();
    }

    @Override
    public List<AssetModel> listByCategoryId(Long categoryId) {
        return assetModelMapper.selectByCategoryIdWithRelations(categoryId);
    }

    @Override
    public AssetModel getById(Long id) {
        return assetModelMapper.selectByIdWithRelations(id);
    }

    @Override
    public void save(AssetModel model) {
        validateRefs(model);
        assetModelMapper.insert(model);
    }

    @Override
    public void updateById(AssetModel model) {
        validateRefs(model);
        assetModelMapper.updateById(model);
    }

    @Override
    public void deleteById(Long id) {
        AssetModel model = assetModelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(404, "型号不存在");
        }
        if (assetModelMapper.countAssetRefs(id) > 0) {
            throw new BusinessException(409, "型号「" + model.getName() + "」已被资产引用，无法删除");
        }
        assetModelMapper.deleteById(id);
    }

    /**
     * 外键存在性校验：仅对非空 ID 校验，任一不存在即拒绝
     */
    private void validateRefs(AssetModel model) {
        if (model.getCategoryId() != null && categoryMapper.selectById(model.getCategoryId()) == null) {
            throw new BusinessException(400, "资产分类不存在（id=" + model.getCategoryId() + "）");
        }
        if (model.getManufacturerId() != null && manufacturerMapper.selectById(model.getManufacturerId()) == null) {
            throw new BusinessException(400, "厂商不存在（id=" + model.getManufacturerId() + "）");
        }
        if (model.getDepreciationId() != null && depreciationRuleMapper.selectById(model.getDepreciationId()) == null) {
            throw new BusinessException(400, "折旧规则不存在（id=" + model.getDepreciationId() + "）");
        }
        if (model.getCompanyId() != null && companyMapper.selectById(model.getCompanyId()) == null) {
            throw new BusinessException(400, "公司主体不存在（id=" + model.getCompanyId() + "）");
        }
    }
}
