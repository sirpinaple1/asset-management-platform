package com.sk.asset.service.basedata.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.entity.basedata.Manufacturer;
import com.sk.asset.mapper.basedata.ManufacturerMapper;
import com.sk.asset.service.basedata.ManufacturerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 厂商服务实现
 */
@Service
public class ManufacturerServiceImpl implements ManufacturerService {

    @Autowired
    private ManufacturerMapper manufacturerMapper;

    @Override
    public List<Manufacturer> list() {
        return manufacturerMapper.selectList(new LambdaQueryWrapper<>());
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
}
