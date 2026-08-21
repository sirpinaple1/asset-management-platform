package com.sk.asset.service.basedata;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sk.asset.entity.basedata.Manufacturer;

import java.util.List;

/**
 * 厂商服务
 */
public interface ManufacturerService {

    /**
     * 不分页查询（导出/下拉全量场景），支持 keyword 与 status 筛选
     */
    List<Manufacturer> listBy(String keyword, Integer status);

    /**
     * 分页查询，支持 keyword 与 status 筛选
     */
    IPage<Manufacturer> page(long current, long size, String keyword, Integer status);

    Manufacturer getById(Long id);

    void save(Manufacturer manufacturer);

    void updateById(Manufacturer manufacturer);

    /**
     * 删除单个厂商（被 asset_model 引用时抛 BusinessException 409）
     */
    void deleteById(Long id);

    /**
     * 批量删除：全部校验通过才执行删除，任一被引用则整体拒绝（409）
     */
    void deleteByIds(List<Long> ids);

    /**
     * 恢复逻辑删除的记录，返回影响行数（0 表示不存在或未被删除）
     */
    int restoreById(Long id);
}
