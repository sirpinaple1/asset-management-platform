package com.sk.asset.service.basedata;

import com.sk.asset.entity.basedata.Location;

import java.util.List;

/**
 * 区域位置服务
 */
public interface LocationService {

    /**
     * 查询所有位置（扁平列表，含 parentId + path）
     */
    List<Location> list();

    /**
     * 查询指定父节点的所有子节点（含子孙节点）
     */
    List<Location> listChildren(Long parentId);

    /**
     * 按 ID 查询位置
     */
    Location getById(Long id);

    /**
     * 新增位置
     */
    void save(Location location);

    /**
     * 更新位置
     */
    void updateById(Location location);

    /**
     * 删除位置
     */
    void deleteById(Long id);
}
