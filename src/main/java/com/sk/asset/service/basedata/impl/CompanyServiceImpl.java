package com.sk.asset.service.basedata.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.entity.basedata.Company;
import com.sk.asset.mapper.basedata.CompanyMapper;
import com.sk.asset.service.basedata.CompanyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 公司主体服务实现
 */
@Service
public class CompanyServiceImpl implements CompanyService {

    @Autowired
    private CompanyMapper companyMapper;

    @Override
    public List<Company> list() {
        return companyMapper.selectList(new LambdaQueryWrapper<>());
    }

    @Override
    public Company getById(Long id) {
        return companyMapper.selectById(id);
    }
}
