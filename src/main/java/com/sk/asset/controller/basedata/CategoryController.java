package com.sk.asset.controller.basedata;

import com.sk.asset.common.Result;
import com.sk.asset.dto.basedata.category.CategoryReq;
import com.sk.asset.dto.basedata.category.CategoryResp;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.service.basedata.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 资产分类管理
 */
@Tag(name = "资产分类管理")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "分类列表（扁平，含 parentId）")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<CategoryResp>> list() {
        List<Category> list = categoryService.list();
        List<CategoryResp> respList = list.stream()
            .map(CategoryResp::from)
            .collect(Collectors.toList());
        return Result.ok(respList);
    }

    @Operation(summary = "分类详情")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public Result<CategoryResp> getById(@PathVariable Long id) {
        Category category = categoryService.getById(id);
        if (category == null) {
            return Result.fail(404, "分类不存在");
        }
        return Result.ok(CategoryResp.from(category));
    }

    @Operation(summary = "新增分类（父分类必须是一级，最多两级；同级重名/编码冲突返回 400）")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping
    public Result<CategoryResp> create(@RequestBody @Valid CategoryReq req) {
        Category entity = req.toEntity();
        categoryService.save(entity);
        return Result.ok(CategoryResp.from(categoryService.getById(entity.getId())));
    }

    @Operation(summary = "编辑分类（不能把自己设为父分类；带子分类的分类只能作为一级）")
    @SecurityRequirement(name = "BearerAuth")
    @PutMapping("/{id}")
    public Result<CategoryResp> update(@PathVariable Long id,
                                       @RequestBody @Valid CategoryReq req) {
        Category existing = categoryService.getById(id);
        if (existing == null) {
            return Result.fail(404, "分类不存在");
        }
        Category entity = new Category();
        entity.setId(id);
        req.updateEntity(entity);
        categoryService.updateById(entity);
        return Result.ok(CategoryResp.from(categoryService.getById(id)));
    }

    @Operation(summary = "删除分类（有子分类或被资产引用时返回 400）")
    @SecurityRequirement(name = "BearerAuth")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.deleteById(id);
        return Result.ok();
    }
}
