package com.sk.asset.mapper.change;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.change.ChangeOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 变更单主表 Mapper（M06）。
 */
@Mapper
public interface ChangeOrderMapper extends BaseMapper<ChangeOrder> {
}
