package com.sk.asset.mapper.stocktake;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.stocktake.StocktakeItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 盘点明细 Mapper
 */
@Mapper
public interface StocktakeItemMapper extends BaseMapper<StocktakeItem> {
}
