package com.sk.asset.mapper.receipt;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.receipt.AssetAllocation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资产持有关系 Mapper。
 */
@Mapper
public interface AssetAllocationMapper extends BaseMapper<AssetAllocation> {
}
