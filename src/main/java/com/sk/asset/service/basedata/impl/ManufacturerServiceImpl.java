package com.sk.asset.service.basedata.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.entity.basedata.Manufacturer;
import com.sk.asset.mapper.basedata.ManufacturerMapper;
import com.sk.asset.service.basedata.ManufacturerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 厂商服务实现
 */
@Service
@RequiredArgsConstructor
public class ManufacturerServiceImpl implements ManufacturerService {

    private final ManufacturerMapper manufacturerMapper;

    @Override
    public List<Manufacturer> list() {
        LambdaQueryWrapper<Manufacturer> query = new LambdaQueryWrapper<>();
        query.orderByAsc(Manufacturer::getId);
        return manufacturerMapper.selectList(query);
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
        manufacturerMapper.deleteById(id);
    }

    @Override
    public int restoreById(Long id) {
        return manufacturerMapper.restoreById(id);
    }
}
