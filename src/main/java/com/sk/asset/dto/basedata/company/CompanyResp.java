package com.sk.asset.dto.basedata.company;

import com.sk.asset.entity.basedata.Company;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公司响应 DTO
 */
@Data
public class CompanyResp {

    private Long id;
    private String code;
    private String name;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CompanyResp from(Company entity) {
        CompanyResp resp = new CompanyResp();
        resp.setId(entity.getId());
        resp.setCode(entity.getCode());
        resp.setName(entity.getName());
        resp.setRemark(entity.getRemark());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
