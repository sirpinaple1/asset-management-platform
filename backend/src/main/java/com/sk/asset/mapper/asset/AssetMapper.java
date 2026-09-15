package com.sk.asset.mapper.asset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.asset.Asset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

/**
 * 资产主表 Mapper。列表/分页走 MP LambdaQueryWrapper 单表查询（service 层批量补关联名称），
 * 详情走 JOIN 一次取齐（对齐 AssetModel 的 selectByIdWithRelations 模式）。
 */
@Mapper
public interface AssetMapper extends BaseMapper<Asset> {

    /**
     * 按 ID 查询资产详情，JOIN 返回全部关联名称
     */
    @Select("SELECT a.*, " +
            "c.name AS category_name, " +
            "m.name AS model_name, " +
            "s.name AS supplier_name, " +
            "l.name AS location_name, " +
            "hl.name AS home_location_name, " +
            "co.name AS company_name " +
            "FROM asset a " +
            "LEFT JOIN asset_category c ON a.category_id = c.id AND c.deleted = 0 " +
            "LEFT JOIN asset_model m ON a.model_id = m.id AND m.deleted = 0 " +
            "LEFT JOIN supplier s ON a.supplier_id = s.id AND s.deleted = 0 " +
            "LEFT JOIN asset_location l ON a.location_id = l.id AND l.deleted = 0 " +
            "LEFT JOIN asset_location hl ON a.home_location_id = hl.id AND hl.deleted = 0 " +
            "LEFT JOIN company co ON a.company_id = co.id AND co.deleted = 0 " +
            "WHERE a.deleted = 0 AND a.id = #{id}")
    @Results(id = "assetWithRelations", value = {
        @Result(property = "id",               column = "id"),
        @Result(property = "barcode",          column = "barcode"),
        @Result(property = "name",             column = "name"),
        @Result(property = "sn",               column = "sn"),
        @Result(property = "status",           column = "status"),
        @Result(property = "categoryId",       column = "category_id"),
        @Result(property = "modelId",          column = "model_id"),
        @Result(property = "supplierId",       column = "supplier_id"),
        @Result(property = "locationId",       column = "location_id"),
        @Result(property = "homeLocationId",   column = "home_location_id"),
        @Result(property = "locationDetail",   column = "location_detail"),
        @Result(property = "userId",           column = "user_id"),
        @Result(property = "userDepartment",   column = "user_department"),
        @Result(property = "adminUserId",      column = "admin_user_id"),
        @Result(property = "companyId",        column = "company_id"),
        @Result(property = "purchaseDate",     column = "purchase_date"),
        @Result(property = "amount",           column = "amount"),
        @Result(property = "remark",           column = "remark"),
        @Result(property = "createdAt",        column = "created_at"),
        @Result(property = "updatedAt",        column = "updated_at"),
        @Result(property = "categoryName",     column = "category_name"),
        @Result(property = "modelName",        column = "model_name"),
        @Result(property = "supplierName",     column = "supplier_name"),
        @Result(property = "locationName",     column = "location_name"),
        @Result(property = "homeLocationName", column = "home_location_name"),
        @Result(property = "companyName",      column = "company_name")
    })
    Asset selectByIdWithRelations(@Param("id") Long id);
}
