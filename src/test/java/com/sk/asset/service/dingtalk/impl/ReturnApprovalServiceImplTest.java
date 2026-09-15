package com.sk.asset.service.dingtalk.impl;

import com.sk.asset.auth.UserDirectory;
import com.sk.asset.dto.dingtalk.ReturnApprovalResp;
import com.sk.asset.dto.user.UserResp;
import com.sk.asset.entity.asset.Asset;
import com.sk.asset.entity.dingtalk.ApprovalInstance;
import com.sk.asset.mapper.asset.AssetMapper;
import com.sk.asset.mapper.dingtalk.ApprovalInstanceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 钉钉退还审批查询单测：发起人反查、资产快照明细、未绑定用户兜底。
 */
@ExtendWith(MockitoExtension.class)
class ReturnApprovalServiceImplTest {

    static {
        // 纯单测无 MyBatis 启动流程，LambdaQueryWrapper 列名解析需要 TableInfo 缓存，手动初始化
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                ApprovalInstance.class);
    }

    @Mock
    private ApprovalInstanceMapper approvalInstanceMapper;

    @Mock
    private AssetMapper assetMapper;

    @Mock
    private UserDirectory userDirectory;

    @InjectMocks
    private ReturnApprovalServiceImpl service;

    @Test
    void 列表_发起人反查与资产快照明细() {
        ApprovalInstance r1 = record(1L, "user_example_002", "RUNNING", null, "101");
        ApprovalInstance r2 = record(2L, "1839004256848168", "COMPLETED", "agree", "98,166");
        when(approvalInstanceMapper.selectList(any())).thenReturn(List.of(r1, r2));
        when(userDirectory.findByDdUserId("user_example_002"))
                .thenReturn(new UserResp(691L, "xp", "李四", "综合管理部/IT科"));
        when(userDirectory.findByDdUserId("1839004256848168"))
                .thenReturn(new UserResp(164L, "lr", "李溶", "非A商务部"));
        Asset a98 = asset(98L, "SKBGDN394");
        Asset a101 = asset(101L, "SKBGIT0151");
        Asset a166 = asset(166L, "SKBGDN231");
        lenient().when(assetMapper.selectBatchIds(any())).thenReturn(List.of(a98, a101, a166));

        List<ReturnApprovalResp> list = service.list();

        assertEquals(2, list.size());
        ReturnApprovalResp first = list.get(0);
        assertEquals(691L, first.getApplicantUserId());
        assertEquals("李四", first.getApplicantName());
        assertEquals("RUNNING", first.getStatus());
        assertEquals(1, first.getAssets().size());
        assertEquals("SKBGIT0151", first.getAssets().get(0).getBarcode());
        ReturnApprovalResp second = list.get(1);
        assertEquals("agree", second.getResult());
        assertEquals(2, second.getAssets().size());
    }

    @Test
    void 列表_发起人未绑定内部账号_兜底钉钉用户() {
        when(approvalInstanceMapper.selectList(any()))
                .thenReturn(List.of(record(3L, "unknown-dd-id", "RUNNING", null, null)));
        when(userDirectory.findByDdUserId("unknown-dd-id")).thenReturn(null);

        List<ReturnApprovalResp> list = service.list();

        assertEquals(1, list.size());
        assertNull(list.get(0).getApplicantUserId());
        assertEquals("钉钉用户", list.get(0).getApplicantName());
        // 存量记录无资产快照：明细为空列表
        assertEquals(0, list.get(0).getAssets().size());
    }

    @Test
    void 列表_无退还记录_返回空() {
        when(approvalInstanceMapper.selectList(any())).thenReturn(List.of());

        assertEquals(0, service.list().size());
    }

    private ApprovalInstance record(Long id, String ddUserId, String status, String result, String assetIds) {
        ApprovalInstance record = new ApprovalInstance();
        record.setId(id);
        record.setBizType(ApprovalInstance.BIZ_RETURN);
        record.setBizId(0L);
        record.setOriginatorDdUserId(ddUserId);
        record.setStatus(status);
        record.setResult(result);
        record.setAssetIds(assetIds);
        return record;
    }

    private Asset asset(Long id, String barcode) {
        Asset asset = new Asset();
        asset.setId(id);
        asset.setBarcode(barcode);
        asset.setName("name-" + barcode);
        asset.setStatus("IN_USE");
        return asset;
    }
}
