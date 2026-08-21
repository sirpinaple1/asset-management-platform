package com.sk.asset.controller.asset;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sk.asset.auth.UserContext;
import com.sk.asset.common.PageResp;
import com.sk.asset.common.Result;
import com.sk.asset.dto.asset.AssetDiscardReq;
import com.sk.asset.dto.asset.AssetExportRow;
import com.sk.asset.dto.asset.AssetLogResp;
import com.sk.asset.dto.asset.AssetQuery;
import com.sk.asset.dto.asset.AssetReq;
import com.sk.asset.dto.asset.AssetResp;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.asset.AssetLog;
import com.sk.asset.enums.asset.AssetStatus;
import com.sk.asset.service.asset.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 资产管理（M03）。状态变更只暴露报废业务端点；
 * 领用/归还等流转由 M04 单据触发，不经本控制器。
 */
@Tag(name = "资产管理")
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @Operation(summary = "资产列表（分页，支持状态/分类/位置/公司/使用人/管理员/关键词筛选）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<PageResp<AssetResp>> page(
            @Parameter(description = "页码，从 1 开始")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 500")
            @RequestParam(defaultValue = "20") long size,
            @Parameter(description = "状态：IDLE-闲置 IN_USE-在用 PENDING_CONFIRM-待确认 DISCARD-报废")
            @RequestParam(required = false) String status,
            @Parameter(description = "分类 ID")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "当前位置 ID")
            @RequestParam(required = false) Long locationId,
            @Parameter(description = "归属公司 ID")
            @RequestParam(required = false) Long companyId,
            @Parameter(description = "使用人 ID")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "资产管理员 ID")
            @RequestParam(required = false) Long adminUserId,
            @Parameter(description = "关键词（资产编码/名称/序列号模糊匹配）")
            @RequestParam(required = false) String keyword) {

        AssetQuery query = buildQuery(status, categoryId, locationId, companyId, userId, adminUserId, keyword);
        IPage<Asset> result = assetService.page(Math.max(page, 1), Math.min(size, 500), query);
        List<AssetResp> respList = result.getRecords().stream()
                .map(AssetResp::from)
                .collect(Collectors.toList());
        return Result.ok(PageResp.of(result, respList));
    }

    @Operation(summary = "资产 Excel 导出（按当前筛选条件导出全部）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/export")
    public void export(
            @Parameter(description = "状态：IDLE-闲置 IN_USE-在用 PENDING_CONFIRM-待确认 DISCARD-报废")
            @RequestParam(required = false) String status,
            @Parameter(description = "分类 ID")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "当前位置 ID")
            @RequestParam(required = false) Long locationId,
            @Parameter(description = "归属公司 ID")
            @RequestParam(required = false) Long companyId,
            @Parameter(description = "使用人 ID")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "资产管理员 ID")
            @RequestParam(required = false) Long adminUserId,
            @Parameter(description = "关键词（资产编码/名称/序列号模糊匹配）")
            @RequestParam(required = false) String keyword,
            HttpServletResponse response) throws IOException {

        AssetQuery query = buildQuery(status, categoryId, locationId, companyId, userId, adminUserId, keyword);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("资产清单", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), AssetExportRow.class)
                .sheet("资产")
                .doWrite(AssetExportRow.fromList(assetService.listBy(query)));
    }

    @Operation(summary = "资产详情（含关联名称）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public Result<AssetResp> getById(@PathVariable Long id) {
        Asset asset = assetService.getById(id);
        if (asset == null) {
            return Result.fail(404, "资产不存在");
        }
        return Result.ok(AssetResp.from(asset));
    }

    @Operation(summary = "新增资产（编码由服务端自动生成：分类前缀-日期-序号；状态自动设为闲置）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<AssetResp> create(@RequestBody @Valid AssetReq req) {
        Asset entity = req.toEntity();
        Long operatorId = Long.valueOf(UserContext.require().getUserId());
        assetService.save(entity, operatorId);
        return Result.ok(AssetResp.from(assetService.getById(entity.getId())));
    }

    @Operation(summary = "编辑资产基础信息（不含状态变更）")
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/{id}")
    public Result<AssetResp> update(@PathVariable Long id,
                                    @RequestBody @Valid AssetReq req) {
        Asset existing = assetService.getById(id);
        if (existing == null) {
            return Result.fail(404, "资产不存在");
        }
        Asset entity = new Asset();
        entity.setId(id);
        req.updateEntity(entity);
        assetService.updateById(entity);
        return Result.ok(AssetResp.from(assetService.getById(id)));
    }

    @Operation(summary = "报废资产（闲置/在用 → 报废，写操作日志）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/{id}/discard")
    public Result<Void> discard(@PathVariable Long id,
                                @RequestBody(required = false) @Valid AssetDiscardReq req) {
        String reason = req != null ? req.getReason() : null;
        Long operatorId = Long.valueOf(UserContext.require().getUserId());
        assetService.discard(id, reason, operatorId);
        return Result.ok();
    }

    @Operation(summary = "资产操作日志（时间倒序）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}/logs")
    public Result<List<AssetLogResp>> logs(@PathVariable Long id) {
        List<AssetLog> logs = assetService.listLogs(id);
        List<AssetLogResp> respList = logs.stream()
                .map(AssetLogResp::from)
                .collect(Collectors.toList());
        return Result.ok(respList);
    }

    private AssetQuery buildQuery(String status, Long categoryId, Long locationId,
                                  Long companyId, Long userId, Long adminUserId, String keyword) {
        AssetQuery query = new AssetQuery();
        if (status != null && !status.isBlank()) {
            query.setStatus(AssetStatus.of(status.trim()));
        }
        query.setCategoryId(categoryId);
        query.setLocationId(locationId);
        query.setCompanyId(companyId);
        query.setUserId(userId);
        query.setAdminUserId(adminUserId);
        query.setKeyword(keyword);
        return query;
    }
}
