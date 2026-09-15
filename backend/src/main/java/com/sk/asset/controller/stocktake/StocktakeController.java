package com.sk.asset.controller.stocktake;

import com.sk.asset.auth.AuthContext;
import com.sk.asset.auth.UserContext;
import com.sk.asset.common.Result;
import com.sk.asset.dto.stocktake.StocktakeBarcodeScanReq;
import com.sk.asset.dto.stocktake.StocktakeCreateReq;
import com.sk.asset.dto.stocktake.StocktakeItemResp;
import com.sk.asset.dto.stocktake.StocktakeQuery;
import com.sk.asset.dto.stocktake.StocktakeReportResp;
import com.sk.asset.dto.stocktake.StocktakeResp;
import com.sk.asset.dto.stocktake.StocktakeScanReq;
import com.sk.asset.dto.transfer.TransferResp;
import com.sk.asset.entity.stocktake.Stocktake;
import com.sk.asset.entity.stocktake.StocktakeItem;
import com.sk.asset.entity.transfer.TransferOrder;
import com.sk.asset.enums.stocktake.StocktakeItemStatus;
import com.sk.asset.enums.stocktake.StocktakeStatus;
import com.sk.asset.service.stocktake.StocktakeService;
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
 * 盘点（M07）。创建（范围快照）→ 开始 → 扫码/人工确认（相符/位置不符/盘亏/盘盈）→
 * 完成（剩余待盘记盘亏）→ 报告汇总 → 位置不符批量触发 INVENTORY_TRIGGERED 调拨单。
 */
@Tag(name = "盘点")
@RestController
@RequestMapping("/api/v1/stocktakes")
@RequiredArgsConstructor
public class StocktakeController {

    private final StocktakeService stocktakeService;

    @Operation(summary = "创建盘点任务（范围：位置含子树/分类，均可空=全库；报废资产不参与，创建时快照明细）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<StocktakeResp> create(@RequestBody @Valid StocktakeCreateReq req) {
        AuthContext user = UserContext.require();
        Stocktake created = stocktakeService.create(req,
                Long.valueOf(user.getUserId()), displayName(user));
        return Result.ok(StocktakeResp.from(created));
    }

    @Operation(summary = "盘点任务列表（status/userId/date 筛选，含范围名称与统计计数）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<StocktakeResp>> list(
            @Parameter(description = "状态：PENDING-待开始 IN_PROGRESS-进行中 COMPLETED-已完成 CANCELLED-已取消")
            @RequestParam(required = false) String status,
            @Parameter(description = "创建人 ID")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "创建日期（yyyy-MM-dd）")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        StocktakeQuery query = new StocktakeQuery();
        if (status != null && !status.isBlank()) {
            query.setStatus(StocktakeStatus.of(status.trim()));
        }
        query.setUserId(userId);
        query.setDate(date);
        List<StocktakeResp> respList = stocktakeService.list(query).stream()
                .map(StocktakeResp::from)
                .collect(Collectors.toList());
        return Result.ok(respList);
    }

    @Operation(summary = "盘点任务详情（含明细行、范围名称与统计计数）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public Result<StocktakeResp> getById(@PathVariable Long id) {
        Stocktake task = stocktakeService.getById(id);
        if (task == null) {
            return Result.fail(404, "盘点任务不存在");
        }
        return Result.ok(StocktakeResp.from(task));
    }

    @Operation(summary = "开始盘点（PENDING → IN_PROGRESS）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/start")
    public Result<StocktakeResp> start(@PathVariable Long id) {
        AuthContext user = UserContext.require();
        return Result.ok(StocktakeResp.from(stocktakeService.start(id, Long.valueOf(user.getUserId()))));
    }

    @Operation(summary = "取消盘点（仅 PENDING/IN_PROGRESS 且创建人可操作，明细与资产不变）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/cancel")
    public Result<StocktakeResp> cancel(@PathVariable Long id) {
        AuthContext user = UserContext.require();
        return Result.ok(StocktakeResp.from(stocktakeService.cancel(id, Long.valueOf(user.getUserId()))));
    }

    @Operation(summary = "盘点明细列表（status 可选筛选，含资产与位置名称）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}/items")
    public Result<List<StocktakeItemResp>> listItems(
            @PathVariable Long id,
            @Parameter(description = "明细状态：PENDING-待盘 MATCHED-账实相符 LOCATION_MISMATCH-位置不符 NOT_FOUND-盘亏 EXTRA-盘盈")
            @RequestParam(required = false) String status) {

        StocktakeItemStatus itemStatus = null;
        if (status != null && !status.isBlank()) {
            itemStatus = StocktakeItemStatus.of(status.trim());
        }
        List<StocktakeItemResp> respList = stocktakeService.listItems(id, itemStatus).stream()
                .map(StocktakeItemResp::from)
                .collect(Collectors.toList());
        return Result.ok(respList);
    }

    @Operation(summary = "明细确认（扫码或人工：提交实际位置判定 相符/位置不符，或 notFound=true 标记盘亏；已盘不可重盘）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/items/{itemId}/scan")
    public Result<StocktakeItemResp> scanItem(@PathVariable Long id,
                                              @PathVariable Long itemId,
                                              @RequestBody @Valid StocktakeScanReq req) {
        AuthContext user = UserContext.require();
        StocktakeItem item = stocktakeService.scanItem(id, itemId, req, Long.valueOf(user.getUserId()));
        return Result.ok(StocktakeItemResp.from(item));
    }

    @Operation(summary = "按条码扫码（PDA 入口）：条码在任务明细中→更新该明细；不在明细但资产已登记→记盘盈；未登记→404")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/scan")
    public Result<StocktakeItemResp> scanByBarcode(@PathVariable Long id,
                                                   @RequestBody @Valid StocktakeBarcodeScanReq req) {
        AuthContext user = UserContext.require();
        StocktakeItem item = stocktakeService.scanByBarcode(id, req, Long.valueOf(user.getUserId()));
        return Result.ok(StocktakeItemResp.from(item));
    }

    @Operation(summary = "完成盘点（剩余待盘明细记盘亏并写资产日志，IN_PROGRESS → COMPLETED）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/complete")
    public Result<StocktakeResp> complete(@PathVariable Long id) {
        AuthContext user = UserContext.require();
        return Result.ok(StocktakeResp.from(
                stocktakeService.complete(id, Long.valueOf(user.getUserId()))));
    }

    @Operation(summary = "盘点报告（各状态汇总计数 + 位置不符/盘亏/盘盈差异明细；进行中可看实时统计）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}/report")
    public Result<StocktakeReportResp> report(@PathVariable Long id) {
        return Result.ok(stocktakeService.report(id));
    }

    @Operation(summary = "对位置不符明细批量生成调拨单（按实际位置分组，source=INVENTORY_TRIGGERED；任务须已完成且未生成过）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/transfer")
    public Result<List<TransferResp>> createTransfers(@PathVariable Long id) {
        AuthContext user = UserContext.require();
        List<TransferResp> respList = stocktakeService.createTransfers(id,
                        Long.valueOf(user.getUserId()), displayName(user)).stream()
                .map(TransferResp::from)
                .collect(Collectors.toList());
        return Result.ok(respList);
    }

    /** 展示姓名：优先 getAuth 返回的 name，缺失时回退登录账号 */
    private String displayName(AuthContext user) {
        String name = user.getUser().getName();
        return (name == null || name.isBlank()) ? user.getUsername() : name;
    }
}
