package com.sk.asset.mapper.dingtalk;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.dingtalk.DingtalkEventLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 钉钉事件日志 Mapper（M10 补强：eventId 幂等去重 + 事件不丢可查）。
 */
@Mapper
public interface DingtalkEventLogMapper extends BaseMapper<DingtalkEventLog> {
}
