package com.sk.asset.migration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * sys_user 目录匹配规则测试（内存目录 + 假建号回调，不连库）。
 */
class AuthUserDirectoryTest {

    @Test
    void 工号精确匹配与未命中() {
        AuthUserDirectory dir = new AuthUserDirectory((u, n, d) -> 0L, true);
        dir.register(new AuthUserDirectory.UserRow(6970L, "SK6970", "鞠天飞", null));
        assertEquals(6970L, dir.resolveByJobNumber("SK6970"));
        assertNull(dir.resolveByJobNumber("SK8888"));
        assertNull(dir.resolveByJobNumber(null));
    }

    @Test
    void 姓名唯一命中() {
        AuthUserDirectory dir = new AuthUserDirectory((u, n, d) -> 0L, true);
        dir.register(new AuthUserDirectory.UserRow(101L, "SK0101", "杨建伟", "生产部"));
        assertEquals(101L, dir.resolveByName("杨建伟", null));
        // 已存在时不再建号
        assertEquals(101L, dir.resolveByName("杨建伟", "随便的部门"));
        assertTrue(dir.getCreatedUsernames().isEmpty());
    }

    @Test
    void 姓名重名取最小id并警告() {
        AuthUserDirectory dir = new AuthUserDirectory((u, n, d) -> 0L, true);
        dir.register(new AuthUserDirectory.UserRow(20L, "SK0020", "李小亲", "A部"));
        dir.register(new AuthUserDirectory.UserRow(10L, "SK0010", "李小亲", "B部"));
        assertEquals(10L, dir.resolveByName("李小亲", null));
        assertTrue(dir.getWarnings().stream().anyMatch(w -> w.contains("李小亲")));
    }

    @Test
    void 姓名未命中自动建号且续用SK序号() {
        AtomicInteger seq = new AtomicInteger();
        AuthUserDirectory dir = new AuthUserDirectory((username, name, dept) -> {
            assertTrue(username.matches("SK\\d+"));
            assertEquals("张三", name);
            assertEquals("IT部", dept);
            return 500L + seq.incrementAndGet();
        }, true);
        // 目录内已有 SK77777，新号应从 77778 顺延
        dir.register(new AuthUserDirectory.UserRow(77777L, "SK77777", "老员工", null));
        assertEquals(501L, dir.resolveByName("张三", "IT部"));
        Long again = dir.resolveByName("张三", "IT部");
        assertEquals(501L, again);
        assertTrue(dir.getCreatedUsernames().contains("SK77778 张三"));
    }

    @Test
    void 操作人文本优先工号() {
        AuthUserDirectory dir = new AuthUserDirectory((u, n, d) -> 0L, true);
        dir.register(new AuthUserDirectory.UserRow(544L, "SK544", "张艳喜", "四楼生产部"));
        // 工号命中：不依赖姓名解析
        assertEquals(544L, dir.resolveByOperatorLabel("四楼生产部 SK544张艳喜+盘点（廖）"));
    }

    @Test
    void 工号未命中回退姓名() {
        AuthUserDirectory dir = new AuthUserDirectory((u, n, d) -> 999L, true);
        dir.register(new AuthUserDirectory.UserRow(544L, "SK544", "张艳喜", null));
        // SK8888 不存在 → 按姓名 张艳喜 命中
        assertEquals(544L, dir.resolveByOperatorLabel("某部门 SK8888张艳喜"));
    }

    @Test
    void 关闭自动建号返回null并警告() {
        AuthUserDirectory dir = new AuthUserDirectory((u, n, d) -> 0L, false);
        assertNull(dir.resolveByName("不存在的人", null));
        assertTrue(dir.getWarnings().stream().anyMatch(w -> w.contains("不存在的人")));
    }

    @Test
    void 空姓名返回Null() {
        AuthUserDirectory dir = new AuthUserDirectory((u, n, d) -> 0L, true);
        assertNull(dir.resolveByName("", null));
        assertNull(dir.resolveByName(null, null));
        assertNull(dir.resolveByName("  ", null));
        assertEquals(List.of(), dir.getCreatedUsernames());
    }
}
