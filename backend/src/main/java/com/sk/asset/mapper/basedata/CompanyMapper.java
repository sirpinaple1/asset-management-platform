package com.sk.asset.mapper.basedata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.basedata.Company;
import org.apache.ibatis.annotations.Mapper;

/**
 * 公司主体 Mapper
 */
@Mapper
public interface CompanyMapper extends BaseMapper<Company> {
}
