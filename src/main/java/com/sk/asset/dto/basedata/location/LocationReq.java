package com.sk.asset.dto.basedata.location;

import com.sk.asset.entity.basedata.Location;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 区域位置请求 DTO
 */
@Data
public class LocationReq {

    @NotBlank(message = "位置名称不能为空")
    @Size(max = 200, message = "位置名称长度不能超过 200")
    private String name;

    @Size(max = 50, message = "位置编码长度不能超过 50")
    private String code;

    private Long parentId;

    @Size(max = 500, message = "路径长度不能超过 500")
    private String path;

    private Integer sortOrder;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    public Location toEntity() {
        Location entity = new Location();
        entity.setName(this.name);
        entity.setCode(this.code);
        entity.setParentId(this.parentId);
        entity.setPath(this.path);
        entity.setSortOrder(this.sortOrder);
        entity.setRemark(this.remark);
        return entity;
    }

    public void updateEntity(Location entity) {
        entity.setName(this.name);
        entity.setCode(this.code);
        entity.setParentId(this.parentId);
        entity.setPath(this.path);
        entity.setSortOrder(this.sortOrder);
        entity.setRemark(this.remark);
    }
}
