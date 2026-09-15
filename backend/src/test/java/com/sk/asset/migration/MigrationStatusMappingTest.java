package com.sk.asset.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 旧系统状态枚举 → 新系统状态映射测试。
 */
class MigrationStatusMappingTest {

    private final MigrationResult result = new MigrationResult();

    @Test
    void 资产状态映射() {
        assertEquals("IDLE", MigrationService.mapAssetStatus("Idle", "B1", result));
        assertEquals("IN_USE", MigrationService.mapAssetStatus("In Use", "B1", result));
        assertEquals("DISCARD", MigrationService.mapAssetStatus("Discard", "B1", result));
        assertEquals("PENDING_CONFIRM", MigrationService.mapAssetStatus("Receive Pending Confirm", "B1", result));
        assertEquals("IDLE", MigrationService.mapAssetStatus("未知状态", "B1", result));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("B1")));
    }

    @Test
    void 领用单状态映射() {
        assertEquals("APPROVED", MigrationService.mapReceiptStatus("Approved", "ARE1", result));
        assertEquals("PENDING", MigrationService.mapReceiptStatus("Pending Approval", "ARE1", result));
        assertEquals("REJECTED", MigrationService.mapReceiptStatus("Rejected", "ARE1", result));
        assertNull(MigrationService.mapReceiptStatus("未知", "ARE1", result));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("ARE1")));
    }

    @Test
    void 调拨单状态映射() {
        assertEquals("COMPLETED", MigrationService.mapTransferStatus("Completed", "ATR1", result));
        assertEquals("REJECTED", MigrationService.mapTransferStatus("Allocated and Rejected", "ATR1", result));
        assertEquals("CANCELLED", MigrationService.mapTransferStatus("Cancelled", "ATR1", result));
        assertNull(MigrationService.mapTransferStatus("未知", "ATR1", result));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("ATR1")));
    }
}
