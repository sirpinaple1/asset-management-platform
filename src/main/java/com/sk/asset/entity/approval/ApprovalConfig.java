package com.sk.asset.entity.approval;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批链配置（approval_config，两级审批人路由表，仅 systemAdmin 可管理）。
 * approverUserName 为查询回填，非表列。
 */
@Data
@TableName("approval_config")
public class ApprovalConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置类型：DEPT_SUPERVISOR-部门主管 WAREHOUSE_KEEPER-领料仓管理员（对应 ApprovalConfigType 枚举） */
    private String configType;

    /** DEPT_SUPERVISOR=部门路径字符串；WAREHOUSE_KEEPER=位置id（字符串）；逻辑删除时置 NULL 释放唯一键 */
    private String configKey;

    /** 审批人ID（comm_public_basic sys_user） */
    private Long approverUserId;

    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 审批人姓名（列表/详情查询时经 UserDirectory 反查回填，展示用） */
    @TableField(exist = false)
    private String approverUserName;

    /** 配置键展示名（查询回填）：DEPT=部门路径原文；WAREHOUSE=位置名称 */
    @TableField(exist = false)
    private String configKeyLabel;
}
