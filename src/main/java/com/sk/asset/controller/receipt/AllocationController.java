package com.sk.asset.controller.receipt;

import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.common.Result;
import com.sk.asset.dto.receipt.AllocationQuery;
import com.sk.asset.dto.receipt.AllocationResp;
import com.sk.asset.dto.receipt.AllocationReturnReq;
import com.sk.asset.enums.receipt.ReceiptType;
import com.sk.asset.service.receipt.AllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 资产持有关系（M04）：审批通过发放时写入；归还（领用退库/借用归还共用）经 /return 闭环。
 * 列表端点为前端"退库/归还"操作提供 allocation id 查询渠道。
 */
@Tag(name = "资产持有关系")
@RestController
@RequestMapping("/api/v1/allocations")
@RequiredArgsConstructor
public class AllocationController {

    private final AllocationService allocationService;

    @Operation(summary = "持有关系列表（支持 assetId/userId/type/active 筛选，含资产名称）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<AllocationResp>> list(
            @Parameter(description = "资产 ID")
            @RequestParam(required = false) Long assetId,
            @Parameter(description = "持有人 ID")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "持有性质：RECEIVE-领用 BORROW-借用")
            @RequestParam(required = false) String type,
            @Parameter(description = "true-持有中 false-已归还 不传-全部")
            @RequestParam(required = false) Boolean active) {

        AllocationQuery query = new AllocationQuery();
        query.setAssetId(assetId);
        query.setUserId(userId);
        if (type != null && !type.isBlank()) {
            query.setType(ReceiptType.of(type.trim()));
        }
        query.setActive(active);
        List<AllocationResp> respList = allocationService.list(query).stream()
                .map(AllocationResp::from)
                .collect(Collectors.toList());
        return Result.ok(respList);
    }

    @Operation(summary = "归还（领用退库/借用归还共用：持有关系闭环，资产回闲置，写操作日志）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/return")
    public Result<Void> returnAllocation(@PathVariable Long id,
                                         @RequestBody(required = false) AllocationReturnReq req) {
        String note = req != null ? req.getNote() : null;
        AuthContext user = UserContext.require();
        allocationService.returnAllocation(id, note, Long.valueOf(user.getUserId()));
        return Result.ok();
    }
}
