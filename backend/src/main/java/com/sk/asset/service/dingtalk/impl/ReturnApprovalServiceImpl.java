package com.sk.asset.service.dingtalk.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.dingtalk.ApprovalInstance;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.dingtalk.ApprovalInstanceMapper;
import com.sk.asset.dto.dingtalk.ReturnApprovalResp;
import com.sk.asset.service.dingtalk.ReturnApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 钉钉退还审批查询实现：映射记录 + 发起人反查（dd_user_id → 系统用户）+ 资产快照明细。
 */
@Service
@RequiredArgsConstructor
public class ReturnApprovalServiceImpl implements ReturnApprovalService {

    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final AssetMapper assetMapper;
    private final UserDirectory userDirectory;

    @Override
    public List<ReturnApprovalResp> list() {
        List<ApprovalInstance> records = approvalInstanceMapper.selectList(
                new LambdaQueryWrapper<ApprovalInstance>()
                        .eq(ApprovalInstance::getBizType, ApprovalInstance.BIZ_RETURN)
                        .orderByDesc(ApprovalInstance::getId));
        if (records.isEmpty()) {
            return List.of();
        }

        // 发起人反查（同 userid 只查一次；未绑定内部账号时无映射，展示走钉钉用户兜底）
        Map<String, UserResp> usersByDdId = new java.util.HashMap<>();
        records.stream()
                .map(ApprovalInstance::getOriginatorDdUserId)
                .filter(Objects::nonNull).distinct()
                .forEach(id -> {
                    UserResp user = userDirectory.findByDdUserId(id);
                    if (user != null) {
                        usersByDdId.put(id, user);
                    }
                });

        // 资产快照批量取（跨记录合并一次查库）
        List<Long> assetIds = records.stream()
                .map(ApprovalInstance::getAssetIds)
                .filter(ids -> ids != null && !ids.isBlank())
                .flatMap(ids -> Arrays.stream(ids.split(",")))
                .map(Long::valueOf).distinct().toList();
        Map<Long, Asset> assets = assetIds.isEmpty() ? Map.of()
                : assetMapper.selectBatchIds(assetIds).stream()
                        .collect(Collectors.toMap(Asset::getId, Function.identity()));

        return records.stream().map(record -> {
            UserResp applicant = usersByDdId.get(record.getOriginatorDdUserId());
            List<ReturnApprovalResp.AssetBrief> briefs = new ArrayList<>();
            if (record.getAssetIds() != null && !record.getAssetIds().isBlank()) {
                for (String id : record.getAssetIds().split(",")) {
                    Asset asset = assets.get(Long.valueOf(id.trim()));
                    if (asset != null) {
                        briefs.add(ReturnApprovalResp.AssetBrief.from(asset));
                    }
                }
            }
            return ReturnApprovalResp.from(record,
                    applicant != null ? applicant.id() : null,
                    applicant != null ? applicant.name() : "钉钉用户",
                    briefs);
        }).toList();
    }
}
