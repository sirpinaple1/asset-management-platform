package com.sk.asset.mapper.stocktake;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.stocktake.Stocktake;
import org.apache.ibatis.annotations.Mapper;

/**
 * 盘点任务主表 Mapper
 */
@Mapper
public interface StocktakeMapper extends BaseMapper<Stocktake> {
}
