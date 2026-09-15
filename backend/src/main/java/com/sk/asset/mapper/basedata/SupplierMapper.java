package com.sk.asset.mapper.basedata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.basedata.Supplier;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 供应商 Mapper
 */
@Mapper
public interface SupplierMapper extends BaseMapper<Supplier> {

    /**
     * 恢复逻辑删除的记录（@TableLogic 会自动追加 deleted=0 条件，须用原生 SQL 绕过）
     */
    @Update("UPDATE supplier SET deleted = 0 WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") Long id);

    /**
     * 统计被 asset 引用的数量（删除前引用完整性检查，P2③）
     */
    @Select("SELECT COUNT(*) FROM asset WHERE supplier_id = #{id} AND deleted = 0")
    long countAssetRefs(@Param("id") Long id);
}
