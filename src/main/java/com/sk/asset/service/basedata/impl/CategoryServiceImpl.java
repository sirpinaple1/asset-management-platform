package com.sk.asset.service.basedata.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sk.asset.entity.basedata.Category;
import com.sk.asset.mapper.basedata.CategoryMapper;
import com.sk.asset.service.basedata.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资产分类服务实现
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public List<Category> list() {
        return categoryMapper.selectList(
            new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId)
        );
    }

    @Override
    public Category getById(Long id) {
        return categoryMapper.selectById(id);
    }

    @Override
    public void save(Category category) {
        categoryMapper.insert(category);
    }

    @Override
    public void updateById(Category category) {
        categoryMapper.updateById(category);
    }

    @Override
    public void deleteById(Long id) {
        categoryMapper.deleteById(id);
    }
}
