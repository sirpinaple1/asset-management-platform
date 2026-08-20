package com.sk.asset.controller.basedata;

import com.sk.asset.common.Result;
import com.sk.asset.dto.basedata.model.AssetModelReq;
import com.sk.asset.dto.basedata.model.AssetModelResp;
import com.sk.asset.entity.basedata.AssetModel;
import com.sk.asset.service.basedata.AssetModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 资产型号管理
 */
@Tag(name = "资产型号管理")
@RestController
@RequestMapping("/api/v1/models")
@RequiredArgsConstructor
public class AssetModelController {

    private final AssetModelService assetModelService;

    @Operation(summary = "型号列表（含关联名称，支持按分类筛选）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<AssetModelResp>> list(
            @Parameter(description = "分类 ID，按分类筛选时使用")
            @RequestParam(required = false) Long categoryId) {

        List<AssetModel> list;
        if (categoryId != null) {
            list = assetModelService.listByCategoryId(categoryId);
        } else {
            list = assetModelService.list();
        }

        List<AssetModelResp> respList = list.stream()
                .map(AssetModelResp::from)
                .collect(Collectors.toList());
        return Result.ok(respList);
    }

    @Operation(summary = "型号详情")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public Result<AssetModelResp> getById(@PathVariable Long id) {
        AssetModel model = assetModelService.getById(id);
        if (model == null) {
            return Result.fail(404, "型号不存在");
        }
        return Result.ok(AssetModelResp.from(model));
    }

    @Operation(summary = "新增型号")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<AssetModelResp> create(@RequestBody @Valid AssetModelReq req) {
        AssetModel entity = req.toEntity();
        assetModelService.save(entity);
        return Result.ok(AssetModelResp.from(entity));
    }

    @Operation(summary = "更新型号")
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/{id}")
    public Result<AssetModelResp> update(@PathVariable Long id,
                                         @RequestBody @Valid AssetModelReq req) {
        AssetModel entity = assetModelService.getById(id);
        if (entity == null) {
            return Result.fail(404, "型号不存在");
        }
        req.updateEntity(entity);
        assetModelService.updateById(entity);
        return Result.ok(AssetModelResp.from(entity));
    }

    @Operation(summary = "删除型号")
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assetModelService.deleteById(id);
        return Result.ok();
    }
}
