package com.sk.asset.service.approval.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.common.BusinessException;
import com.sk.asset.entity.approval.ApprovalConfig;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.enums.approval.ApprovalConfigType;
import com.sk.asset.mapper.approval.ApprovalConfigMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.service.approval.ApprovalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批链配置服务实现。
 * 校验兜底：类型枚举合法、WAREHOUSE_KEEPER 键=存在的位置 id、审批人存在于 comm_public_basic、
 * (config_type, config_key) 唯一（预检 + DuplicateKeyException 兜底，逻辑删除行占用也覆盖）；
 * 删除时置空 config_key 释放唯一键（对齐 Category 删除释放 code 模式）。
 */
@Service
@RequiredArgsConstructor
public class ApprovalConfigServiceImpl implements ApprovalConfigService {

    private final ApprovalConfigMapper configMapper;
    private final LocationMapper locationMapper;
    private final UserDirectory userDirectory;

    @Override
    public IPage<ApprovalConfig> page(long page, long size, ApprovalConfigType type, String keyword) {
        LambdaQueryWrapper<ApprovalConfig> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            wrapper.eq(ApprovalConfig::getConfigType, type.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like(ApprovalConfig::getConfigKey, like)
                    .or().like(ApprovalConfig::getRemark, like));
        }
        wrapper.orderByDesc(ApprovalConfig::getId);
        IPage<ApprovalConfig> result = configMapper.selectPage(new Page<>(page, size), wrapper);
        fillDisplayFields(result.getRecords());
        return result;
    }

    @Override
    public ApprovalConfig getById(Long id) {
        ApprovalConfig config = configMapper.selectById(id);
        if (config != null) {
            fillDisplayFields(List.of(config));
        }
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ApprovalConfig config) {
        ApprovalConfigType type = normalizeAndValidate(config, null);
        validateKeyUnique(type, config.getConfigKey(), null);
        try {
            configMapper.insert(config);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, occupiedMessage(type, config.getConfigKey()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateById(ApprovalConfig config) {
        ApprovalConfig existing = configMapper.selectById(config.getId());
        if (existing == null) {
            throw new BusinessException(404, "审批链配置不存在（id=" + config.getId() + "）");
        }
        ApprovalConfigType type = normalizeAndValidate(config, existing);
        validateKeyUnique(type, config.getConfigKey(), config.getId());
        try {
            // 全字段显式 set：允许清空 remark（updateById 会跳过 null 列）
            configMapper.update(null, new LambdaUpdateWrapper<ApprovalConfig>()
                    .eq(ApprovalConfig::getId, config.getId())
                    .set(ApprovalConfig::getConfigType, type.name())
                    .set(ApprovalConfig::getConfigKey, config.getConfigKey())
                    .set(ApprovalConfig::getApproverUserId, config.getApproverUserId())
                    .set(ApprovalConfig::getRemark, config.getRemark()));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, occupiedMessage(type, config.getConfigKey()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        ApprovalConfig existing = configMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "审批链配置不存在（id=" + id + "）");
        }
        // 逻辑删除同时释放 config_key（uk 含已删行，不清会导致同键配置无法重建，对齐 Category 删除释放 code 模式）
        configMapper.update(null, new LambdaUpdateWrapper<ApprovalConfig>()
                .eq(ApprovalConfig::getId, id)
                .set(ApprovalConfig::getConfigKey, null)
                .set(ApprovalConfig::getDeleted, 1));
    }

    @Override
    public Long keeperUserIdOf(Long locationId) {
        if (locationId == null) {
            return null;
        }
        ApprovalConfig keeper = configMapper.selectOne(new LambdaQueryWrapper<ApprovalConfig>()
                .eq(ApprovalConfig::getConfigType, ApprovalConfigType.WAREHOUSE_KEEPER.name())
                .eq(ApprovalConfig::getConfigKey, String.valueOf(locationId)));
        return keeper != null ? keeper.getApproverUserId() : null;
    }

    /** 归一化 + 类型/键/审批人校验；@return 归一后的类型枚举 */
    private ApprovalConfigType normalizeAndValidate(ApprovalConfig config, ApprovalConfig existing) {
        ApprovalConfigType type;
        try {
            type = ApprovalConfigType.of(config.getConfigType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        }
        config.setConfigType(type.name());

        if (config.getConfigKey() == null || config.getConfigKey().isBlank()) {
            // 更新时请求键为空保持原值（前端空值不发送/发空串两种风格都兼容）
            if (existing != null && existing.getConfigKey() != null) {
                config.setConfigKey(existing.getConfigKey());
                config.setConfigType(existing.getConfigType());
                type = ApprovalConfigType.of(existing.getConfigType());
            } else {
                throw new BusinessException(400, "配置键不能为空");
            }
        } else {
            config.setConfigKey(config.getConfigKey().trim());
        }
        if (config.getRemark() != null && config.getRemark().isBlank()) {
            config.setRemark(null);
        }

        if (config.getApproverUserId() == null) {
            throw new BusinessException(400, "审批人不能为空");
        }
        if (!userDirectory.exists(config.getApproverUserId())) {
            throw new BusinessException(400, "审批人不存在（id=" + config.getApproverUserId() + "）");
        }

        // WAREHOUSE_KEEPER 的键必须是存在的位置 id（防手滑配错路由）
        if (type == ApprovalConfigType.WAREHOUSE_KEEPER) {
            Long locationId;
            try {
                locationId = Long.parseLong(config.getConfigKey());
            } catch (NumberFormatException e) {
                throw new BusinessException(400, "仓管配置键需为位置 id（收到：" + config.getConfigKey() + "）");
            }
            if (locationMapper.selectById(locationId) == null) {
                throw new BusinessException(400, "位置不存在（id=" + locationId + "）");
            }
        }
        return type;
    }

    /** 唯一约束预检（活跃行；逻辑删除行的占用由 DuplicateKeyException 兜底转 400） */
    private void validateKeyUnique(ApprovalConfigType type, String key, Long excludeId) {
        Long count = configMapper.selectCount(new LambdaQueryWrapper<ApprovalConfig>()
                .eq(ApprovalConfig::getConfigType, type.name())
                .eq(ApprovalConfig::getConfigKey, key)
                .ne(excludeId != null, ApprovalConfig::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(400, occupiedMessage(type, key));
        }
    }

    private static String occupiedMessage(ApprovalConfigType type, String key) {
        String target = type == ApprovalConfigType.DEPT_SUPERVISOR ? "部门〔" + key + "〕" : "位置（id=" + key + "）";
        return target + "已配置审批人（可能被已删除配置占用）";
    }

    /** 批量回填审批人姓名 + 配置键展示名（WAREHOUSE=位置名称） */
    private void fillDisplayFields(List<ApprovalConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        // 审批人姓名
        Set<Long> userIds = configs.stream()
                .map(ApprovalConfig::getApproverUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, com.sk.asset.dto.user.UserResp> users = userDirectory.namesByIds(userIds);
        // 位置名称（仅 WAREHOUSE_KEEPER）
        Set<Long> locationIds = configs.stream()
                .filter(c -> ApprovalConfigType.WAREHOUSE_KEEPER.name().equals(c.getConfigType()))
                .map(ApprovalConfig::getConfigKey)
                .filter(Objects::nonNull)
                .map(key -> {
                    try {
                        return Long.parseLong(key);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> locationNames = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectBatchIds(locationIds).stream()
                        .collect(Collectors.toMap(Location::getId, Location::getName));
        for (ApprovalConfig config : configs) {
            com.sk.asset.dto.user.UserResp user = users.get(config.getApproverUserId());
            config.setApproverUserName(user != null ? user.name() : null);
            if (ApprovalConfigType.WAREHOUSE_KEEPER.name().equals(config.getConfigType())
                    && config.getConfigKey() != null) {
                Long locationId = null;
                try {
                    locationId = Long.parseLong(config.getConfigKey());
                } catch (NumberFormatException ignored) {
                }
                config.setConfigKeyLabel(locationId != null ? locationNames.get(locationId) : null);
            } else {
                config.setConfigKeyLabel(config.getConfigKey());
            }
        }
    }
}
