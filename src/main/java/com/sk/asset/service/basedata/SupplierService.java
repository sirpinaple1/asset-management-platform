package com.sk.asset.service.basedata;

import com.sk.asset.entity.basedata.Supplier;

import java.util.List;

/**
 * 供应商服务接口
 */
public interface SupplierService {

    List<Supplier> list();

    Supplier getById(Long id);

    void save(Supplier supplier);

    void updateById(Supplier supplier);

    void deleteById(Long id);

    /**
     * 恢复逻辑删除的记录，返回影响行数（0 表示不存在或未被删除）
     */
    int restoreById(Long id);
}
