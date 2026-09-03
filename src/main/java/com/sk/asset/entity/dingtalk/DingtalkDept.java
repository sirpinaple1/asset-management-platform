package com.sk.asset.entity.dingtalk;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 钉钉部门树与主管本地缓存（V20260903，多级主管审批链）。
 *
 * <p>启动全量 + 每日定时刷新（{@code DingtalkDeptSyncService}）；钉钉侧已不存在的
 * 部门软删（deleted=1），解析链时自动滤除。根部门 dept_id=1 不落库（无主管属正常）。</p>
 */
@Data
@TableName("dingtalk_dept")
public class DingtalkDept {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 钉钉部门 id */
    private Long deptId;

    private String name;

    /** 父部门 id（根=1；根本身不落库，链在此自然截止） */
    private Long parentId;

    /** 主管钉钉 userid，逗号分隔（dept_manager_userid_list；多主管取第一人入链） */
    private String managerDdUserIds;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
