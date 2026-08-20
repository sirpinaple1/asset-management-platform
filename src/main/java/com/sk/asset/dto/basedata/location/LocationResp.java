package com.sk.asset.dto.basedata.location;

import com.sk.asset.entity.basedata.Location;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 区域位置响应 DTO
 */
@Data
public class LocationResp {

    private Long id;
    private String name;
    private String code;
    private Long parentId;
    private String path;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LocationResp from(Location entity) {
        LocationResp resp = new LocationResp();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setCode(entity.getCode());
        resp.setParentId(entity.getParentId());
        resp.setPath(entity.getPath());
        resp.setSortOrder(entity.getSortOrder());
        resp.setRemark(entity.getRemark());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
