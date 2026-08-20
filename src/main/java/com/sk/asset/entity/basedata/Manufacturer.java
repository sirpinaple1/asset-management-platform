package com.sk.asset.entity.basedata;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 厂商表
 */
@Data
@TableName("manufacturer")
public class Manufacturer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String contact;
    private String phone;
    private String email;
    private String address;
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
