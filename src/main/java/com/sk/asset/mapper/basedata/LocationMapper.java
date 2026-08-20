package com.sk.asset.mapper.basedata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.basedata.Location;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 区域位置 Mapper
 */
@Mapper
public interface LocationMapper extends BaseMapper<Location> {

    /**
     * 查询指定路径下的所有子节点（含自身）
     * @param parentPath 父节点路径（如 "/1/" 或 "/1/5/"）
     * @return 子树节点列表（扁平）
     */
    @Select("SELECT * FROM asset_location " +
            "WHERE deleted = 0 AND path LIKE CONCAT(#{parentPath}, '%') " +
            "ORDER BY path, sort_order")
    List<Location> selectByParentPath(@Param("parentPath") String parentPath);
}
