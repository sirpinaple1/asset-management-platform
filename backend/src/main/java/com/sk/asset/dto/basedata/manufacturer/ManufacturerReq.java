package com.sk.asset.dto.basedata.manufacturer;

import com.sk.asset.entity.basedata.Manufacturer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 厂商请求 DTO
 */
@Data
public class ManufacturerReq {

    @NotBlank(message = "厂商名称不能为空")
    @Size(max = 200, message = "厂商名称长度不能超过 200")
    private String name;

    @Size(max = 100, message = "联系人长度不能超过 100")
    private String contact;

    @Size(max = 50, message = "联系电话长度不能超过 50")
    private String phone;

    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;

    @Size(max = 500, message = "地址长度不能超过 500")
    private String address;

    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态取值只能为 0 或 1")
    @Max(value = 1, message = "状态取值只能为 0 或 1")
    private Integer status;

    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    public Manufacturer toEntity() {
        Manufacturer entity = new Manufacturer();
        entity.setName(this.name);
        entity.setContact(this.contact);
        entity.setPhone(this.phone);
        entity.setEmail(this.email);
        entity.setAddress(this.address);
        entity.setStatus(this.status);
        entity.setRemark(this.remark);
        return entity;
    }

    public void updateEntity(Manufacturer entity) {
        entity.setName(this.name);
        entity.setContact(this.contact);
        entity.setPhone(this.phone);
        entity.setEmail(this.email);
        entity.setAddress(this.address);
        entity.setStatus(this.status);
        entity.setRemark(this.remark);
    }
}
