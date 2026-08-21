package com.sk.asset.mapper.transfer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.transfer.TransferOrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调拨单明细 Mapper（M05）。
 */
@Mapper
public interface TransferOrderItemMapper extends BaseMapper<TransferOrderItem> {
}
