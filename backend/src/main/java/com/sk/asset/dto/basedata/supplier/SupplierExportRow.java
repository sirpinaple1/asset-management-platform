package com.sk.asset.dto.basedata.supplier;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.sk.asset.entity.basedata.Supplier;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 供应商 Excel 导出行（列对齐厂商管理原型表格）
 */
@Data
@ColumnWidth(20)
public class SupplierExportRow {

    @ExcelProperty("供应商编码")
    @ColumnWidth(12)
    private Long id;

    @ExcelProperty("供应商名称")
    private String name;

    @ExcelProperty("联系人")
    private String contact;

    @ExcelProperty("联系电话")
    private String phone;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("地址")
    @ColumnWidth(32)
    private String address;

    @ExcelProperty("状态")
    @ColumnWidth(10)
    private String statusLabel;

    @ExcelProperty("备注")
    @ColumnWidth(28)
    private String remark;

    public static List<SupplierExportRow> fromList(List<Supplier> list) {
        return list.stream().map(entity -> {
            SupplierExportRow row = new SupplierExportRow();
            row.setId(entity.getId());
            row.setName(entity.getName());
            row.setContact(entity.getContact());
            row.setPhone(entity.getPhone());
            row.setEmail(entity.getEmail());
            row.setAddress(entity.getAddress());
            row.setStatusLabel(entity.getStatus() != null && entity.getStatus() == 1 ? "启用" : "停用");
            row.setRemark(entity.getRemark());
            return row;
        }).collect(Collectors.toList());
    }
}
