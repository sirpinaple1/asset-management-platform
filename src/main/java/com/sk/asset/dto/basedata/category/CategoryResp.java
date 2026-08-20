package com.sk.asset.dto.basedata.category;

import com.sk.asset.entity.basedata.Category;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产分类响应 DTO
 */
@Data
public class CategoryResp {

    private Long id;
    private String name;
    private String code;
    private Long parentId;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CategoryResp from(Category entity) {
        CategoryResp resp = new CategoryResp();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setCode(entity.getCode());
        resp.setParentId(entity.getParentId());
        resp.setSortOrder(entity.getSortOrder());
        resp.setRemark(entity.getRemark());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
