package com.sk.asset.controller.basedata;

import com.sk.asset.common.Result;
import com.sk.asset.dto.basedata.company.CompanyResp;
import com.sk.asset.entity.basedata.Company;
import com.sk.asset.service.basedata.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 公司主体管理（只读）
 */
@Tag(name = "公司主体管理")
@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @Operation(summary = "公司列表")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public Result<List<CompanyResp>> list() {
        List<Company> companies = companyService.list();
        List<CompanyResp> respList = companies.stream()
            .map(CompanyResp::from)
            .collect(Collectors.toList());
        return Result.ok(respList);
    }

    @Operation(summary = "公司详情")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public Result<CompanyResp> getById(@PathVariable Long id) {
        Company company = companyService.getById(id);
        if (company == null) {
            return Result.fail(404, "公司不存在");
        }
        return Result.ok(CompanyResp.from(company));
    }
}
