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

    /** 状态：1-启用 0-停用（停用仅从下拉选择排除，数据保留） */
    private Integer status;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
