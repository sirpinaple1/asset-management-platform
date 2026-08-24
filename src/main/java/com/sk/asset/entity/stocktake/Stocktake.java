package com.sk.asset.entity.stocktake;

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
 * 盘点任务主表（M07）。范围名称、统计计数与明细行仅用于查询回填，非表列。
 * 创建时按范围圈资产快照生成明细（报废资产不参与），expected 位置 = 创建时资产账面位置。
 */
@Data
@TableName("stocktake")
public class Stocktake {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 盘点任务名称 */
    private String name;

    /** 状态：PENDING-待开始 IN_PROGRESS-进行中 COMPLETED-已完成 CANCELLED-已取消（对应 StocktakeStatus 枚举） */
    private String status;

    /** 盘点范围-位置（asset_location.id，空=全库；含子树） */
    private Long locationId;

    /** 盘点范围-分类（asset_category.id，空=全类） */
    private Long categoryId;

    /** 创建人ID（comm_public_basic 用户） */
    private Long creatorUserId;

    /** 创建人姓名（提交时快照） */
    private String creatorName;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 完成时间 */
    private LocalDateTime completeTime;

    /** 备注 */
    private String remark;

    /** 归属公司（company.id） */
    private Long companyId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ---- 以下为查询回填字段（非表列） ----

    /** 盘点范围-位置名称（查询回填） */
    @TableField(exist = false)
    private String locationName;

    /** 盘点范围-分类名称（查询回填） */
    @TableField(exist = false)
    private String categoryName;

    /** 明细行（详情/创建返回时回填，含资产与位置名称） */
    @TableField(exist = false)
    private List<StocktakeItem> items;

    /** 统计：明细总数（查询回填） */
    @TableField(exist = false)
    private Long totalCount;

    /** 统计：待盘数 */
    @TableField(exist = false)
    private Long pendingCount;

    /** 统计：账实相符数 */
    @TableField(exist = false)
    private Long matchedCount;

    /** 统计：位置不符数 */
    @TableField(exist = false)
    private Long mismatchCount;

    /** 统计：盘亏数 */
    @TableField(exist = false)
    private Long notFoundCount;

    /** 统计：盘盈数 */
    @TableField(exist = false)
    private Long extraCount;
}
