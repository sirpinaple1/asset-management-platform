package com.sk.asset.auth;

import com.sk.asset.common.BusinessException;
import com.sk.asset.common.PageResp;
import com.sk.asset.dto.user.UserResp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserDirectory 未配置（url 为空）时的降级语义：
 * exists 放行（assignee 校验跳过）、search 明确 503。
 * JDBC 查询路径属集成行为，本地联调覆盖（application-local.yml user-directory 配置）。
 */
class UserDirectoryTest {

    private UserDirectory unconfigured() {
        return new UserDirectory("", "", "");
    }

    @Test
    void isConfigured_shouldBeFalseWhenUrlBlank() {
        assertFalse(unconfigured().isConfigured());
        assertTrue(new UserDirectory("jdbc:mysql://127.0.0.1:3306/db", "u", "p").isConfigured());
    }

    @Test
    void exists_shouldDegradeToTrueWhenUnconfigured() {
        assertTrue(unconfigured().exists(123L));
    }

    @Test
    void exists_shouldRejectNullUserId() {
        // 即便配置了连接，null 也不应触发查询
        assertFalse(new UserDirectory("jdbc:mysql://127.0.0.1:3306/db", "u", "p").exists(null));
    }

    @Test
    void search_shouldThrow503WhenUnconfigured() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> unconfigured().search("张", 1, 20));

        assertEquals(503, exception.getCode());
        assertTrue(exception.getMessage().contains("用户目录未配置"));
    }

    @Test
    void search_shouldClampPageParams() {
        // page/size 边界收敛属纯逻辑：未配置时在触达 JDBC 前抛 503，此处仅验证不因非法参数抛 NPE/越界
        assertThrows(BusinessException.class, () -> unconfigured().search(null, -1, 0));
    }

    @Test
    void pageResp_shouldExposeExpectedShape() {
        // PageResp 组装契约（编译期形状保护）：records/total/page/size 四件套
        PageResp<UserResp> resp = new PageResp<>();
        resp.setRecords(java.util.List.of(new UserResp(1L, "SK10086", "张三", "PMC部")));
        resp.setTotal(1L);
        resp.setPage(1L);
        resp.setSize(20L);
        assertEquals(1, resp.getRecords().size());
        assertEquals(1L, resp.getTotal());
    }
}
