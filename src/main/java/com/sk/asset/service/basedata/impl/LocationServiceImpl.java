package com.sk.asset.service.basedata.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.service.basedata.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 区域位置服务实现。
 * 树完整性保障（REVIEW-M02 P1②）：path 一律由服务端按 parentId 派生（格式 /{id}/.../{id}/，
 * 顶级为 /{id}/），客户端传入的 path 不生效；parentId 做存在性校验；移动节点时整体重算子树 path。
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
    @Transactional(rollbackFor = Exception.class)
    public void save(Location location) {
        validateParent(location.getParentId(), null);
        location.setPath(null);
        locationMapper.insert(location);
        // insert 后主键回填，按 parentId 派生 materialized path
        location.setPath(derivePath(location.getParentId(), location.getId()));
        locationMapper.updateById(location);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateById(Location location) {
        Location existing = locationMapper.selectById(location.getId());
        if (existing == null) {
            throw new BusinessException(404, "位置不存在");
        }

        Long newParentId = location.getParentId();
        if (!Objects.equals(newParentId, existing.getParentId())) {
            // 移动节点：校验新父合法（存在 + 不成环），然后整体重算子树 path
            validateParent(newParentId, existing);
            String newPath = derivePath(newParentId, existing.getId());
            locationMapper.updateSubtreePaths(existing.getPath(), newPath);
            location.setPath(newPath);
        } else {
            // 未移动：path 保持库中现值，不接受客户端指定
            location.setPath(existing.getPath());
        }
        locationMapper.updateById(location);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        Location location = locationMapper.selectById(id);
        if (location == null) {
            throw new BusinessException(404, "位置不存在");
        }
        Long childCount = locationMapper.selectCount(
                new LambdaQueryWrapper<Location>().eq(Location::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(409, "位置「" + location.getName() + "」存在子位置，请先删除子位置");
        }
        if (locationMapper.countAssetRefs(id) > 0) {
            throw new BusinessException(409, "位置「" + location.getName() + "」已被资产引用，无法删除");
        }
        locationMapper.deleteById(id);
    }

    /**
     * 校验父节点存在且不与自身成环（移动场景）
     */
    private void validateParent(Long parentId, Location self) {
        if (parentId == null) {
            return;
        }
        if (self != null && parentId.equals(self.getId())) {
            throw new BusinessException(400, "父位置不能是自身");
        }
        Location parent = locationMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(400, "父位置不存在（id=" + parentId + "）");
        }
        if (self != null && self.getPath() != null && parent.getPath() != null
                && parent.getPath().startsWith(self.getPath())) {
            throw new BusinessException(400, "不能移动到自身子节点下");
        }
    }

    /**
     * 派生 materialized path：顶级 /{id}/，子级 {parentPath}{id}/
     */
    private String derivePath(Long parentId, Long id) {
        if (parentId == null) {
            return "/" + id + "/";
        }
        Location parent = locationMapper.selectById(parentId);
        if (parent == null || parent.getPath() == null) {
            throw new BusinessException(400, "父位置不存在（id=" + parentId + "）");
        }
        return parent.getPath() + id + "/";
    }
}
