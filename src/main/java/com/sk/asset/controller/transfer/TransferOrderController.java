package com.sk.asset.controller.transfer;

import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.common.Result;
import com.sk.asset.dto.transfer.TransferApplyReq;
import com.sk.asset.dto.transfer.TransferQuery;
import com.sk.asset.dto.transfer.TransferRejectReq;
import com.sk.asset.dto.transfer.TransferResp;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.enums.transfer.TransferSource;
import com.sk.asset.enums.transfer.TransferStatus;
import com.sk.asset.service.transfer.TransferOrderService;
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
 * 调拨单（M05，ATR 单）。调出方发起 → 调入方确认/拒绝 → 发起方可撤销；
 * 确认时更新资产归属（位置/部门/负责人）并转移持有关系。
 */
@Tag(name = "调拨单")
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferOrderController {

    private final TransferOrderService transferService;

    @Operation(summary = "调拨单列表（支持 status/source/userId/assigneeUserId/unassigned/dept/date 筛选，含明细行与位置名称）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<TransferResp>> list(
            @Parameter(description = "状态：PENDING-待确认 COMPLETED-已完成 CANCELLED-已撤销 REJECTED-已拒绝")
            @RequestParam(required = false) String status,
            @Parameter(description = "来源：MANUAL-手动调拨 INVENTORY_TRIGGERED-盘点触发")
            @RequestParam(required = false) String source,
            @Parameter(description = "发起人 ID")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "指定处理人 ID（精确匹配）")
            @RequestParam(required = false) Long assigneeUserId,
            @Parameter(description = "true=仅共享池单据（未指定处理人）")
            @RequestParam(required = false) Boolean unassigned,
            @Parameter(description = "调入部门（模糊匹配）")
            @RequestParam(required = false) String dept,
            @Parameter(description = "申请日期（yyyy-MM-dd）")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        TransferQuery query = new TransferQuery();
        if (status != null && !status.isBlank()) {
            query.setStatus(TransferStatus.of(status.trim()));
        }
        if (source != null && !source.isBlank()) {
            query.setSource(TransferSource.of(source.trim()));
        }
        query.setUserId(userId);
        query.setAssigneeUserId(assigneeUserId);
        query.setUnassigned(unassigned);
        query.setDept(dept);
        query.setDate(date);
        List<TransferResp> respList = transferService.list(query).stream()
                .map(TransferResp::from)
                .collect(Collectors.toList());
        return Result.ok(respList);
    }

    @Operation(summary = "调拨单详情（含明细行与位置名称）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public Result<TransferResp> getById(@PathVariable Long id) {
        TransferOrder order = transferService.getById(id);
        if (order == null) {
            return Result.fail(404, "调拨单不存在");
        }
        return Result.ok(TransferResp.from(order));
    }

    @Operation(summary = "发起调拨（一次可调拨多台资产，发起人取当前登录用户；调入区域与调入部门至少一项）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<TransferResp> create(@RequestBody @Valid TransferApplyReq req) {
        AuthContext user = UserContext.require();
        TransferOrder created = transferService.create(req,
                Long.valueOf(user.getUserId()), displayName(user));
        return Result.ok(TransferResp.from(created));
    }

    @Operation(summary = "调入方确认收到（资产归属更新为调入方并写持有记录与调拨日志；不能由发起人自己确认）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/confirm")
    public Result<TransferResp> confirm(@PathVariable Long id) {
        AuthContext user = UserContext.require();
        TransferOrder updated = transferService.confirm(id,
                Long.valueOf(user.getUserId()), displayName(user));
        return Result.ok(TransferResp.from(updated));
    }

    @Operation(summary = "调入方拒绝接收（资产不变，记录拒绝原因；不能由发起人自己拒绝）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/reject")
    public Result<TransferResp> reject(@PathVariable Long id,
                                       @RequestBody @Valid TransferRejectReq req) {
        AuthContext user = UserContext.require();
        TransferOrder updated = transferService.reject(id, req.getReason(),
                Long.valueOf(user.getUserId()), displayName(user));
        return Result.ok(TransferResp.from(updated));
    }

    @Operation(summary = "撤销调拨（仅 PENDING 状态、仅发起人可撤，资产不变）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/cancel")
    public Result<TransferResp> cancel(@PathVariable Long id) {
        AuthContext user = UserContext.require();
        TransferOrder updated = transferService.cancel(id, Long.valueOf(user.getUserId()));
        return Result.ok(TransferResp.from(updated));
    }

    /** 展示姓名：优先 getAuth 返回的 name，缺失时回退登录账号 */
    private String displayName(AuthContext user) {
        String name = user.getUser().getName();
        return (name == null || name.isBlank()) ? user.getUsername() : name;
    }
}
