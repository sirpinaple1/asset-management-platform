package com.sk.asset.dto.basedata.manufacturer;

import com.sk.asset.entity.basedata.Manufacturer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 厂商响应 DTO
 */
@Data
public class ManufacturerResp {

    private Long id;
    private String name;
    private String contact;
    private String phone;
    private String email;
    private String address;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ManufacturerResp from(Manufacturer entity) {
        ManufacturerResp resp = new ManufacturerResp();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setContact(entity.getContact());
        resp.setPhone(entity.getPhone());
        resp.setEmail(entity.getEmail());
        resp.setAddress(entity.getAddress());
        resp.setRemark(entity.getRemark());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
