package com.sk.asset.mapper.basedata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.basedata.Location;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 整体替换子树 path 前缀（移动节点时重算子孙路径，P1②）：
     * 自身与所有以 oldPath 为前缀的节点，前缀替换为 newPath
     */
    @Update("UPDATE asset_location " +
            "SET path = CONCAT(#{newPath}, SUBSTRING(path, CHAR_LENGTH(#{oldPath}) + 1)) " +
            "WHERE deleted = 0 AND path LIKE CONCAT(#{oldPath}, '%')")
    int updateSubtreePaths(@Param("oldPath") String oldPath, @Param("newPath") String newPath);

    /**
     * 统计被 asset 引用的数量（当前位置或应归放位置，删除前引用完整性检查，P2③）
     */
    @Select("SELECT COUNT(*) FROM asset WHERE (location_id = #{id} OR home_location_id = #{id}) AND deleted = 0")
    long countAssetRefs(@Param("id") Long id);
}
