package com.sk.asset.entity.change;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实物信息变更单主表（M06，AOC 单）。一单 = N 台资产 + 一组统一新值。
 * new_* 列为变更后目标值（null = 不变更）；位置/公司名称与明细行仅用于查询回填，非表列。
 */
@Data
@TableName("change_order")
public class ChangeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 单号：AOC + yyyyMMdd + 4位序号 */
    private String serialNo;

    /** 状态：PENDING-待确认 CONFIRMED-已执行 CANCELLED-已撤销（对应 ChangeStatus 枚举） */
    private String status;

    /** 发起人ID（comm_public_basic 用户） */
    private Long applicantUserId;

    /** 发起人姓名（提交时快照） */
    private String applicantName;

    /** 指定处理人ID（comm_public_basic 用户，NULL=共享池：任何人可确认；B1 定向待办） */
    private Long assigneeUserId;

    /** 变更原因 */
    private String reason;

    /** 变更后使用人ID（null = 不变更） */
    private Long newUserId;

    /** 变更后使用人姓名（发起时快照） */
    private String newUserName;

    /** 变更后使用人部门（null = 不变更） */
    private String newUserDepartment;

    /** 变更后位置（asset_location.id，null = 不变更） */
    private Long newLocationId;

    /** 变更后存放位置明细（null = 不变更） */
    private String newLocationDetail;

    /** 变更后归属公司（company.id，null = 不变更） */
    private Long newCompanyId;

    /** 确认人ID（确认执行时写入） */
    private Long confirmerUserId;

    /** 确认人姓名（确认执行时快照） */
    private String confirmerName;

    /** 确认执行时间 */
    private LocalDateTime confirmTime;

    private Long companyId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ---- 以下为查询回填字段（非表列） ----

    /** 变更后位置名称（查询回填） */
    @TableField(exist = false)
    private String newLocationName;

    /** 变更后归属公司名称（查询回填） */
    @TableField(exist = false)
    private String newCompanyName;

    /** 明细行（列表/详情查询时批量回填，含资产编码/名称/序列号与变更前/后值） */
    @TableField(exist = false)
    private List<ChangeOrderItem> items;
}
