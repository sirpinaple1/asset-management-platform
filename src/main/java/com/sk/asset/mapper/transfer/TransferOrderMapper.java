package com.sk.asset.mapper.transfer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.transfer.TransferOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调拨单主表 Mapper（M05）。
 */
@Mapper
public interface TransferOrderMapper extends BaseMapper<TransferOrder> {
}
