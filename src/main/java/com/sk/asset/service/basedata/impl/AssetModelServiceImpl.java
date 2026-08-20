package com.sk.asset.service.basedata.impl;

import com.sk.asset.entity.basedata.AssetModel;
import com.sk.asset.mapper.basedata.AssetModelMapper;
import com.sk.asset.service.basedata.AssetModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资产型号服务实现
 */
@Service
@RequiredArgsConstructor
public class AssetModelServiceImpl implements AssetModelService {

    private final AssetModelMapper assetModelMapper;

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
        return assetModelMapper.selectById(id);
    }

    @Override
    public void save(AssetModel model) {
        assetModelMapper.insert(model);
    }

    @Override
    public void updateById(AssetModel model) {
        assetModelMapper.updateById(model);
    }

    @Override
    public void deleteById(Long id) {
        assetModelMapper.deleteById(id);
    }
}
