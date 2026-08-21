package com.sk.asset.service.asset.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.asset.AssetQuery;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.asset.AssetLog;
import com.sk.asset.entity.basedata.AssetModel;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.entity.basedata.Company;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.basedata.Supplier;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.mapper.asset.AssetLogMapper;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.AssetModelMapper;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.mapper.basedata.CompanyMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.basedata.SupplierMapper;
import com.sk.asset.service.asset.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资产服务实现。
 * 状态机：所有状态变更经 changeStatus，合法流转见 AssetStatus；
 * 每次变更写 asset_log（对齐"【字段】由【旧值】变更为【新值】"格式）。
 */
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final AssetMapper assetMapper;
    private final AssetLogMapper assetLogMapper;
    private final CategoryMapper categoryMapper;
    private final AssetModelMapper assetModelMapper;
    private final SupplierMapper supplierMapper;
    private final LocationMapper locationMapper;
    private final CompanyMapper companyMapper;

    @Override
    public IPage<Asset> page(long current, long size, AssetQuery query) {
        IPage<Asset> result = assetMapper.selectPage(new Page<>(current, size), buildWrapper(query));
        fillRelations(result.getRecords());
        return result;
    }

    @Override
    public List<Asset> listBy(AssetQuery query) {
        List<Asset> list = assetMapper.selectList(buildWrapper(query));
        fillRelations(list);
        return list;
    }

    private LambdaQueryWrapper<Asset> buildWrapper(AssetQuery query) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getStatus() != null) {
                wrapper.eq(Asset::getStatus, query.getStatus().name());
            }
            if (query.getCategoryId() != null) {
                wrapper.eq(Asset::getCategoryId, query.getCategoryId());
            }
            if (query.getLocationId() != null) {
                wrapper.eq(Asset::getLocationId, query.getLocationId());
            }
            if (query.getCompanyId() != null) {
                wrapper.eq(Asset::getCompanyId, query.getCompanyId());
            }
            if (query.getUserId() != null) {
                wrapper.eq(Asset::getUserId, query.getUserId());
            }
            if (query.getAdminUserId() != null) {
                wrapper.eq(Asset::getAdminUserId, query.getAdminUserId());
            }
            if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
                String keyword = query.getKeyword().trim();
                wrapper.and(w -> w.like(Asset::getBarcode, keyword)
                        .or().like(Asset::getName, keyword)
                        .or().like(Asset::getSn, keyword));
            }
        }
        wrapper.orderByDesc(Asset::getId);
        return wrapper;
    }

    @Override
    public Asset getById(Long id) {
        return assetMapper.selectByIdWithRelations(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Asset asset, Long operatorUserId) {
        validateBarcodeUnique(asset.getBarcode(), null);
        validateRefs(asset);
        asset.setId(null);
        asset.setStatus(AssetStatus.IDLE.name());
        assetMapper.insert(asset);
        assetLogMapper.insert(buildLog(asset.getId(), "新增", operatorUserId,
                "新增资产「" + asset.getName() + "」（编码 " + asset.getBarcode() + "）"));
    }

    @Override
    public void updateById(Asset asset) {
        Asset existing = assetMapper.selectById(asset.getId());
        if (existing == null) {
            throw new BusinessException(404, "资产不存在（id=" + asset.getId() + "）");
        }
        validateBarcodeUnique(asset.getBarcode(), asset.getId());
        validateRefs(asset);
        // 状态不随编辑变更（M03 约定：状态只能通过业务接口流转）
        asset.setStatus(existing.getStatus());
        assetMapper.updateById(asset);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void discard(Long id, String reason, Long operatorUserId) {
        changeStatus(id, AssetStatus.DISCARD, operatorUserId, reason);
    }

    @Override
    public List<AssetLog> listLogs(Long assetId) {
        return assetLogMapper.selectList(new LambdaQueryWrapper<AssetLog>()
                .eq(AssetLog::getAssetId, assetId)
                .orderByDesc(AssetLog::getCreatedAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long assetId, AssetStatus newStatus, Long operatorUserId, String note) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(404, "资产不存在（id=" + assetId + "）");
        }
        AssetStatus current = AssetStatus.of(asset.getStatus());
        if (!current.canTransitionTo(newStatus)) {
            throw new BusinessException(409, "资产状态不允许从「" + current.getLabel()
                    + "」变更为「" + newStatus.getLabel() + "」");
        }
        assetMapper.update(null, new LambdaUpdateWrapper<Asset>()
                .eq(Asset::getId, assetId)
                .set(Asset::getStatus, newStatus.name()));
        String content = "【状态】由【" + current.getLabel() + "】变更为【" + newStatus.getLabel() + "】";
        if (note != null && !note.isBlank()) {
            content += "：" + note.trim();
        }
        assetLogMapper.insert(buildLog(assetId, operationTypeOf(newStatus), operatorUserId, content));
    }

    /** 按目标状态推导日志操作类型（M04 落地时如需区分申请/审批语义再细化） */
    private String operationTypeOf(AssetStatus newStatus) {
        switch (newStatus) {
            case IN_USE:
            case PENDING_CONFIRM:
                return "领用";
            case IDLE:
                return "归还";
            case DISCARD:
                return "报废";
            default:
                return "实物信息变更";
        }
    }

    private AssetLog buildLog(Long assetId, String operationType, Long operatorUserId, String content) {
        AssetLog log = new AssetLog();
        log.setAssetId(assetId);
        log.setOperationType(operationType);
        log.setOperatorUserId(operatorUserId);
        log.setContent(content);
        return log;
    }

    private void validateBarcodeUnique(String barcode, Long excludeId) {
        Long count = assetMapper.selectCount(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getBarcode, barcode)
                .ne(excludeId != null, Asset::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(409, "资产编码「" + barcode + "」已存在");
        }
    }

    /**
     * 外键存在性校验：仅对非空 ID 校验，任一不存在即拒绝（对齐 AssetModel 模式）
     */
    private void validateRefs(Asset asset) {
        if (asset.getCategoryId() != null && categoryMapper.selectById(asset.getCategoryId()) == null) {
            throw new BusinessException(400, "资产分类不存在（id=" + asset.getCategoryId() + "）");
        }
        if (asset.getModelId() != null && assetModelMapper.selectById(asset.getModelId()) == null) {
            throw new BusinessException(400, "资产型号不存在（id=" + asset.getModelId() + "）");
        }
        if (asset.getSupplierId() != null && supplierMapper.selectById(asset.getSupplierId()) == null) {
            throw new BusinessException(400, "供应商不存在（id=" + asset.getSupplierId() + "）");
        }
        if (asset.getLocationId() != null && locationMapper.selectById(asset.getLocationId()) == null) {
            throw new BusinessException(400, "位置不存在（id=" + asset.getLocationId() + "）");
        }
        if (asset.getHomeLocationId() != null && locationMapper.selectById(asset.getHomeLocationId()) == null) {
            throw new BusinessException(400, "应归放位置不存在（id=" + asset.getHomeLocationId() + "）");
        }
        if (asset.getCompanyId() != null && companyMapper.selectById(asset.getCompanyId()) == null) {
            throw new BusinessException(400, "公司主体不存在（id=" + asset.getCompanyId() + "）");
        }
    }

    /**
     * 批量回填关联名称（列表/分页单表查询后的二次补齐，每类关联一次批量查询）
     */
    private void fillRelations(List<Asset> assets) {
        if (assets == null || assets.isEmpty()) {
            return;
        }
        Map<Long, String> categoryNames = nameMap(collectIds(assets, Asset::getCategoryId),
                categoryMapper::selectBatchIds, Category::getId, Category::getName);
        Map<Long, String> modelNames = nameMap(collectIds(assets, Asset::getModelId),
                assetModelMapper::selectBatchIds, AssetModel::getId, AssetModel::getName);
        Map<Long, String> supplierNames = nameMap(collectIds(assets, Asset::getSupplierId),
                supplierMapper::selectBatchIds, Supplier::getId, Supplier::getName);
        Set<Long> locationIds = new HashSet<>(collectIds(assets, Asset::getLocationId));
        locationIds.addAll(collectIds(assets, Asset::getHomeLocationId));
        Map<Long, String> locationNames = nameMap(locationIds,
                locationMapper::selectBatchIds, Location::getId, Location::getName);
        Map<Long, String> companyNames = nameMap(collectIds(assets, Asset::getCompanyId),
                companyMapper::selectBatchIds, Company::getId, Company::getName);

        assets.forEach(asset -> {
            asset.setCategoryName(categoryNames.get(asset.getCategoryId()));
            asset.setModelName(modelNames.get(asset.getModelId()));
            asset.setSupplierName(supplierNames.get(asset.getSupplierId()));
            asset.setLocationName(locationNames.get(asset.getLocationId()));
            asset.setHomeLocationName(locationNames.get(asset.getHomeLocationId()));
            asset.setCompanyName(companyNames.get(asset.getCompanyId()));
        });
    }

    private Set<Long> collectIds(List<Asset> assets, Function<Asset, Long> idGetter) {
        return assets.stream()
                .map(idGetter)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private <T> Map<Long, String> nameMap(Collection<Long> ids,
                                          Function<Collection<Long>, List<T>> batchLoader,
                                          Function<T, Long> idGetter,
                                          Function<T, String> nameGetter) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return batchLoader.apply(ids).stream()
                .collect(Collectors.toMap(idGetter, nameGetter, (a, b) -> a));
    }
}
