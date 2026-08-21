package com.sk.asset.controller.basedata;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sk.asset.common.PageResp;
import com.sk.asset.common.Result;
import com.sk.asset.dto.basedata.manufacturer.ManufacturerExportRow;
import com.sk.asset.dto.basedata.manufacturer.ManufacturerReq;
import com.sk.asset.dto.basedata.manufacturer.ManufacturerResp;
import com.sk.asset.entity.basedata.Manufacturer;
import com.sk.asset.service.basedata.ManufacturerService;
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
 * 厂商管理
 */
@Tag(name = "厂商管理")
@RestController
@RequestMapping("/api/v1/manufacturers")
@RequiredArgsConstructor
public class ManufacturerController {

    private final ManufacturerService manufacturerService;

    @Operation(summary = "厂商列表（分页，支持 keyword/status 筛选）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<PageResp<ManufacturerResp>> page(
            @Parameter(description = "页码，从 1 开始")
            @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数，上限 500")
            @RequestParam(defaultValue = "20") long size,
            @Parameter(description = "名称关键词（模糊匹配）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：1-启用 0-停用，缺省=全部")
            @RequestParam(required = false) Integer status) {

        IPage<Manufacturer> result = manufacturerService.page(Math.max(page, 1), Math.min(size, 500), keyword, status);
        List<ManufacturerResp> respList = result.getRecords().stream()
                .map(ManufacturerResp::from)
                .collect(Collectors.toList());
        return Result.ok(PageResp.of(result, respList));
    }

    @Operation(summary = "厂商 Excel 导出（按当前筛选条件导出全部）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/export")
    public void export(
            @Parameter(description = "名称关键词（模糊匹配）")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "状态：1-启用 0-停用，缺省=全部")
            @RequestParam(required = false) Integer status,
            HttpServletResponse response) throws IOException {

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("厂商清单", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        EasyExcel.write(response.getOutputStream(), ManufacturerExportRow.class)
                .sheet("厂商")
                .doWrite(ManufacturerExportRow.fromList(manufacturerService.listBy(keyword, status)));
    }

    @Operation(summary = "厂商详情")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public Result<ManufacturerResp> getById(@PathVariable Long id) {
        Manufacturer manufacturer = manufacturerService.getById(id);
        if (manufacturer == null) {
            return Result.fail(404, "厂商不存在");
        }
        return Result.ok(ManufacturerResp.from(manufacturer));
    }

    @Operation(summary = "新增厂商")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<ManufacturerResp> create(@RequestBody @Valid ManufacturerReq req) {
        Manufacturer entity = req.toEntity();
        manufacturerService.save(entity);
        return Result.ok(ManufacturerResp.from(entity));
    }

    @Operation(summary = "更新厂商")
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/{id}")
    public Result<ManufacturerResp> update(@PathVariable Long id,
                                           @RequestBody @Valid ManufacturerReq req) {
        Manufacturer entity = manufacturerService.getById(id);
        if (entity == null) {
            return Result.fail(404, "厂商不存在");
        }
        req.updateEntity(entity);
        manufacturerService.updateById(entity);
        return Result.ok(ManufacturerResp.from(entity));
    }

    @Operation(summary = "删除厂商（被资产型号引用时返回 409）")
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        manufacturerService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除厂商（任一被资产型号引用则整体拒绝 409）")
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping
    public Result<Void> deleteBatch(@Parameter(description = "厂商 ID 列表，逗号分隔") @RequestParam List<Long> ids) {
        manufacturerService.deleteByIds(ids);
        return Result.ok();
    }

    @Operation(summary = "恢复已删除的厂商（撤销删除）")
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        int rows = manufacturerService.restoreById(id);
        if (rows == 0) {
            return Result.fail(404, "厂商不存在或未被删除");
        }
        return Result.ok();
    }
}
