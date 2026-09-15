package com.sk.asset.mapper.basedata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.basedata.AssetModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 资产型号 Mapper
 */
@Mapper
public interface AssetModelMapper extends BaseMapper<AssetModel> {

    /**
     * 查询全部型号，JOIN 返回关联名称
     */
    @Select("SELECT m.*, " +
            "c.name AS category_name, " +
            "mf.name AS manufacturer_name, " +
            "dr.name AS depreciation_rule_name " +
            "FROM asset_model m " +
            "LEFT JOIN asset_category c ON m.category_id = c.id AND c.deleted = 0 " +
            "LEFT JOIN manufacturer mf ON m.manufacturer_id = mf.id AND mf.deleted = 0 " +
            "LEFT JOIN depreciation_rule dr ON m.depreciation_id = dr.id AND dr.deleted = 0 " +
            "WHERE m.deleted = 0 " +
            "ORDER BY m.id")
    @Results(id = "assetModelWithRelations", value = {
        @Result(property = "id",                  column = "id"),
        @Result(property = "name",                column = "name"),
        @Result(property = "modelNumber",         column = "model_number"),
        @Result(property = "categoryId",          column = "category_id"),
        @Result(property = "manufacturerId",      column = "manufacturer_id"),
        @Result(property = "depreciationId",      column = "depreciation_id"),
        @Result(property = "eolMonths",           column = "eol_months"),
        @Result(property = "notes",               column = "notes"),
        @Result(property = "companyId",           column = "company_id"),
        @Result(property = "deleted",             column = "deleted"),
        @Result(property = "createdAt",           column = "created_at"),
        @Result(property = "updatedAt",           column = "updated_at"),
        @Result(property = "categoryName",        column = "category_name"),
        @Result(property = "manufacturerName",    column = "manufacturer_name"),
        @Result(property = "depreciationRuleName",column = "depreciation_rule_name")
    })
    List<AssetModel> selectAllWithRelations();

    /**
     * 按分类查询型号，JOIN 返回关联名称
     */
    @Select("SELECT m.*, " +
            "c.name AS category_name, " +
            "mf.name AS manufacturer_name, " +
            "dr.name AS depreciation_rule_name " +
            "FROM asset_model m " +
            "LEFT JOIN asset_category c ON m.category_id = c.id AND c.deleted = 0 " +
            "LEFT JOIN manufacturer mf ON m.manufacturer_id = mf.id AND mf.deleted = 0 " +
            "LEFT JOIN depreciation_rule dr ON m.depreciation_id = dr.id AND dr.deleted = 0 " +
            "WHERE m.deleted = 0 AND m.category_id = #{categoryId} " +
            "ORDER BY m.id")
    @ResultMap("assetModelWithRelations")
    List<AssetModel> selectByCategoryIdWithRelations(@Param("categoryId") Long categoryId);

    /**
     * 按 ID 查询型号，JOIN 返回关联名称（详情接口与列表对齐，P2⑤）
     */
    @Select("SELECT m.*, " +
            "c.name AS category_name, " +
            "mf.name AS manufacturer_name, " +
            "dr.name AS depreciation_rule_name " +
            "FROM asset_model m " +
            "LEFT JOIN asset_category c ON m.category_id = c.id AND c.deleted = 0 " +
            "LEFT JOIN manufacturer mf ON m.manufacturer_id = mf.id AND mf.deleted = 0 " +
            "LEFT JOIN depreciation_rule dr ON m.depreciation_id = dr.id AND dr.deleted = 0 " +
            "WHERE m.deleted = 0 AND m.id = #{id}")
    @ResultMap("assetModelWithRelations")
    AssetModel selectByIdWithRelations(@Param("id") Long id);

    /**
     * 统计被 asset 引用的数量（删除前引用完整性检查，P2③）
     */
    @Select("SELECT COUNT(*) FROM asset WHERE model_id = #{id} AND deleted = 0")
    long countAssetRefs(@Param("id") Long id);
}
