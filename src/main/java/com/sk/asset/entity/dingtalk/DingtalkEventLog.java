package com.sk.asset.entity.dingtalk;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 钉钉事件日志（M10 补强，V20260904）：Stream 事件先落库后处理。
 *
 * <p>eventId 唯一键持久化去重（钉钉重复推送/重启后内存锁失效的兜底）；
 * 恒回 SUCCESS 导致的事件不重发，崩溃/重启瞬间的滞留事件经此表可查可回放。
 * 仅记录 bpms_* 审批事件，其他事件类型无业务含义不落表。</p>
 */
@Data
@TableName("dingtalk_event_log")
public class DingtalkEventLog {

    /** 落库未处理（处理中崩溃会滞留此状态，需人工排查） */
    public static final String STATUS_RECEIVED = "RECEIVED";
    /** 处理成功（含"记日志忽略"的正常业务分支） */
    public static final String STATUS_PROCESSED = "PROCESSED";
    /** 预期忽略：业务校验拦截（409/403 幂等/越权）、非本企业、空数据 */
    public static final String STATUS_IGNORED = "IGNORED";
    /** 基础设施异常（已告警，需人工介入） */
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 钉钉事件推送唯一 id（Stream 头 eventId，去重键） */
    private String eventId;

    private String eventType;

    /** 审批实例 id（从 data 提取，排查索引） */
    private String processInstanceId;

    private String corpId;

    /** 事件原始 data JSON（审计/回放） */
    private String payload;

    private String status;

    /** IGNORED/FAILED 时的原因说明 */
    private String error;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
