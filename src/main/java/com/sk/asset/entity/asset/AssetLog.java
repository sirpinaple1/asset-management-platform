package com.sk.asset.entity.asset;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产操作日志（service 层内置写入，不可变，无逻辑删除/更新列）。
 * content 对齐现有系统"【字段】由【旧值】变更为【新值】"格式。
 */
@Data
@TableName("asset_log")
public class AssetLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;

    /** 操作类型：新增/领用/归还/调拨/实物信息变更/盘点处理/报废 */
    private String operationType;

    /** 操作人ID（comm_public_basic 用户，迁移数据匹配不到时为空） */
    private Long operatorUserId;

    /** 操作人原始文本（M08 迁移保底） */
    private String operatorLabel;

    /** 操作内容 */
    private String content;

    /** 字段变更明细 JSON（M08 历史日志迁移产物，如 [{"field":"使用人","before":"","after":"谷仍山"}]） */
    private String diffJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
