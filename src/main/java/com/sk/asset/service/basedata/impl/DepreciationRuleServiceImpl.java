package com.sk.asset.service.basedata.impl;

import com.sk.asset.mapper.basedata.DepreciationRuleMapper;
import com.sk.asset.service.basedata.DepreciationRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 折旧规则服务实现（骨架，M09/Phase 4 实现完整 CRUD）
 */
@Service
@RequiredArgsConstructor
public class DepreciationRuleServiceImpl implements DepreciationRuleService {

    private final DepreciationRuleMapper depreciationRuleMapper;
}
