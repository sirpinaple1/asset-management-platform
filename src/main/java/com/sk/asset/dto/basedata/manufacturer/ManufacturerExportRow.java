package com.sk.asset.dto.basedata.manufacturer;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.sk.asset.entity.basedata.Manufacturer;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 厂商 Excel 导出行（EasyExcel 注解式，列对齐原型厂商管理表格：编码/名称/联系人/电话/地址/状态）
 */
@Data
@ColumnWidth(20)
public class ManufacturerExportRow {

    @ExcelProperty("厂商编码")
    @ColumnWidth(12)
    private Long id;

    @ExcelProperty("厂商名称")
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

    public static List<ManufacturerExportRow> fromList(List<Manufacturer> list) {
        return list.stream().map(entity -> {
            ManufacturerExportRow row = new ManufacturerExportRow();
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
