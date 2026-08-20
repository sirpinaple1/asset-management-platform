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
}
