package com.sk.asset.service.asset.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.asset.AssetQuery;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.asset.AssetLog;
import com.sk.asset.entity.basedata.AssetModel;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.entity.basedata.Company;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.basedata.Supplier;
import com.sk.asset.entity.receipt.AssetAllocation;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.mapper.asset.AssetLogMapper;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.AssetModelMapper;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.mapper.basedata.CompanyMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.basedata.SupplierMapper;
import com.sk.asset.mapper.receipt.AssetAllocationMapper;
import com.sk.asset.service.asset.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
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
    private final AssetAllocationMapper allocationMapper;
    private final CategoryMapper categoryMapper;
    private final AssetModelMapper assetModelMapper;
    private final SupplierMapper supplierMapper;
    private final LocationMapper locationMapper;
    private final CompanyMapper companyMapper;
    private final UserDirectory userDirectory;

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
                List<List<String>> groups = parseKeyword(query.getKeyword());
                if (!groups.isEmpty()) {
                    // 组间 OR（空格分隔）、组内子词 AND（"-"分隔），每个子词对 编码/名称/序列号 模糊匹配。
                    // 注意：组间连接必须用 or(consumer) 重载，无参 or() 对 and(consumer) 不生效。
                    wrapper.and(w -> {
                        for (int i = 0; i < groups.size(); i++) {
                            List<String> terms = groups.get(i);
                            java.util.function.Consumer<LambdaQueryWrapper<Asset>> groupCond = g ->
                                    terms.forEach(term -> g.and(t -> t.like(Asset::getBarcode, term)
                                            .or().like(Asset::getName, term)
                                            .or().like(Asset::getSn, term)));
                            if (i == 0) {
                                w.and(groupCond);
                            } else {
                                w.or(groupCond);
                            }
                        }
                    });
                }
            }
        }
        wrapper.orderByDesc(Asset::getId);
        return wrapper;
    }

    /**
     * 解析搜索关键词：按空白切分为多个词组（组间 OR），词组内按 "-" 切分为多个子词（组内 AND），
     * 每个子词对 编码/名称/序列号 三字段模糊匹配。
     * 示例："笔记本 台式机" 命中含任一关键词的资产；"笔记本-Lenovo" 需同时包含两者。
     * 纯分隔符输入（如 "--"）解析为空列表，等价于无关键词。
     */
    private List<List<String>> parseKeyword(String raw) {
        return Arrays.stream(raw.trim().split("[\\s\\u3000]+"))
                .filter(s -> !s.isBlank())
                .map(group -> Arrays.stream(group.split("-"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList())
                .filter(terms -> !terms.isEmpty())
                .toList();
    }

    @Override
    public Asset getById(Long id) {
        Asset asset = assetMapper.selectByIdWithRelations(id);
        if (asset != null) {
            // 详情关联名称走 SQL JOIN，用户姓名在 comm_public_basic 库无法 JOIN，单独反查回填
            fillUserNames(List.of(asset));
        }
        return asset;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Asset asset, Long operatorUserId) {
        validateRefs(asset);
        asset.setId(null);
        asset.setStatus(AssetStatus.IDLE.name());
        asset.setBarcode(generateBarcode(asset.getCategoryId()));
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
        // barcode 可空：编辑未传编码时保持原编码（MP updateById 跳过 null 列）
        if (asset.getBarcode() != null && !asset.getBarcode().isBlank()) {
            validateBarcodeUnique(asset.getBarcode(), asset.getId());
        } else {
            asset.setBarcode(null);
        }
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
        changeStatus(assetId, newStatus, operatorUserId, operationTypeOf(newStatus), note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long assetId, AssetStatus newStatus, Long operatorUserId,
                             String operationType, String note) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(404, "资产不存在（id=" + assetId + "）");
        }
        AssetStatus current = AssetStatus.of(asset.getStatus());
        if (!current.canTransitionTo(newStatus)) {
            throw new BusinessException(409, "资产状态不允许从「" + current.getLabel()
                    + "」变更为「" + newStatus.getLabel() + "」");
        }
        // 条件更新（CAS）：WHERE 携带旧状态，并发下后到者 affected=0 → 409 整体回滚。
        // 防同一资产被两张单同时占用（如双单并发提领用：先提交者占住 PENDING_CONFIRM，
        // 后提交者此处 IN_USE→PENDING_CONFIRM 条件不命中即失败，不再依赖前置校验的时序）
        int updated = assetMapper.update(null, new LambdaUpdateWrapper<Asset>()
                .eq(Asset::getId, assetId)
                .eq(Asset::getStatus, current.name())
                .set(Asset::getStatus, newStatus.name()));
        if (updated == 0) {
            throw new BusinessException(409, "资产状态已变化，操作冲突请刷新重试（id=" + assetId + "）");
        }
        String content = "【状态】由【" + current.getLabel() + "】变更为【" + newStatus.getLabel() + "】";
        if (note != null && !note.isBlank()) {
            content += "：" + note.trim();
        }
        // 报废联动：闭环持有中的 asset_allocation 并清空资产持有人，
        // 避免在用报废后持有关系悬死（退库入口因 DISCARD→IDLE 非法被 409 卡死，前端永远显示持有中）
        if (newStatus == AssetStatus.DISCARD) {
            content += closeActiveAllocationsOnDiscard(assetId, note);
        }
        assetLogMapper.insert(buildLog(assetId, operationType, operatorUserId, content));
    }

    /**
     * 报废联动闭环：将该资产持有中（returned_at 为空）的 asset_allocation 全部终结
     * （returned_at=now，note 记报废原因），并清空资产 user_id/user_department。
     * PENDING_CONFIRM→DISCARD 已被状态机阻止，待审批单据不经过此路径。
     *
     * @return 追加到报废日志的内容（无持有关系时为空串）
     */
    private String closeActiveAllocationsOnDiscard(Long assetId, String note) {
        List<AssetAllocation> active = allocationMapper.selectList(new LambdaQueryWrapper<AssetAllocation>()
                .eq(AssetAllocation::getAssetId, assetId)
                .isNull(AssetAllocation::getReturnedAt));
        if (active.isEmpty()) {
            return "";
        }
        String closureNote = "资产报废，持有关系随报废终结";
        if (note != null && !note.isBlank()) {
            closureNote += "：" + note.trim();
        }
        for (AssetAllocation allocation : active) {
            allocationMapper.update(null, new LambdaUpdateWrapper<AssetAllocation>()
                    .eq(AssetAllocation::getId, allocation.getId())
                    .set(AssetAllocation::getReturnedAt, LocalDateTime.now())
                    .set(AssetAllocation::getNote, closureNote));
        }
        // 报废为终态，清空资产持有人（持有人随持有关系一并终结）
        assetMapper.update(null, new LambdaUpdateWrapper<Asset>()
                .eq(Asset::getId, assetId)
                .set(Asset::getUserId, null)
                .set(Asset::getUserDepartment, null));
        String holders = active.stream()
                .map(a -> a.getUserName() != null && !a.getUserName().isBlank()
                        ? a.getUserName() : String.valueOf(a.getUserId()))
                .distinct()
                .collect(Collectors.joining("、"));
        return "；持有人【" + holders + "】的持有关系随报废终结";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void writeLog(Long assetId, String operationType, Long operatorUserId, String content) {
        if (assetMapper.selectById(assetId) == null) {
            throw new BusinessException(404, "资产不存在（id=" + assetId + "）");
        }
        assetLogMapper.insert(buildLog(assetId, operationType, operatorUserId, content));
    }

    /** 按目标状态推导日志操作类型（默认入口；M04 单据流显式传操作类型区分领用/借用） */
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

    /**
     * 生成资产编码：分类前缀-yyyyMMdd-4位序号（如 SKSCDM-20260821-0001）。
     * 前缀取 asset_category.barcode_prefix（沿用旧系统编码约定，见 M08 迁移文档），
     * 分类自身未配置前缀时沿 parent 链向上继承最近的大类前缀
     * （V20260832 两级分类后，资产直接挂大类时用大类前缀如 SKBG/SKSC，
     * 不至于回退 SK）；全链为空或未选分类时回退 SK；日期段与旧资产编码
     * （前缀-序号）命名空间隔离，M08 历史迁移 upsert 不冲突。
     * 序号取号与 ARE/BOR 单号同模式：
     * likeRight + orderByDesc + LIMIT 1 FOR UPDATE 锁定读串行，uk_asset_barcode 兜底。
     */
    private String generateBarcode(Long categoryId) {
        String prefix = resolveBarcodePrefix(categoryId);
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String scope = prefix + "-" + datePart + "-";
        Asset latest = assetMapper.selectOne(new LambdaQueryWrapper<Asset>()
                .likeRight(Asset::getBarcode, scope)
                .orderByDesc(Asset::getBarcode)
                .last("LIMIT 1 FOR UPDATE"));
        int next = 1;
        if (latest != null && latest.getBarcode() != null
                && latest.getBarcode().length() > scope.length()) {
            next = Integer.parseInt(latest.getBarcode().substring(scope.length())) + 1;
        }
        return scope + String.format("%04d", next);
    }

    /**
     * 解析分类编码前缀：分类自身 barcode_prefix 为空时沿 parent 链向上取最近的非空值，
     * 全链为空或未选分类回退 SK。分类树强制两级（CategoryServiceImpl 校验），
     * 深度上限仅作防御（页面数据异常时不死循环）。
     */
    private String resolveBarcodePrefix(Long categoryId) {
        Long id = categoryId;
        for (int depth = 0; id != null && depth < 5; depth++) {
            Category category = categoryMapper.selectById(id);
            if (category == null) {
                break;
            }
            if (category.getBarcodePrefix() != null && !category.getBarcodePrefix().isBlank()) {
                return category.getBarcodePrefix();
            }
            id = category.getParentId();
        }
        return "SK";
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
        fillUserNames(assets);
    }

    /**
     * 实时反查 comm_public_basic sys_user 回填使用人/资产管理员姓名
     * （ADR-0004：asset 库不存用户主数据，跨库无法 JOIN，列表/详情统一走此反查）。
     * userId/adminUserId 合并一次批量查询；用户未配置连接时 namesByIds 返回空 Map，
     * 姓名留空不阻塞列表；用户被删除/未命中 name 置 null（前端兜底显示 —）。
     * 注：userDepartment 保持业务时点快照口径（领用/调拨/变更确认时写入），
     * 与实时姓名可能不一致——单据凭证语义优先，不做实时化。
     */
    private void fillUserNames(List<Asset> assets) {
        Set<Long> userIds = new HashSet<>(collectIds(assets, Asset::getUserId));
        userIds.addAll(collectIds(assets, Asset::getAdminUserId));
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, UserResp> users = userDirectory.namesByIds(userIds);
        assets.forEach(asset -> {
            // userId/adminUserId 可为 null（闲置资产无持有人），null key 查询对不可变 Map 会 NPE，先判空
            UserResp user = asset.getUserId() != null ? users.get(asset.getUserId()) : null;
            asset.setUserName(user != null ? user.name() : null);
            UserResp admin = asset.getAdminUserId() != null ? users.get(asset.getAdminUserId()) : null;
            asset.setAdminUserName(admin != null ? admin.name() : null);
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
