package com.sk.asset.service.basedata.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.basedata.Manufacturer;
import com.sk.asset.mapper.basedata.ManufacturerMapper;
import com.sk.asset.service.basedata.ManufacturerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 厂商服务实现
 */
@Service
@RequiredArgsConstructor
public class ManufacturerServiceImpl implements ManufacturerService {

    private final ManufacturerMapper manufacturerMapper;

    @Override
    public List<Manufacturer> listBy(String keyword, Integer status) {
        return manufacturerMapper.selectList(buildQuery(keyword, status));
    }

    @Override
    public IPage<Manufacturer> page(long current, long size, String keyword, Integer status) {
        return manufacturerMapper.selectPage(new Page<>(current, size), buildQuery(keyword, status));
    }

    private LambdaQueryWrapper<Manufacturer> buildQuery(String keyword, Integer status) {
        LambdaQueryWrapper<Manufacturer> query = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            query.like(Manufacturer::getName, keyword.trim());
        }
        if (status != null) {
            query.eq(Manufacturer::getStatus, status);
        }
        query.orderByDesc(Manufacturer::getId);
        return query;
    }

    @Override
    public Manufacturer getById(Long id) {
        return manufacturerMapper.selectById(id);
    }

    @Override
    public void save(Manufacturer manufacturer) {
        manufacturerMapper.insert(manufacturer);
    }

    @Override
    public void updateById(Manufacturer manufacturer) {
        manufacturerMapper.updateById(manufacturer);
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
        List<Manufacturer> existing = manufacturerMapper.selectBatchIds(ids);
        List<String> referenced = existing.stream()
                .filter(m -> manufacturerMapper.countModelRefs(m.getId()) > 0)
                .map(Manufacturer::getName)
                .collect(Collectors.toList());
        if (!referenced.isEmpty()) {
            throw new BusinessException(409, "厂商「" + String.join("」「", referenced) + "」已被资产型号引用，无法删除");
        }
        existing.forEach(m -> manufacturerMapper.deleteById(m.getId()));
    }

    @Override
    public int restoreById(Long id) {
        return manufacturerMapper.restoreById(id);
    }
}
