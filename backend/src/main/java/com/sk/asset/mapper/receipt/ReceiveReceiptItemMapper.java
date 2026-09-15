package com.sk.asset.mapper.receipt;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.receipt.ReceiveReceiptItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 领用/借用单明细 Mapper。
 */
@Mapper
public interface ReceiveReceiptItemMapper extends BaseMapper<ReceiveReceiptItem> {
}
