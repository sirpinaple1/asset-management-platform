package com.sk.asset.mapper.dingtalk;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sk.asset.entity.dingtalk.DingtalkDept;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 钉钉部门树缓存 Mapper（多级主管审批链）。
 *
 * <p>upsert / 软删走原生 SQL：@TableLogic 逻辑删除会给 BaseMapper 的 update 自动追加
 * {@code deleted=0} 条件，无法复活/软删已删行，同步器绕开用 dept_id 直操作。</p>
 */
@Mapper
public interface DingtalkDeptMapper extends BaseMapper<DingtalkDept> {

    /** 按 dept_id 插入或更新（含复活：软删行重新出现时置回 deleted=0） */
    @Insert("INSERT INTO dingtalk_dept (dept_id, name, parent_id, manager_dd_user_ids) "
            + "VALUES (#{deptId}, #{name}, #{parentId}, #{managerDdUserIds}) "
            + "ON DUPLICATE KEY UPDATE name = VALUES(name), parent_id = VALUES(parent_id), "
            + "manager_dd_user_ids = VALUES(manager_dd_user_ids), deleted = 0")
    int upsert(DingtalkDept dept);

    /** 软删（钉钉侧已不存在的部门）；已删行幂等 */
    @Update("UPDATE dingtalk_dept SET deleted = 1 WHERE dept_id = #{deptId} AND deleted = 0")
    int softDeleteByDeptId(@Param("deptId") Long deptId);
}
