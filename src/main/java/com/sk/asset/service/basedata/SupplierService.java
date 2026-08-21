package com.sk.asset.service.basedata;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sk.asset.entity.basedata.Supplier;

import java.util.List;

/**
 * 供应商服务接口
 */
public interface SupplierService {

    /**
     * 不分页查询（导出/下拉全量场景），支持 keyword 与 status 筛选
     */
    List<Supplier> listBy(String keyword, Integer status);

    /**
     * 分页查询，支持 keyword 与 status 筛选
     */
    IPage<Supplier> page(long current, long size, String keyword, Integer status);

    Supplier getById(Long id);

    void save(Supplier supplier);

    void updateById(Supplier supplier);

    /**
     * 删除单个供应商（被 asset 引用时抛 BusinessException 409）
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
