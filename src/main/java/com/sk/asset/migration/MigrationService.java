package com.sk.asset.migration;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.asset.AssetLog;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.entity.basedata.Company;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.entity.basedata.Supplier;
import com.sk.asset.entity.receipt.AssetAllocation;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.mapper.asset.AssetLogMapper;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.mapper.basedata.CompanyMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.mapper.basedata.SupplierMapper;
import com.sk.asset.mapper.receipt.AssetAllocationMapper;
import com.sk.asset.mapper.receipt.ReceiveReceiptMapper;
import com.sk.asset.mapper.transfer.TransferOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M08 历史数据迁移编排：基础数据 → 资产 → 领用单 → 调拨单 → 操作日志。
 *
 * <p>幂等策略：</p>
 * <ul>
 *   <li>资产按 barcode、单据按 serial_no upsert（存在则全字段刷新为 Excel 基线）</li>
 *   <li>在用持有关系（asset_allocation）仅补建：已存在持有中的资产跳过</li>
 *   <li>asset_log 按"时间区段清除 + 重灌"：run 开始时删除 created_at 早于
 *       app.migration.log-cutoff（默认 2026-08-19，新系统上线日）的全部日志后重写——
 *       新系统业务日志不可能早于上线时间，该区段只可能来自上次迁移</li>
 * </ul>
 *
 * <p>已知数据局限（旧系统导出契约）：领用单/调拨单导出无资产明细列，
 * 单据只迁移主表（items 为空）；资产无 model 维度，model_id 不迁移。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    // ---- AssetCard 列索引（31 列，见 M08 文档） ----
    private static final int AC_STATUS = 0;
    private static final int AC_BARCODE = 3;
    private static final int AC_NAME = 6;
    private static final int AC_CATEGORY = 8;
    private static final int AC_SPECS = 9;
    private static final int AC_SN = 11;
    private static final int AC_AMOUNT = 13;
    private static final int AC_USER_DEPT = 15;
    private static final int AC_USER = 16;
    private static final int AC_AREA_PATH = 18;
    private static final int AC_LOCATION_DETAIL = 19;
    private static final int AC_ADMIN = 20;
    private static final int AC_OWNER_COMPANY = 21;
    private static final int AC_PURCHASED_DATE = 22;
    private static final int AC_VENDOR = 24;
    private static final int AC_CREATED_DATE = 28;

    // ---- 领用单列索引（11 列） ----
    private static final int RC_STATUS = 0;
    private static final int RC_SERIAL = 1;
    private static final int RC_DATE = 2;
    private static final int RC_USER = 3;
    private static final int RC_DEPT = 5;
    private static final int RC_AREA = 6;
    private static final int RC_REMARKS = 8;
    private static final int RC_PROCESSOR = 9;

    // ---- 调拨单列索引（10 列） ----
    private static final int TR_STATUS = 0;
    private static final int TR_NO = 1;
    private static final int TR_OUT_DATE = 2;
    private static final int TR_OUT_ADMIN = 3;
    private static final int TR_IN_DATE = 5;
    private static final int TR_IN_ADMIN = 6;
    private static final int TR_REMARKS = 8;
    private static final int TR_IN_DEPT = 9;

    // ---- 操作日志列索引（24 列，第 0 行为标题行，表头在第 1 行） ----
    private static final int LG_BARCODE = 2;
    private static final int LG_OP_TYPE = 20;
    private static final int LG_TIME = 21;
    private static final int LG_OPERATOR = 22;
    private static final int LG_CONTENT = 23;

    private static final String COMPANY_SK = "森科五金(深圳)有限公司";
    private static final String COMPANY_SF = "森丰";

    private final AssetMapper assetMapper;
    private final AssetLogMapper assetLogMapper;
    private final AssetAllocationMapper allocationMapper;
    private final CategoryMapper categoryMapper;
    private final CompanyMapper companyMapper;
    private final LocationMapper locationMapper;
    private final SupplierMapper supplierMapper;
    private final ReceiveReceiptMapper receiptMapper;
    private final TransferOrderMapper transferMapper;
    private final MigrationProperties props;

    // ---- 运行期缓存（单次 run 内有效） ----
    private AuthUserDirectory directory;
    private final Map<String, Long> companyIdByName = new HashMap<>();
    private final Map<String, Long> categoryIdByName = new HashMap<>();
    private final Map<String, Long> locationIdByPath = new HashMap<>();
    private final Map<String, Long> locationIdByName = new HashMap<>();
    private final Map<String, Long> supplierIdByName = new HashMap<>();
    private final Map<Long, Location> locationsById = new HashMap<>();
    /** 位置树子级缓存：parentId(0=顶级) → name → 节点 */
    private final Map<Long, Map<String, Location>> locationChildren = new HashMap<>();

    @Transactional(rollbackFor = Exception.class)
    public MigrationResult run() {
        MigrationResult result = new MigrationResult();
        result.setStartedAt(LocalDateTime.now());
        try (Connection conn = openAuthDbConnection()) {
            directory = AuthUserDirectory.load(conn,
                    new AuthUserDirectory.JdbcAccountCreator(conn, props.getDefaultPassword()),
                    props.isAutoCreateUsers());

            List<Map<Integer, String>> assetRows = readRows(props.resolve(props.getAssetFile()), 1);
            List<Map<Integer, String>> receiptRows = readRows(props.resolve(props.getReceiptFile()), 1);
            List<Map<Integer, String>> transferRows = readRows(props.resolve(props.getTransferFile()), 1);
            List<Map<Integer, String>> logRows = readRows(props.resolve(props.getLogFile()), 2);

            // 幂等：清除上次迁移写入的历史时间区段日志（业务日志不可能早于新系统上线时间）
            int removedLogs = assetLogMapper.delete(new LambdaQueryWrapper<AssetLog>()
                    .lt(AssetLog::getCreatedAt, props.getLogCutoff()));
            if (removedLogs > 0) {
                result.addWarning("[幂等] 清除上次迁移日志 " + removedLogs + " 条（created_at < "
                        + props.getLogCutoff() + "），本次重灌");
            }

            importBaseData(assetRows, result);
            importAssets(assetRows, result);
            importReceipts(receiptRows, result);
            importTransfers(transferRows, result);
            importLogs(logRows, result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "迁移执行失败：" + e.getMessage());
        }
        result.setCreatedUsers(directory.getCreatedUsernames());
        result.getWarnings().addAll(directory.getWarnings());
        result.setFinishedAt(LocalDateTime.now());
        log.info("M08 迁移完成：{}", result.getPhases());
        return result;
    }

    // =====================================================================
    // 基础数据：公司 / 分类 / 位置树 / 供应商（名称 upsert）
    // =====================================================================

    private void importBaseData(List<Map<Integer, String>> assetRows, MigrationResult result) {
        MigrationResult.PhaseResult phase = new MigrationResult.PhaseResult("基础数据（公司/分类/位置/供应商）");
        result.getPhases().add(phase);

        loadCompanies();
        ensureCompany(COMPANY_SK, "SK", "M08 历史迁移校验（预期已由种子数据存在）", phase);
        ensureCompany(COMPANY_SF, "SF", "M08 历史迁移：原系统 SF 前缀资产归属主体", phase);

        loadCategories();
        List<String> categoryNames = distinct(assetRows, AC_CATEGORY);
        int order = 90;
        for (String name : categoryNames) {
            if (!categoryIdByName.containsKey(name)) {
                Category c = new Category();
                c.setName(name);
                c.setSortOrder(order++);
                c.setRemark("M08 历史迁移");
                categoryMapper.insert(c);
                categoryIdByName.put(name, c.getId());
                phase.inserted();
            } else {
                phase.skipped();
            }
        }

        loadLocations();
        for (String path : distinct(assetRows, AC_AREA_PATH)) {
            locationIdByPath.put(path, resolveOrCreateLocation(path, phase));
        }
        refreshLocationNameIndex();

        loadSuppliers();
        for (String vendor : distinct(assetRows, AC_VENDOR)) {
            if (!supplierIdByName.containsKey(vendor)) {
                Supplier s = new Supplier();
                s.setName(vendor);
                s.setRemark("M08 历史迁移");
                supplierMapper.insert(s);
                supplierIdByName.put(vendor, s.getId());
                phase.inserted();
            } else {
                phase.skipped();
            }
        }
    }

    private void loadCompanies() {
        for (Company c : companyMapper.selectList(null)) {
            companyIdByName.putIfAbsent(c.getName(), c.getId());
        }
    }

    private void ensureCompany(String name, String code, String remark,
                               MigrationResult.PhaseResult phase) {
        if (!companyIdByName.containsKey(name)) {
            Company c = new Company();
            c.setCode(code);
            c.setName(name);
            c.setRemark(remark);
            companyMapper.insert(c);
            companyIdByName.put(name, c.getId());
            phase.inserted();
        } else {
            phase.skipped();
        }
    }

    private void loadCategories() {
        for (Category c : categoryMapper.selectList(null)) {
            categoryIdByName.putIfAbsent(c.getName(), c.getId());
        }
    }

    private void loadSuppliers() {
        for (Supplier s : supplierMapper.selectList(null)) {
            supplierIdByName.putIfAbsent(s.getName(), s.getId());
        }
    }

    private void loadLocations() {
        for (Location l : locationMapper.selectList(null)) {
            locationsById.put(l.getId(), l);
        }
        rebuildLocationChildren();
    }

    private void rebuildLocationChildren() {
        locationChildren.clear();
        for (Location l : locationsById.values()) {
            long parent = l.getParentId() == null ? 0L : l.getParentId();
            locationChildren.computeIfAbsent(parent, k -> new HashMap<>()).put(l.getName(), l);
        }
    }

    /**
     * 按路径（如 "森科/PMC部/森科物料仓"）逐级解析/创建位置节点。
     * 叶子段若已有同名顶级节点（V20260820 种子），将其收养至正确父级而非重复建行。
     */
    private Long resolveOrCreateLocation(String path, MigrationResult.PhaseResult phase) {
        String[] segments = path.split("/");
        Long parentId = null;
        String parentPath = "/";
        Location current = null;
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i].trim();
            if (segment.isEmpty()) {
                continue;
            }
            long parentKey = parentId == null ? 0L : parentId;
            current = locationChildren.getOrDefault(parentKey, Map.of()).get(segment);
            if (current == null && i == segments.length - 1) {
                // 叶子段收养：同名顶级种子节点归位（避免同名双节点）
                Location orphan = findTopLevelByName(segment);
                if (orphan != null) {
                    orphan.setParentId(parentId);
                    orphan.setPath(parentPath + orphan.getId() + "/");
                    locationMapper.update(null, new LambdaUpdateWrapper<Location>()
                            .eq(Location::getId, orphan.getId())
                            .set(Location::getParentId, parentId)
                            .set(Location::getPath, orphan.getPath()));
                    current = orphan;
                    phase.updated();
                }
            }
            if (current == null) {
                Location loc = new Location();
                loc.setName(segment);
                loc.setParentId(parentId);
                locationMapper.insert(loc);
                loc.setPath(parentPath + loc.getId() + "/");
                locationMapper.update(null, new LambdaUpdateWrapper<Location>()
                        .eq(Location::getId, loc.getId())
                        .set(Location::getPath, loc.getPath()));
                locationsById.put(loc.getId(), loc);
                locationChildren.computeIfAbsent(parentKey, k -> new HashMap<>()).put(segment, loc);
                phase.inserted();
                current = loc;
            }
            parentId = current.getId();
            parentPath = current.getPath();
        }
        return current == null ? null : current.getId();
    }

    private Location findTopLevelByName(String name) {
        return locationChildren.getOrDefault(0L, Map.of()).get(name);
    }

    private void refreshLocationNameIndex() {
        locationIdByName.clear();
        locationsById.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .forEach(l -> locationIdByName.putIfAbsent(l.getName(), l.getId()));
    }

    // =====================================================================
    // 资产主数据（563 条，按 barcode upsert；在用持有关系补建）
    // =====================================================================

    private void importAssets(List<Map<Integer, String>> rows, MigrationResult result) {
        MigrationResult.PhaseResult phase = new MigrationResult.PhaseResult("资产主数据");
        result.getPhases().add(phase);
        phase.setRows(rows.size());

        Map<String, Long> assetIdByBarcode = assetMapper.selectList(null).stream()
                .collect(Collectors.toMap(Asset::getBarcode, Asset::getId, (a, b) -> a));
        Set<Long> activeAllocationAssetIds = allocationMapper.selectList(
                        new LambdaQueryWrapper<AssetAllocation>().isNull(AssetAllocation::getReturnedAt))
                .stream().map(AssetAllocation::getAssetId).collect(Collectors.toSet());

        for (Map<Integer, String> row : rows) {
            String barcode = cell(row, AC_BARCODE);
            if (barcode.isEmpty()) {
                phase.skipped();
                result.addWarning("资产行缺少编码，跳过：" + cell(row, AC_NAME));
                continue;
            }
            String status = mapAssetStatus(cell(row, AC_STATUS), barcode, result);
            String userName = cell(row, AC_USER);
            String userDept = cell(row, AC_USER_DEPT);
            // 报废/闲置清使用人（对齐新系统不变量：报废联动清持有人、归还清使用人）
            boolean keepUser = "IN_USE".equals(status) || "PENDING_CONFIRM".equals(status);
            Long userId = keepUser && !userName.isEmpty()
                    ? directory.resolveByName(userName, userDept) : null;
            Long adminId = cell(row, AC_ADMIN).isEmpty() ? null
                    : directory.resolveByName(cell(row, AC_ADMIN), null);
            Long companyId = companyIdByName.get(cell(row, AC_OWNER_COMPANY));
            if (companyId == null) {
                result.addWarning("资产 " + barcode + " 归属公司未匹配：「" + cell(row, AC_OWNER_COMPANY) + "」");
            }
            String areaPath = cell(row, AC_AREA_PATH);
            Long locationId = locationIdByPath.get(areaPath);
            if (locationId == null) {
                result.addWarning("资产 " + barcode + " 区域路径未匹配：「" + areaPath + "」");
            }
            String specs = cell(row, AC_SPECS);
            String remark = specs.isEmpty() ? null : "规格：" + specs;

            Asset asset = new Asset();
            asset.setBarcode(barcode);
            asset.setName(cell(row, AC_NAME));
            asset.setSn(cell(row, AC_SN).isEmpty() ? null : cell(row, AC_SN));
            asset.setStatus(status);
            asset.setCategoryId(categoryIdByName.get(cell(row, AC_CATEGORY)));
            asset.setLocationId(locationId);
            asset.setLocationDetail(cell(row, AC_LOCATION_DETAIL).isEmpty() ? null : cell(row, AC_LOCATION_DETAIL));
            asset.setUserId(userId);
            asset.setUserDepartment(keepUser && !userDept.isEmpty() ? userDept : null);
            asset.setAdminUserId(adminId);
            asset.setCompanyId(companyId);
            asset.setPurchaseDate(parseDate(cell(row, AC_PURCHASED_DATE)));
            asset.setAmount(parseAmount(cell(row, AC_AMOUNT)));
            asset.setRemark(remark);
            asset.setSupplierId(cell(row, AC_VENDOR).isEmpty() ? null : supplierIdByName.get(cell(row, AC_VENDOR)));
            LocalDateTime createdAt = parseDateTime(cell(row, AC_CREATED_DATE));

            Long existingId = assetIdByBarcode.get(barcode);
            Long assetId;
            if (existingId == null) {
                asset.setCreatedAt(createdAt);
                assetMapper.insert(asset);
                assetId = asset.getId();
                assetIdByBarcode.put(barcode, assetId);
                phase.inserted();
            } else {
                assetId = existingId;
                assetMapper.update(null, new LambdaUpdateWrapper<Asset>()
                        .eq(Asset::getId, assetId)
                        .set(Asset::getName, asset.getName())
                        .set(Asset::getSn, asset.getSn())
                        .set(Asset::getStatus, asset.getStatus())
                        .set(Asset::getCategoryId, asset.getCategoryId())
                        .set(Asset::getModelId, null)
                        .set(Asset::getSupplierId, asset.getSupplierId())
                        .set(Asset::getLocationId, asset.getLocationId())
                        .set(Asset::getHomeLocationId, null)
                        .set(Asset::getLocationDetail, asset.getLocationDetail())
                        .set(Asset::getUserId, asset.getUserId())
                        .set(Asset::getUserDepartment, asset.getUserDepartment())
                        .set(Asset::getAdminUserId, asset.getAdminUserId())
                        .set(Asset::getCompanyId, asset.getCompanyId())
                        .set(Asset::getPurchaseDate, asset.getPurchaseDate())
                        .set(Asset::getAmount, asset.getAmount())
                        .set(Asset::getRemark, asset.getRemark())
                        .set(Asset::getCreatedAt, createdAt));
                phase.updated();
            }

            // 在用持有关系补建（M04 语义：IN_USE = 有人持有；已存在持有中则跳过）
            if ("IN_USE".equals(status) && userId != null
                    && !activeAllocationAssetIds.contains(assetId)) {
                AssetAllocation allocation = new AssetAllocation();
                allocation.setAssetId(assetId);
                allocation.setUserId(userId);
                allocation.setUserName(userName);
                allocation.setType("RECEIVE");
                allocation.setDepartment(userDept.isEmpty() ? null : userDept);
                allocation.setAllocatedAt(createdAt != null ? createdAt : LocalDateTime.now());
                allocation.setNote("M08 历史数据迁移补建（原系统在用持有）");
                allocation.setCompanyId(companyId);
                allocationMapper.insert(allocation);
                activeAllocationAssetIds.add(assetId);
            }

            // 迁移标记日志（每台资产至少一条记录，标注来源；随日志幂等清除线一并重灌）
            writeMarkerLog(assetId, barcode, createdAt);
        }
    }

    private void writeMarkerLog(Long assetId, String barcode, LocalDateTime createdAt) {
        AssetLog marker = new AssetLog();
        marker.setAssetId(assetId);
        marker.setOperationType("迁移导入");
        marker.setOperatorLabel("M08 历史数据迁移");
        marker.setContent("历史数据迁移导入（原系统资产档案，编码 " + barcode + "）");
        marker.setCreatedAt(createdAt != null ? createdAt : LocalDateTime.now());
        assetLogMapper.insert(marker);
    }

    // =====================================================================
    // 领用单（162 条，按 serial_no upsert；导出无资产明细，仅迁主表）
    // =====================================================================

    private void importReceipts(List<Map<Integer, String>> rows, MigrationResult result) {
        MigrationResult.PhaseResult phase = new MigrationResult.PhaseResult("领用单（ARE）");
        result.getPhases().add(phase);
        phase.setRows(rows.size());

        Map<String, Long> idBySerial = receiptMapper.selectList(null).stream()
                .collect(Collectors.toMap(ReceiveReceipt::getSerialNo, ReceiveReceipt::getId, (a, b) -> a));
        Long companyId = companyIdByName.get(COMPANY_SK);

        for (Map<Integer, String> row : rows) {
            String serial = cell(row, RC_SERIAL);
            if (serial.isEmpty()) {
                phase.skipped();
                continue;
            }
            String status = mapReceiptStatus(cell(row, RC_STATUS), serial, result);
            if (status == null) {
                phase.skipped();
                continue;
            }
            LocalDate receiveDate = parseDate(cell(row, RC_DATE));
            String applicantName = cell(row, RC_USER);
            Long applicantId = applicantName.isEmpty() ? null
                    : directory.resolveByName(applicantName, cell(row, RC_DEPT));
            String processor = cell(row, RC_PROCESSOR);
            Long processorId = processor.isEmpty() ? null : directory.resolveByName(processor, null);
            String area = cell(row, RC_AREA);
            Long locationId = area.isEmpty() ? null : locationIdByName.get(area);
            if (!area.isEmpty() && locationId == null) {
                result.addWarning("领用单 " + serial + " 领用区域未匹配：「" + area + "」（locationId 置空）");
            }

            ReceiveReceipt r = new ReceiveReceipt();
            r.setSerialNo(serial);
            r.setType("RECEIVE");
            r.setStatus(status);
            r.setApplicantUserId(applicantId);
            r.setApplicantName(applicantName.isEmpty() ? null : applicantName);
            r.setDepartment(cell(row, RC_DEPT).isEmpty() ? null : cell(row, RC_DEPT));
            r.setLocationId(locationId);
            r.setReason(cell(row, RC_REMARKS).isEmpty() ? null : cell(row, RC_REMARKS));
            if (!"PENDING".equals(status)) {
                r.setApproverUserId(processorId);
                r.setApproverName(processor.isEmpty() ? null : processor);
                r.setApproveTime(receiveDate != null ? receiveDate.atStartOfDay() : null);
            }
            r.setCompanyId(companyId);
            r.setCreatedAt(receiveDate != null ? receiveDate.atStartOfDay() : null);

            Long existingId = idBySerial.get(serial);
            if (existingId == null) {
                receiptMapper.insert(r);
                idBySerial.put(serial, r.getId());
                phase.inserted();
            } else {
                receiptMapper.update(null, new LambdaUpdateWrapper<ReceiveReceipt>()
                        .eq(ReceiveReceipt::getId, existingId)
                        .set(ReceiveReceipt::getType, r.getType())
                        .set(ReceiveReceipt::getStatus, r.getStatus())
                        .set(ReceiveReceipt::getApplicantUserId, r.getApplicantUserId())
                        .set(ReceiveReceipt::getApplicantName, r.getApplicantName())
                        .set(ReceiveReceipt::getDepartment, r.getDepartment())
                        .set(ReceiveReceipt::getLocationId, r.getLocationId())
                        .set(ReceiveReceipt::getReason, r.getReason())
                        .set(ReceiveReceipt::getApproverUserId, r.getApproverUserId())
                        .set(ReceiveReceipt::getApproverName, r.getApproverName())
                        .set(ReceiveReceipt::getApproveTime, r.getApproveTime())
                        .set(ReceiveReceipt::getApproveRemark, null)
                        .set(ReceiveReceipt::getCompanyId, r.getCompanyId())
                        .set(ReceiveReceipt::getCreatedAt, r.getCreatedAt()));
                phase.updated();
            }
        }
    }

    // =====================================================================
    // 调拨单（56 条，按 serial_no upsert；导出无资产明细，仅迁主表）
    // =====================================================================

    private void importTransfers(List<Map<Integer, String>> rows, MigrationResult result) {
        MigrationResult.PhaseResult phase = new MigrationResult.PhaseResult("调拨单（ATR）");
        result.getPhases().add(phase);
        phase.setRows(rows.size());

        Map<String, Long> idBySerial = transferMapper.selectList(null).stream()
                .collect(Collectors.toMap(TransferOrder::getSerialNo, TransferOrder::getId, (a, b) -> a));
        Long companyId = companyIdByName.get(COMPANY_SK);

        for (Map<Integer, String> row : rows) {
            String serial = cell(row, TR_NO);
            if (serial.isEmpty()) {
                phase.skipped();
                continue;
            }
            String status = mapTransferStatus(cell(row, TR_STATUS), serial, result);
            if (status == null) {
                phase.skipped();
                continue;
            }
            String outAdmin = cell(row, TR_OUT_ADMIN);
            String inAdmin = cell(row, TR_IN_ADMIN);
            String remarks = cell(row, TR_REMARKS);
            LocalDate outDate = parseDate(cell(row, TR_OUT_DATE));
            LocalDate inDate = parseDate(cell(row, TR_IN_DATE));

            String applicantName = OperatorTextParser.extractName(outAdmin);
            if (applicantName == null) {
                applicantName = outAdmin;
            }
            Long applicantId = directory.resolveByOperatorLabel(outAdmin);
            String confirmerName = OperatorTextParser.extractName(inAdmin);
            Long confirmerId = inAdmin.isEmpty() ? null : directory.resolveByOperatorLabel(inAdmin);
            boolean inventoryTriggered = outAdmin.contains("+盘点") || inAdmin.contains("+盘点");

            TransferOrder t = new TransferOrder();
            t.setSerialNo(serial);
            t.setStatus(status);
            t.setSource(inventoryTriggered ? "INVENTORY_TRIGGERED" : "MANUAL");
            t.setApplicantUserId(applicantId);
            t.setApplicantName(applicantName);
            t.setFromUserId(applicantId);
            t.setFromUserName(applicantName);
            t.setToUserId(confirmerId);
            t.setToUserName(confirmerName == null || confirmerName.isEmpty() ? null : confirmerName);
            t.setToDepartment(cell(row, TR_IN_DEPT).isEmpty() ? null : cell(row, TR_IN_DEPT));
            t.setReason(remarks.isEmpty() ? null : remarks);
            if (!"CANCELLED".equals(status)) {
                t.setConfirmerUserId(confirmerId);
                t.setConfirmerName(confirmerName == null || confirmerName.isEmpty() ? null : confirmerName);
                t.setConfirmTime(inDate != null ? inDate.atStartOfDay() : null);
            }
            if ("REJECTED".equals(status)) {
                t.setRejectReason(remarks.isEmpty() ? null : remarks);
            }
            t.setCompanyId(companyId);
            t.setCreatedAt(outDate != null ? outDate.atStartOfDay() : null);

            Long existingId = idBySerial.get(serial);
            if (existingId == null) {
                transferMapper.insert(t);
                idBySerial.put(serial, t.getId());
                phase.inserted();
            } else {
                transferMapper.update(null, new LambdaUpdateWrapper<TransferOrder>()
                        .eq(TransferOrder::getId, existingId)
                        .set(TransferOrder::getStatus, t.getStatus())
                        .set(TransferOrder::getSource, t.getSource())
                        .set(TransferOrder::getApplicantUserId, t.getApplicantUserId())
                        .set(TransferOrder::getApplicantName, t.getApplicantName())
                        .set(TransferOrder::getFromLocationId, null)
                        .set(TransferOrder::getFromUserId, t.getFromUserId())
                        .set(TransferOrder::getFromUserName, t.getFromUserName())
                        .set(TransferOrder::getToLocationId, null)
                        .set(TransferOrder::getToDepartment, t.getToDepartment())
                        .set(TransferOrder::getToUserId, t.getToUserId())
                        .set(TransferOrder::getToUserName, t.getToUserName())
                        .set(TransferOrder::getReason, t.getReason())
                        .set(TransferOrder::getConfirmerUserId, t.getConfirmerUserId())
                        .set(TransferOrder::getConfirmerName, t.getConfirmerName())
                        .set(TransferOrder::getConfirmTime, t.getConfirmTime())
                        .set(TransferOrder::getRejectReason, t.getRejectReason())
                        .set(TransferOrder::getCompanyId, t.getCompanyId())
                        .set(TransferOrder::getCreatedAt, t.getCreatedAt()));
                phase.updated();
            }
        }
    }

    // =====================================================================
    // 操作日志（1773 条，历史时间戳原样落库 + content 解析 diff_json）
    // =====================================================================

    private void importLogs(List<Map<Integer, String>> rows, MigrationResult result) {
        MigrationResult.PhaseResult phase = new MigrationResult.PhaseResult("操作日志");
        result.getPhases().add(phase);
        phase.setRows(rows.size());

        Map<String, Long> assetIdByBarcode = assetMapper.selectList(null).stream()
                .collect(Collectors.toMap(Asset::getBarcode, Asset::getId, (a, b) -> a));

        for (Map<Integer, String> row : rows) {
            String barcode = cell(row, LG_BARCODE);
            Long assetId = assetIdByBarcode.get(barcode);
            if (assetId == null) {
                phase.skipped();
                result.addWarning("日志资产编码未匹配，跳过：「" + barcode + "」");
                continue;
            }
            AssetLog log = new AssetLog();
            log.setAssetId(assetId);
            String opType = cell(row, LG_OP_TYPE);
            log.setOperationType(opType.isEmpty() ? "未知" : opType);
            String operator = cell(row, LG_OPERATOR);
            if (!operator.isEmpty()) {
                log.setOperatorUserId(directory.resolveByOperatorLabel(operator));
                log.setOperatorLabel(operator);
            }
            String content = cell(row, LG_CONTENT);
            log.setContent(content.isEmpty() ? "（无内容）" : content);
            log.setDiffJson(LogContentParser.parseDiffJson(content));
            LocalDate time = parseDate(cell(row, LG_TIME));
            log.setCreatedAt(time != null ? time.atStartOfDay() : LocalDateTime.now());
            assetLogMapper.insert(log);
            phase.inserted();
        }
    }

    // =====================================================================
    // 通用工具
    // =====================================================================

    private Connection openAuthDbConnection() throws Exception {
        if (props.getAuthDbUrl() == null || props.getAuthDbUrl().isBlank()) {
            throw new BusinessException(500, "app.migration.auth-db-url 未配置（comm_public_basic 库连接）");
        }
        return DriverManager.getConnection(props.getAuthDbUrl(),
                props.getAuthDbUsername(), props.getAuthDbPassword());
    }

    /** 读 Excel 全部行（含表头，调用方按 skipHeaderRows 跳过）；空行过滤 */
    @SuppressWarnings("unchecked")
    private List<Map<Integer, String>> readRows(Path file, int skipHeaderRows) {
        if (!Files.exists(file)) {
            throw new BusinessException(400, "迁移源文件不存在：" + file);
        }
        List<Map<Integer, String>> raw = EasyExcel.read(file.toString())
                .sheet(0).headRowNumber(0).doReadSync();
        return raw.stream()
                .skip(skipHeaderRows)
                .filter(row -> row.values().stream()
                        .anyMatch(v -> v != null && !v.toString().isBlank()))
                .map(row -> {
                    Map<Integer, String> mapped = new LinkedHashMap<>();
                    row.forEach((k, v) -> mapped.put(k, v == null ? "" : String.valueOf(v).trim()));
                    return mapped;
                })
                .collect(Collectors.toList());
    }

    private List<String> distinct(List<Map<Integer, String>> rows, int col) {
        return rows.stream()
                .map(r -> cell(r, col))
                .filter(v -> !v.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private static String cell(Map<Integer, String> row, int idx) {
        String v = row.get(idx);
        return v == null ? "" : v.trim();
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-M-d"));
    }

    private static LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        if (s.contains(":")) {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-M-d H:m"));
        }
        LocalDate d = parseDate(s);
        return d == null ? null : d.atStartOfDay();
    }

    private static BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String mapAssetStatus(String raw, String barcode, MigrationResult result) {
        return switch (raw) {
            case "Idle" -> "IDLE";
            case "In Use" -> "IN_USE";
            case "Discard" -> "DISCARD";
            case "Receive Pending Confirm" -> "PENDING_CONFIRM";
            default -> {
                result.addWarning("资产 " + barcode + " 未知状态「" + raw + "」，按 IDLE 处理");
                yield "IDLE";
            }
        };
    }

    static String mapReceiptStatus(String raw, String serial, MigrationResult result) {
        return switch (raw) {
            case "Approved" -> "APPROVED";
            case "Pending Approval" -> "PENDING";
            case "Rejected" -> "REJECTED";
            default -> {
                result.addWarning("领用单 " + serial + " 未知状态「" + raw + "」，跳过");
                yield null;
            }
        };
    }

    static String mapTransferStatus(String raw, String serial, MigrationResult result) {
        return switch (raw) {
            case "Completed" -> "COMPLETED";
            case "Allocated and Rejected" -> "REJECTED";
            case "Cancelled" -> "CANCELLED";
            default -> {
                result.addWarning("调拨单 " + serial + " 未知状态「" + raw + "」，跳过");
                yield null;
            }
        };
    }
}
