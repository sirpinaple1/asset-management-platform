package com.sk.asset.migration;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * M08 迁移执行结果（各阶段计数 + 自动创建账号清单 + 警告）。
 */
@Data
public class MigrationResult {

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private List<PhaseResult> phases = new ArrayList<>();

    /** 自动创建的 sys_user 账号（username 姓名） */
    private List<String> createdUsers = new ArrayList<>();

    /** 需要人工关注的警告（重名取小 id、未知状态、无匹配位置等），上限 200 条 */
    private List<String> warnings = new ArrayList<>();

    @Data
    public static class PhaseResult {
        private String phase;
        private int rows;
        private int inserted;
        private int updated;
        private int skipped;

        public PhaseResult(String phase) {
            this.phase = phase;
        }

        void inserted() {
            inserted++;
        }

        void updated() {
            updated++;
        }

        void skipped() {
            skipped++;
        }
    }

    void addWarning(String warning) {
        if (warnings.size() < 200) {
            warnings.add(warning);
        }
    }
}
