package com.sk.asset.controller.basedata;

import com.sk.asset.common.Result;
import com.sk.asset.dto.basedata.supplier.SupplierReq;
import com.sk.asset.dto.basedata.supplier.SupplierResp;
import com.sk.asset.entity.basedata.Supplier;
import com.sk.asset.service.basedata.SupplierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 供应商管理
 */
@Tag(name = "供应商管理")
@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {

    @Autowired
    private SupplierService supplierService;

    @Operation(summary = "供应商列表")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<SupplierResp>> list() {
        List<Supplier> list = supplierService.list();
        List<SupplierResp> respList = list.stream()
            .map(SupplierResp::from)
            .collect(Collectors.toList());
        return Result.ok(respList);
    }

    @Operation(summary = "供应商详情")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public Result<SupplierResp> getById(@PathVariable Long id) {
        Supplier supplier = supplierService.getById(id);
        if (supplier == null) {
            return Result.fail(404, "供应商不存在");
        }
        return Result.ok(SupplierResp.from(supplier));
    }

    @Operation(summary = "新增供应商")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<SupplierResp> create(@RequestBody @Valid SupplierReq req) {
        Supplier entity = req.toEntity();
        supplierService.save(entity);
        return Result.ok(SupplierResp.from(entity));
    }

    @Operation(summary = "更新供应商")
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/{id}")
    public Result<SupplierResp> update(@PathVariable Long id,
                                       @RequestBody @Valid SupplierReq req) {
        Supplier entity = supplierService.getById(id);
        if (entity == null) {
            return Result.fail(404, "供应商不存在");
        }
        req.updateEntity(entity);
        supplierService.updateById(entity);
        return Result.ok(SupplierResp.from(entity));
    }

    @Operation(summary = "删除供应商")
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.deleteById(id);
        return Result.ok();
    }
}
