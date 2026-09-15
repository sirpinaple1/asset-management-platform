package com.sk.asset.entity.notification;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知（sys_notification，B2 通知中心）。
 * 通知与业务同库同事务写入；不可变（只追加 + 标记已读），无逻辑删除/更新列。
 */
@Data
@TableName("sys_notification")
public class SysNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人ID（comm_public_basic 用户） */
    private Long userId;

    /** 类型：DOC_SUBMITTED / DOC_APPROVED / DOC_REJECTED / DOC_COMPLETED */
    private String type;

    /** 通知标题（含单据类型/单号/操作人/原因摘要） */
    private String title;

    /** 业务单据类型：RECEIVE / BORROW / TRANSFER / CHANGE */
    private String bizType;

    /** 业务单据ID（对应各单据主表 id） */
    private Long bizId;

    /** 已读标记：0-未读 1-已读 */
    private Integer readFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
