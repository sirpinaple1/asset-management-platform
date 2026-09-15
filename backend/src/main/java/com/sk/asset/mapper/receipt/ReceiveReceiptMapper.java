package com.sk.asset.mapper.receipt;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.receipt.ReceiveReceipt;
import org.apache.ibatis.annotations.Mapper;

/**
 * 领用/借用单主表 Mapper。单表查询走 LambdaQueryWrapper，
 * serial_no 生成用 likeRight + orderByDesc + LIMIT 1 FOR UPDATE（锁定读串行取号）。
 */
@Mapper
public interface ReceiveReceiptMapper extends BaseMapper<ReceiveReceipt> {
}
