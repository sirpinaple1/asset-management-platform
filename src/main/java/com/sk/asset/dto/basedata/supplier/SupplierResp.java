package com.sk.asset.dto.basedata.supplier;

import com.sk.asset.entity.basedata.Supplier;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商响应 DTO
 */
@Data
public class SupplierResp {

    private Long id;
    private String name;
    private String contact;
    private String phone;
    private String email;
    private String address;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SupplierResp from(Supplier entity) {
        SupplierResp resp = new SupplierResp();
        resp.setId(entity.getId());
        resp.setName(entity.getName());
        resp.setContact(entity.getContact());
        resp.setPhone(entity.getPhone());
        resp.setEmail(entity.getEmail());
        resp.setAddress(entity.getAddress());
        resp.setStatus(entity.getStatus());
        resp.setRemark(entity.getRemark());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }
}
