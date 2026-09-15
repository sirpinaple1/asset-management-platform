package com.sk.asset.service.basedata;

import com.sk.asset.entity.basedata.AssetModel;

import java.util.List;

/**
 * 资产型号服务
 */
public interface AssetModelService {

    /**
     * 查询所有型号（含关联名称）
     */
    List<AssetModel> list();

    /**
     * 按分类查询型号（含关联名称）
     */
    List<AssetModel> listByCategoryId(Long categoryId);

    /**
     * 按 ID 查询型号
     */
    AssetModel getById(Long id);

    /**
     * 新增型号
     */
    void save(AssetModel model);

    /**
     * 更新型号
     */
    void updateById(AssetModel model);

    /**
     * 删除型号
     */
    void deleteById(Long id);
}
