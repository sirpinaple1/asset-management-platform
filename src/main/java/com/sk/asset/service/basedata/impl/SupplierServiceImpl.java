package com.sk.asset.service.basedata.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.entity.basedata.Supplier;
import com.sk.asset.mapper.basedata.SupplierMapper;
import com.sk.asset.service.basedata.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 供应商服务实现
 */
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierMapper supplierMapper;

    @Override
    public List<Supplier> list() {
        LambdaQueryWrapper<Supplier> query = new LambdaQueryWrapper<>();
        query.orderByAsc(Supplier::getId);
        return supplierMapper.selectList(query);
    }

    @Override
    public Supplier getById(Long id) {
        return supplierMapper.selectById(id);
    }

    @Override
    public void save(Supplier supplier) {
        supplierMapper.insert(supplier);
    }

    @Override
    public void updateById(Supplier supplier) {
        supplierMapper.updateById(supplier);
    }

    @Override
    public void deleteById(Long id) {
        supplierMapper.deleteById(id);
    }

    @Override
    public int restoreById(Long id) {
        return supplierMapper.restoreById(id);
    }
}
