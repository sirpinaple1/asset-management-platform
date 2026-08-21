package com.sk.asset.dto.asset;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.enums.asset.AssetStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 资产 Excel 导出行（EasyExcel 注解式，列对齐原型资产管理表格）
 */
@Data
@ColumnWidth(20)
public class AssetExportRow {

    @ExcelProperty("资产编码")
    @ColumnWidth(18)
    private String barcode;

    @ExcelProperty("资产名称")
    private String name;

    @ExcelProperty("序列号")
    private String sn;

    @ExcelProperty("分类")
    private String categoryName;

    @ExcelProperty("型号")
    private String modelName;

    @ExcelProperty("供应商")
    private String supplierName;

    @ExcelProperty("当前位置")
    private String locationName;

    @ExcelProperty("存放明细")
    private String locationDetail;

    @ExcelProperty("使用部门")
    private String userDepartment;

    @ExcelProperty("归属公司")
    private String companyName;

    @ExcelProperty("购入日期")
    @ColumnWidth(14)
    private LocalDate purchaseDate;

    @ExcelProperty("购入金额(元)")
    @ColumnWidth(14)
    private BigDecimal amount;

    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String statusLabel;

    @ExcelProperty("备注")
    @ColumnWidth(28)
    private String remark;

    public static List<AssetExportRow> fromList(List<Asset> list) {
        return list.stream().map(entity -> {
            AssetExportRow row = new AssetExportRow();
            row.setBarcode(entity.getBarcode());
            row.setName(entity.getName());
            row.setSn(entity.getSn());
            row.setCategoryName(entity.getCategoryName());
            row.setModelName(entity.getModelName());
            row.setSupplierName(entity.getSupplierName());
            row.setLocationName(entity.getLocationName());
            row.setLocationDetail(entity.getLocationDetail());
            row.setUserDepartment(entity.getUserDepartment());
            row.setCompanyName(entity.getCompanyName());
            row.setPurchaseDate(entity.getPurchaseDate());
            row.setAmount(entity.getAmount());
            row.setStatusLabel(AssetStatus.of(entity.getStatus()).getLabel());
            row.setRemark(entity.getRemark());
            return row;
        }).collect(Collectors.toList());
    }
}
