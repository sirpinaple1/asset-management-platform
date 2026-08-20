package com.sk.asset.service.basedata.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.service.basedata.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 区域位置服务实现
 */
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final LocationMapper locationMapper;

    @Override
    public List<Location> list() {
        LambdaQueryWrapper<Location> query = new LambdaQueryWrapper<>();
        query.orderByAsc(Location::getPath)
             .orderByAsc(Location::getSortOrder);
        return locationMapper.selectList(query);
    }

    @Override
    public List<Location> listChildren(Long parentId) {
        if (parentId == null) {
            return list();
        }

        Location parent = locationMapper.selectById(parentId);
        if (parent == null || parent.getPath() == null) {
            return List.of();
        }

        return locationMapper.selectByParentPath(parent.getPath());
    }

    @Override
    public Location getById(Long id) {
        return locationMapper.selectById(id);
    }

    @Override
    public void save(Location location) {
        locationMapper.insert(location);
    }

    @Override
    public void updateById(Location location) {
        locationMapper.updateById(location);
    }

    @Override
    public void deleteById(Long id) {
        locationMapper.deleteById(id);
    }
}
