package com.sk.asset.dto.asset;

import com.sk.asset.enums.asset.AssetStatus;
import lombok.Data;

/**
 * 资产列表筛选条件（M03：对齐原型资产管理页的过滤维度）
 */
@Data
public class AssetQuery {

    /** 状态筛选 */
    private AssetStatus status;

    /** 分类 */
    private Long categoryId;

    /** 当前位置 */
    private Long locationId;

    /** 归属公司 */
    private Long companyId;

    /** 使用人 */
    private Long userId;

    /** 资产管理员 */
    private Long adminUserId;

    /** 关键词（barcode/name/sn 模糊匹配；空格分隔多组=或，组内"-"分隔=且） */
    private String keyword;
}
