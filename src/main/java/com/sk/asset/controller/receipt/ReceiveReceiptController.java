package com.sk.asset.controller.receipt;

import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.common.Result;
import com.sk.asset.dto.receipt.ReceiptApplyReq;
import com.sk.asset.dto.receipt.ReceiptQuery;
import com.sk.asset.dto.receipt.ReceiptRejectReq;
import com.sk.asset.dto.receipt.ReceiptResp;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import com.sk.asset.enums.receipt.ReceiptStatus;
import com.sk.asset.enums.receipt.ReceiptType;
import com.sk.asset.service.receipt.ReceiveReceiptService;
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
 * 领用/借用单（M04）。领用（RECEIVE）与借用（BORROW）共用单据流，
 * 前端两菜单各自按 type 过滤同一接口；申请人/审批人取 UserContext。
 */
@Tag(name = "领用/借用单")
@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public class ReceiveReceiptController {

    private final ReceiveReceiptService receiptService;

    @Operation(summary = "单据列表（支持 type/status/userId/date 筛选，含明细行与资产名称）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<ReceiptResp>> list(
            @Parameter(description = "单据类型：RECEIVE-领用 BORROW-借用")
            @RequestParam(required = false) String type,
            @Parameter(description = "状态：PENDING-待审批 APPROVED-已批准 REJECTED-已拒绝")
            @RequestParam(required = false) String status,
            @Parameter(description = "申请人 ID")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "申请日期（yyyy-MM-dd）")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        ReceiptQuery query = new ReceiptQuery();
        if (type != null && !type.isBlank()) {
            query.setType(ReceiptType.of(type.trim()));
        }
        if (status != null && !status.isBlank()) {
            query.setStatus(ReceiptStatus.of(status.trim()));
        }
        query.setUserId(userId);
        query.setDate(date);
        List<ReceiptResp> respList = receiptService.list(query).stream()
                .map(ReceiptResp::from)
                .collect(Collectors.toList());
        return Result.ok(respList);
    }

    @Operation(summary = "单据详情（含明细行与资产名称）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public Result<ReceiptResp> getById(@PathVariable Long id) {
        ReceiveReceipt receipt = receiptService.getById(id);
        if (receipt == null) {
            return Result.fail(404, "单据不存在");
        }
        return Result.ok(ReceiptResp.from(receipt));
    }

    @Operation(summary = "发起领用/借用申请（一次可申请多台资产，申请人取当前登录用户）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<ReceiptResp> create(@RequestBody @Valid ReceiptApplyReq req) {
        AuthContext user = UserContext.require();
        ReceiveReceipt created = receiptService.create(req,
                Long.valueOf(user.getUserId()), displayName(user));
        return Result.ok(ReceiptResp.from(created));
    }

    @Operation(summary = "批准（审批人与申请人不能是同一人；资产转在用并写持有记录）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/approve")
    public Result<ReceiptResp> approve(@PathVariable Long id) {
        AuthContext user = UserContext.require();
        ReceiveReceipt updated = receiptService.approve(id,
                Long.valueOf(user.getUserId()), displayName(user));
        return Result.ok(ReceiptResp.from(updated));
    }

    @Operation(summary = "拒绝（资产回闲置）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/reject")
    public Result<ReceiptResp> reject(@PathVariable Long id,
                                      @RequestBody @Valid ReceiptRejectReq req) {
        AuthContext user = UserContext.require();
        ReceiveReceipt updated = receiptService.reject(id, req.getReason(),
                Long.valueOf(user.getUserId()), displayName(user));
        return Result.ok(ReceiptResp.from(updated));
    }

    /** 展示姓名：优先 getAuth 返回的 name，缺失时回退登录账号 */
    private String displayName(AuthContext user) {
        String name = user.getUser().getName();
        return (name == null || name.isBlank()) ? user.getUsername() : name;
    }
}
