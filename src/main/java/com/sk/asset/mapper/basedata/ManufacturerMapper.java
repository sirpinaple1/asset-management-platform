package com.sk.asset.mapper.basedata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.basedata.Manufacturer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 厂商 Mapper
 */
@Mapper
public interface ManufacturerMapper extends BaseMapper<Manufacturer> {

    /**
     * 恢复逻辑删除的记录（@TableLogic 会自动追加 deleted=0 条件，须用原生 SQL 绕过）
     */
    @Update("UPDATE manufacturer SET deleted = 0 WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") Long id);
}
