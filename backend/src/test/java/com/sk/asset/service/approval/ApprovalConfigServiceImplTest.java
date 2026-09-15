package com.sk.asset.service.approval;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sk.asset.auth.UserDirectory;
import com.sk.asset.common.BusinessException;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.approval.ApprovalConfig;
import com.sk.asset.entity.basedata.Location;
import com.sk.asset.enums.approval.ApprovalConfigType;
import com.sk.asset.mapper.approval.ApprovalConfigMapper;
import com.sk.asset.mapper.basedata.LocationMapper;
import com.sk.asset.service.approval.impl.ApprovalConfigServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审批链配置服务单测：类型/键/审批人校验、(type, key) 唯一（预检 + DuplicateKeyException 兜底）、
 * 更新空键保持原值、逻辑删除释放唯一键、分页回填审批人姓名与位置名称。
 */
@ExtendWith(MockitoExtension.class)
class ApprovalConfigServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaQueryWrapper/LambdaUpdateWrapper 列名解析需要 TableInfo 缓存，手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalConfig.class);
    }

    private static final Long APPROVER = 200L;

    @Mock
    private ApprovalConfigMapper configMapper;

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private UserDirectory userDirectory;

    @InjectMocks
    private ApprovalConfigServiceImpl configService;

    private ApprovalConfig deptConfig(String key) {
        ApprovalConfig config = new ApprovalConfig();
        config.setId(5L);
        config.setConfigType("DEPT_SUPERVISOR");
        config.setConfigKey(key);
        config.setApproverUserId(APPROVER);
        return config;
    }

    private void stubApproverExists() {
        when(userDirectory.exists(APPROVER)).thenReturn(true);
    }

    // ---- save ----

    @Test
    void save_shouldInsertDeptSupervisorConfig() {
        ApprovalConfig config = deptConfig(" PMC部 ");
        stubApproverExists();
        when(configMapper.selectCount(any())).thenReturn(0L);
        when(configMapper.insert(any(ApprovalConfig.class))).thenReturn(1);

        configService.save(config);

        ArgumentCaptor<ApprovalConfig> captor = ArgumentCaptor.forClass(ApprovalConfig.class);
        verify(configMapper).insert(captor.capture());
        // 键两侧空白归一化后落库
        assertEquals("PMC部", captor.getValue().getConfigKey());
        assertEquals("DEPT_SUPERVISOR", captor.getValue().getConfigType());
    }

    @Test
    void save_shouldInsertWarehouseKeeperWithLocationCheck() {
        ApprovalConfig config = new ApprovalConfig();
        config.setConfigType("WAREHOUSE_KEEPER");
        config.setConfigKey("1");
        config.setApproverUserId(APPROVER);
        stubApproverExists();
        when(locationMapper.selectById(1L)).thenReturn(new Location());
        when(configMapper.selectCount(any())).thenReturn(0L);
        when(configMapper.insert(any(ApprovalConfig.class))).thenReturn(1);

        configService.save(config);

        verify(configMapper).insert(any(ApprovalConfig.class));
    }

    @Test
    void save_shouldRejectWhenWarehouseKeyNotLocationId() {
        ApprovalConfig config = new ApprovalConfig();
        config.setConfigType("WAREHOUSE_KEEPER");
        config.setConfigKey("abc");
        config.setApproverUserId(APPROVER);
        stubApproverExists();

        BusinessException exception = assertThrows(BusinessException.class, () -> configService.save(config));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("位置 id"));
    }

    @Test
    void save_shouldRejectWhenWarehouseLocationMissing() {
        ApprovalConfig config = new ApprovalConfig();
        config.setConfigType("WAREHOUSE_KEEPER");
        config.setConfigKey("9");
        config.setApproverUserId(APPROVER);
        stubApproverExists();
        when(locationMapper.selectById(9L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> configService.save(config));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("位置不存在"));
    }

    @Test
    void save_shouldRejectWhenApproverMissingInUserDirectory() {
        ApprovalConfig config = deptConfig("PMC部");
        when(userDirectory.exists(APPROVER)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> configService.save(config));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("审批人不存在"));
    }

    @Test
    void save_shouldRejectWhenKeyBlank() {
        ApprovalConfig config = deptConfig(" ");

        BusinessException exception = assertThrows(BusinessException.class, () -> configService.save(config));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("配置键不能为空"));
    }

    @Test
    void save_shouldRejectWhenTypeInvalid() {
        ApprovalConfig config = deptConfig("PMC部");
        config.setConfigType("BAD");

        BusinessException exception = assertThrows(BusinessException.class, () -> configService.save(config));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("非法审批链配置类型"));
    }

    @Test
    void save_shouldRejectDuplicateByPrecheck() {
        ApprovalConfig config = deptConfig("PMC部");
        stubApproverExists();
        when(configMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class, () -> configService.save(config));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("已配置审批人"));
    }

    @Test
    void save_shouldTranslateDuplicateKeyExceptionTo400() {
        // 预检通过但并发/逻辑删除行占用唯一键 → DuplicateKeyException 兜底转 400
        ApprovalConfig config = deptConfig("PMC部");
        stubApproverExists();
        when(configMapper.selectCount(any())).thenReturn(0L);
        when(configMapper.insert(any(ApprovalConfig.class)))
                .thenThrow(new DuplicateKeyException("uk_approval_config_type_key"));

        BusinessException exception = assertThrows(BusinessException.class, () -> configService.save(config));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("已配置审批人"));
        assertTrue(exception.getMessage().contains("可能被已删除配置占用"));
    }

    // ---- update ----

    @Test
    void update_shouldKeepExistingKeyWhenRequestKeyBlank() {
        // 前端空值不发送/发空串两种风格兼容：键为空保持原值
        ApprovalConfig existing = deptConfig("资材管理中心/PMC部");
        when(configMapper.selectById(5L)).thenReturn(existing);
        stubApproverExists();
        when(configMapper.selectCount(any())).thenReturn(0L);
        when(configMapper.update(isNull(), any())).thenReturn(1);

        ApprovalConfig request = deptConfig(null);
        configService.updateById(request);

        ArgumentCaptor<LambdaUpdateWrapper<ApprovalConfig>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(configMapper).update(isNull(), captor.capture());
        // 落库键 = 原值（wrapper 参数含既有键），非 null
        assertTrue(captor.getValue().getSqlSet().contains("config_key"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("资材管理中心/PMC部"),
                "空键更新应保持原键，实际参数：" + captor.getValue().getParamNameValuePairs());
    }

    @Test
    void update_shouldRejectWhenConfigMissing() {
        when(configMapper.selectById(9L)).thenReturn(null);
        ApprovalConfig request = deptConfig("PMC部");
        request.setId(9L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> configService.updateById(request));

        assertEquals(404, exception.getCode());
    }

    // ---- delete ----

    @Test
    void delete_shouldReleaseKeyAndMarkDeleted() {
        // 逻辑删除同时置空 config_key 释放唯一键（对齐 Category 删除释放 code 模式）
        when(configMapper.selectById(5L)).thenReturn(deptConfig("PMC部"));
        when(configMapper.update(isNull(), any())).thenReturn(1);

        configService.deleteById(5L);

        ArgumentCaptor<LambdaUpdateWrapper<ApprovalConfig>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(configMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("config_key"), "删除应置空键释放唯一键，实际 SET：" + sqlSet);
        assertTrue(sqlSet.contains("deleted"), "删除应置逻辑删除标记，实际 SET：" + sqlSet);
    }

    @Test
    void delete_shouldRejectWhenConfigMissing() {
        when(configMapper.selectById(9L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> configService.deleteById(9L));

        assertEquals(404, exception.getCode());
    }

    // ---- page：回填展示字段 ----

    @Test
    void page_shouldFillApproverNameAndLocationLabel() {
        ApprovalConfig dept = deptConfig("资材管理中心/PMC部");
        ApprovalConfig warehouse = new ApprovalConfig();
        warehouse.setId(6L);
        warehouse.setConfigType("WAREHOUSE_KEEPER");
        warehouse.setConfigKey("1");
        warehouse.setApproverUserId(300L);
        when(configMapper.selectPage(any(), any())).thenAnswer(inv -> {
            Page<ApprovalConfig> page = inv.getArgument(0);
            page.setRecords(List.of(dept, warehouse));
            page.setTotal(2);
            return page;
        });
        Map<Long, UserResp> users = new HashMap<>();
        users.put(APPROVER, new UserResp(APPROVER, "u200", "李四", null));
        users.put(300L, new UserResp(300L, "u300", "王五", null));
        when(userDirectory.namesByIds(any())).thenReturn(users);
        Location location = new Location();
        location.setId(1L);
        location.setName("一号车间");
        when(locationMapper.selectBatchIds(any())).thenReturn(List.of(location));

        List<ApprovalConfig> records = configService.page(1, 20, null, null).getRecords();

        assertEquals(2, records.size());
        // 部门主管：姓名回填 + 键展示 = 部门路径本身
        assertEquals("李四", records.get(0).getApproverUserName());
        assertEquals("资材管理中心/PMC部", records.get(0).getConfigKeyLabel());
        // 仓管：姓名回填 + 键展示 = 位置名称（人可读）
        assertEquals("王五", records.get(1).getApproverUserName());
        assertEquals("一号车间", records.get(1).getConfigKeyLabel());
    }
}
