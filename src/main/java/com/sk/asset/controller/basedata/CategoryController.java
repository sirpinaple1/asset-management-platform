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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 资产分类管理
 */
@Tag(name = "资产分类管理")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

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
}
