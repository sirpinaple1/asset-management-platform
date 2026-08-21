package com.sk.asset.controller.change;

import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.common.Result;
import com.sk.asset.dto.change.ChangeApplyReq;
import com.sk.asset.dto.change.ChangeQuery;
import com.sk.asset.dto.change.ChangeResp;
import com.sk.asset.entity.change.ChangeOrder;
import com.sk.asset.enums.change.ChangeStatus;
import com.sk.asset.service.change.ChangeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 实物信息变更单（M06，AOC 单）。发起变更（变更前/后快照入明细）→ 确认执行（更新资产归属）
 * → 发起方可撤销；变更不流转资产状态，使用人变化时同步持有关系。
 */
@Tag(name = "实物信息变更单")
@RestController
@RequestMapping("/api/v1/change-orders")
@RequiredArgsConstructor
public class ChangeOrderController {

    private final ChangeOrderService changeOrderService;

    @Operation(summary = "变更单列表（支持 status/userId/assetId/date 筛选，assetId 查某台资产的变更历史；含明细行变更前/后对比）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<ChangeResp>> list(
            @Parameter(description = "状态：PENDING-待确认 CONFIRMED-已执行 CANCELLED-已撤销")
            @RequestParam(required = false) String status,
            @Parameter(description = "发起人 ID")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "资产 ID（查该资产的变更历史）")
            @RequestParam(required = false) Long assetId,
            @Parameter(description = "申请日期（yyyy-MM-dd）")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        ChangeQuery query = new ChangeQuery();
        if (status != null && !status.isBlank()) {
            query.setStatus(ChangeStatus.of(status.trim()));
        }
        query.setUserId(userId);
        query.setAssetId(assetId);
        query.setDate(date);
        List<ChangeResp> respList = changeOrderService.list(query).stream()
                .map(ChangeResp::from)
                .collect(Collectors.toList());
        return Result.ok(respList);
    }

    @Operation(summary = "变更单详情（含明细行，每台资产的每个实际变更字段一行，展示变更前/后对比）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public Result<ChangeResp> getById(@PathVariable Long id) {
        ChangeOrder order = changeOrderService.getById(id);
        if (order == null) {
            return Result.fail(404, "变更单不存在");
        }
        return Result.ok(ChangeResp.from(order));
    }

    @Operation(summary = "发起变更（一次可变更多台资产，统一变更到一组新值；各字段 null=不变更，至少一项；发起人取当前登录用户）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<ChangeResp> create(@RequestBody @Valid ChangeApplyReq req) {
        AuthContext user = UserContext.require();
        ChangeOrder created = changeOrderService.create(req,
                Long.valueOf(user.getUserId()), displayName(user));
        return Result.ok(ChangeResp.from(created));
    }

    @Operation(summary = "确认执行（更新资产归属字段，使用人变化时同步持有关系并写变更日志；信息修正单据允许发起人自己确认）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/confirm")
    public Result<ChangeResp> confirm(@PathVariable Long id) {
        AuthContext user = UserContext.require();
        ChangeOrder updated = changeOrderService.confirm(id,
                Long.valueOf(user.getUserId()), displayName(user));
        return Result.ok(ChangeResp.from(updated));
    }

    @Operation(summary = "撤销变更（仅 PENDING 状态、仅发起人可撤，资产不变）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/cancel")
    public Result<ChangeResp> cancel(@PathVariable Long id) {
        AuthContext user = UserContext.require();
        ChangeOrder updated = changeOrderService.cancel(id, Long.valueOf(user.getUserId()));
        return Result.ok(ChangeResp.from(updated));
    }

    /** 展示姓名：优先 getAuth 返回的 name，缺失时回退登录账号 */
    private String displayName(AuthContext user) {
        String name = user.getUser().getName();
        return (name == null || name.isBlank()) ? user.getUsername() : name;
    }
}
