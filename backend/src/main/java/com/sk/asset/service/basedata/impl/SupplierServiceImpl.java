package com.sk.asset.service.basedata.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.basedata.Supplier;
import com.sk.asset.mapper.basedata.SupplierMapper;
import com.sk.asset.service.basedata.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 供应商服务实现
 */
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private final SupplierMapper supplierMapper;

    @Override
    public List<Supplier> listBy(String keyword, Integer status) {
        return supplierMapper.selectList(buildQuery(keyword, status));
    }

    @Override
    public IPage<Supplier> page(long current, long size, String keyword, Integer status) {
        return supplierMapper.selectPage(new Page<>(current, size), buildQuery(keyword, status));
    }

    private LambdaQueryWrapper<Supplier> buildQuery(String keyword, Integer status) {
        LambdaQueryWrapper<Supplier> query = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            query.like(Supplier::getName, keyword.trim());
        }
        if (status != null) {
            query.eq(Supplier::getStatus, status);
        }
        query.orderByDesc(Supplier::getId);
        return query;
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
        deleteByIds(List.of(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Supplier> existing = supplierMapper.selectBatchIds(ids);
        List<String> referenced = existing.stream()
                .filter(s -> supplierMapper.countAssetRefs(s.getId()) > 0)
                .map(Supplier::getName)
                .collect(Collectors.toList());
        if (!referenced.isEmpty()) {
            throw new BusinessException(409, "供应商「" + String.join("」「", referenced) + "」已被资产引用，无法删除");
        }
        existing.forEach(s -> supplierMapper.deleteById(s.getId()));
    }

    @Override
    public int restoreById(Long id) {
        return supplierMapper.restoreById(id);
    }
}
