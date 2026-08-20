package com.sk.asset.controller.basedata;

import com.sk.asset.common.Result;
import com.sk.asset.dto.basedata.location.LocationReq;
import com.sk.asset.dto.basedata.location.LocationResp;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.service.basedata.LocationService;
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
 * 区域位置管理
 */
@Tag(name = "区域位置管理")
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @Operation(summary = "位置列表（扁平，含 parentId + path）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<LocationResp>> list(
            @Parameter(description = "父节点 ID，查询子树时使用")
            @RequestParam(required = false) Long parentId) {

        List<Location> list;
        if (parentId != null) {
            list = locationService.listChildren(parentId);
        } else {
            list = locationService.list();
        }

        List<LocationResp> respList = list.stream()
                .map(LocationResp::from)
                .collect(Collectors.toList());
        return Result.ok(respList);
    }

    @Operation(summary = "位置详情")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public Result<LocationResp> getById(@PathVariable Long id) {
        Location location = locationService.getById(id);
        if (location == null) {
            return Result.fail(404, "位置不存在");
        }
        return Result.ok(LocationResp.from(location));
    }

    @Operation(summary = "新增位置")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<LocationResp> create(@RequestBody @Valid LocationReq req) {
        Location entity = req.toEntity();
        locationService.save(entity);
        return Result.ok(LocationResp.from(entity));
    }

    @Operation(summary = "更新位置")
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/{id}")
    public Result<LocationResp> update(@PathVariable Long id,
                                       @RequestBody @Valid LocationReq req) {
        Location entity = locationService.getById(id);
        if (entity == null) {
            return Result.fail(404, "位置不存在");
        }
        req.updateEntity(entity);
        locationService.updateById(entity);
        return Result.ok(LocationResp.from(entity));
    }

    @Operation(summary = "删除位置")
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        locationService.deleteById(id);
        return Result.ok();
    }
}
