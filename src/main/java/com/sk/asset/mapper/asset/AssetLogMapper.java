package com.sk.asset.mapper.asset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.asset.AssetLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资产操作日志 Mapper（不可变日志，仅插入与按资产查询）
 */
@Mapper
public interface AssetLogMapper extends BaseMapper<AssetLog> {
}
