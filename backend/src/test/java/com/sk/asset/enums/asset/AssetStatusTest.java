package com.sk.asset.enums.asset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 资产状态机全路径覆盖（M03 完成标准：单测覆盖所有合法流转）
 */
class AssetStatusTest {

    @Test
    void idle_canTransitionToInUseDiscardAndPendingConfirm() {
        assertTrue(AssetStatus.IDLE.canTransitionTo(AssetStatus.IN_USE));
        assertTrue(AssetStatus.IDLE.canTransitionTo(AssetStatus.DISCARD));
        assertTrue(AssetStatus.IDLE.canTransitionTo(AssetStatus.PENDING_CONFIRM));
        assertFalse(AssetStatus.IDLE.canTransitionTo(AssetStatus.IDLE));
    }

    @Test
    void inUse_canTransitionToIdleDiscardAndPendingConfirm() {
        assertTrue(AssetStatus.IN_USE.canTransitionTo(AssetStatus.IDLE));
        assertTrue(AssetStatus.IN_USE.canTransitionTo(AssetStatus.DISCARD));
        assertTrue(AssetStatus.IN_USE.canTransitionTo(AssetStatus.PENDING_CONFIRM));
        assertFalse(AssetStatus.IN_USE.canTransitionTo(AssetStatus.IN_USE));
    }

    @Test
    void pendingConfirm_canOnlyGoBackToInUseOrIdle() {
        assertTrue(AssetStatus.PENDING_CONFIRM.canTransitionTo(AssetStatus.IN_USE));
        assertTrue(AssetStatus.PENDING_CONFIRM.canTransitionTo(AssetStatus.IDLE));
        assertFalse(AssetStatus.PENDING_CONFIRM.canTransitionTo(AssetStatus.DISCARD));
        assertFalse(AssetStatus.PENDING_CONFIRM.canTransitionTo(AssetStatus.PENDING_CONFIRM));
    }

    @Test
    void discard_isTerminalState() {
        for (AssetStatus target : AssetStatus.values()) {
            assertFalse(AssetStatus.DISCARD.canTransitionTo(target),
                    "报废为终态，不允许流转到 " + target);
        }
    }

    @Test
    void of_shouldParseValidName() {
        assertEquals(AssetStatus.IDLE, AssetStatus.of("IDLE"));
        assertEquals(AssetStatus.IN_USE, AssetStatus.of("IN_USE"));
        assertEquals(AssetStatus.PENDING_CONFIRM, AssetStatus.of("PENDING_CONFIRM"));
        assertEquals(AssetStatus.DISCARD, AssetStatus.of("DISCARD"));
    }

    @Test
    void of_shouldRejectInvalidName() {
        assertThrows(IllegalArgumentException.class, () -> AssetStatus.of("idle"));
        assertThrows(IllegalArgumentException.class, () -> AssetStatus.of("FOO"));
        assertThrows(IllegalArgumentException.class, () -> AssetStatus.of(""));
        assertThrows(IllegalArgumentException.class, () -> AssetStatus.of(null));
    }

    @Test
    void getLabel_shouldReturnChineseLabel() {
        assertEquals("闲置", AssetStatus.IDLE.getLabel());
        assertEquals("在用", AssetStatus.IN_USE.getLabel());
        assertEquals("待确认", AssetStatus.PENDING_CONFIRM.getLabel());
        assertEquals("报废", AssetStatus.DISCARD.getLabel());
    }
}
