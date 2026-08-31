package com.sk.asset.entity.dingtalk;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 钉钉审批实例 ↔ 系统单据映射（M10，V20260834）。
 *
 * <p>outbox 语义：单据创建事务提交后异步推钉钉，sync_status 记录同步结果
 * （PENDING→SYNCED/FAILED），失败不回滚业务单据（站内审批兜底，ADR-0007 D4）；
 * process_instance_id 唯一键兜底事件重复推送幂等。</p>
 */
@Data
@TableName("approval_instance")
public class ApprovalInstance {

    /** 单据类型：RECEIVE/BORROW/TRANSFER/CHANGE（LEAVE_RETURN 预留） */
    public static final String BIZ_RECEIVE = "RECEIVE";
    public static final String BIZ_BORROW = "BORROW";
    public static final String BIZ_TRANSFER = "TRANSFER";
    public static final String BIZ_CHANGE = "CHANGE";

    /** 同步状态 */
    public static final String SYNC_PENDING = "PENDING";
    public static final String SYNC_SYNCED = "SYNCED";
    public static final String SYNC_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizType;

    private Long bizId;

    private String processCode;

    /** 钉钉审批实例 id（创建成功后回填；失败时为 NULL） */
    private String processInstanceId;

    private String title;

    /** 发起人钉钉 userid（提交时快照，事件反查免查库） */
    private String originatorDdUserId;

    private String syncStatus;

    private String syncError;

    /** 钉钉实例状态：RUNNING/COMPLETED/TERMINATED */
    private String status;

    /** 钉钉审批结果：agree/refuse */
    private String result;

    /** 事件回调明细（JSON 数组字符串，按次追加，审计用） */
    private String callbacks;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
