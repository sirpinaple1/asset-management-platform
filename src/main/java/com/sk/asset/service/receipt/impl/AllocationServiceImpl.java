package com.sk.asset.service.receipt.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.receipt.AllocationQuery;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.receipt.AssetAllocation;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.receipt.AssetAllocationMapper;
import com.sk.asset.service.asset.AssetService;
import com.sk.asset.service.receipt.AllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资产持有关系服务实现。归还 = allocation 闭环 + 资产状态机流转 + 持有人清空，
 * 全部在一个事务内（任一步失败整体回滚）。
 */
@Service
@RequiredArgsConstructor
public class AllocationServiceImpl implements AllocationService {

    private final AssetAllocationMapper allocationMapper;
    private final AssetMapper assetMapper;
    private final AssetService assetService;
    private final com.sk.asset.service.approval.ApprovalConfigService approvalConfigService;

    @Override
    public List<AssetAllocation> list(AllocationQuery query) {
        LambdaQueryWrapper<AssetAllocation> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getAssetId() != null) {
                wrapper.eq(AssetAllocation::getAssetId, query.getAssetId());
            }
            if (query.getUserId() != null) {
                wrapper.eq(AssetAllocation::getUserId, query.getUserId());
            }
            if (query.getType() != null) {
                wrapper.eq(AssetAllocation::getType, query.getType().name());
            }
            if (Boolean.TRUE.equals(query.getActive())) {
                wrapper.isNull(AssetAllocation::getReturnedAt);
            } else if (Boolean.FALSE.equals(query.getActive())) {
                wrapper.isNotNull(AssetAllocation::getReturnedAt);
            }
        }
        wrapper.orderByDesc(AssetAllocation::getId);
        List<AssetAllocation> allocations = allocationMapper.selectList(wrapper);
        fillAssets(allocations);
        return allocations;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnAllocation(Long id, String note, Long operatorUserId) {
        AssetAllocation allocation = allocationMapper.selectById(id);
        if (allocation == null) {
            throw new BusinessException(404, "持有记录不存在（id=" + id + "）");
        }
        if (allocation.getReturnedAt() != null) {
            throw new BusinessException(409, "该资产已归还（归还时间：" + allocation.getReturnedAt() + "）");
        }

        allocationMapper.update(null, new LambdaUpdateWrapper<AssetAllocation>()
                .eq(AssetAllocation::getId, id)
                .set(AssetAllocation::getReturnedAt, LocalDateTime.now())
                .set(note != null && !note.isBlank(), AssetAllocation::getNote, note));

        // 资产 IN_USE → IDLE（写日志）；状态不合法（如已报废）时 409 整体回滚
        String holder = allocation.getUserName() != null && !allocation.getUserName().isBlank()
                ? allocation.getUserName() : String.valueOf(allocation.getUserId());
        String noteText = "持有记录 #" + id + " 归还，原持有人：" + holder;
        if (note != null && !note.isBlank()) {
            noteText += "（" + note.trim() + "）";
        }
        assetService.changeStatus(allocation.getAssetId(), AssetStatus.IDLE, operatorUserId, "归还", noteText);

        // B4（2026-08-31 决策）：归还带回位置——资产位置回置到发放前快照（A 区），
        // 存量记录无快照时回退 home_location_id；两者皆空则位置保持不动。
        // 接口无位置参数，归还位置完全由快照决定（不可手改）。位置回置同时区域管理员实时解析。
        Asset asset = assetMapper.selectById(allocation.getAssetId());
        Long backLocation = allocation.getLocationBefore() != null
                ? allocation.getLocationBefore()
                : (asset != null ? asset.getHomeLocationId() : null);

        // 资产持有人仍为本记录持有人时清空（期间已转让给他人则不动）；
        // 持有人未转移时位置一并回置（转让给他人则位置归新持有人的领用区域管）
        LambdaUpdateWrapper<Asset> clearHolder = new LambdaUpdateWrapper<Asset>()
                .eq(Asset::getId, allocation.getAssetId())
                .eq(Asset::getUserId, allocation.getUserId())
                .set(Asset::getUserId, null)
                .set(Asset::getUserDepartment, null);
        if (backLocation != null) {
            clearHolder.set(Asset::getLocationId, backLocation)
                    .set(Asset::getAdminUserId, approvalConfigService.keeperUserIdOf(backLocation));
        }
        assetMapper.update(null, clearHolder);
    }

    /** 批量回填资产编码/名称/序列号 */
    private void fillAssets(List<AssetAllocation> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return;
        }
        Set<Long> assetIds = allocations.stream()
                .map(AssetAllocation::getAssetId)
                .collect(Collectors.toSet());
        Map<Long, Asset> assets = assetMapper.selectBatchIds(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));
        allocations.forEach(allocation -> {
            Asset asset = assets.get(allocation.getAssetId());
            if (asset != null) {
                allocation.setAssetBarcode(asset.getBarcode());
                allocation.setAssetName(asset.getName());
                allocation.setAssetSn(asset.getSn());
            }
        });
    }
}
