package com.sk.asset.mapper.basedata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.basedata.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资产分类 Mapper
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
