package com.sk.asset.controller.basedata;

import com.sk.asset.common.Result;
import com.sk.asset.dto.basedata.manufacturer.ManufacturerReq;
import com.sk.asset.dto.basedata.manufacturer.ManufacturerResp;
import com.sk.asset.entity.basedata.Manufacturer;
import com.sk.asset.service.basedata.ManufacturerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 厂商管理
 */
@Tag(name = "厂商管理")
@RestController
@RequestMapping("/api/v1/manufacturers")
public class ManufacturerController {

    @Autowired
    private ManufacturerService manufacturerService;

    @Operation(summary = "厂商列表")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<ManufacturerResp>> list() {
        List<Manufacturer> list = manufacturerService.list();
        List<ManufacturerResp> respList = list.stream()
            .map(ManufacturerResp::from)
            .collect(Collectors.toList());
        return Result.ok(respList);
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

    @Operation(summary = "删除厂商")
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        manufacturerService.deleteById(id);
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
