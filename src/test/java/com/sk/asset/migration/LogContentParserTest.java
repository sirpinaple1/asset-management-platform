package com.sk.asset.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志 content → diff JSON 解析规则测试（样本取自旧系统真实数据）。
 */
class LogContentParserTest {

    @Test
    void 括号格式多段解析() {
        String content = "【使用人】字段由【】变更为【谷仍山】;【使用部门】字段由【】变更为【IT部】;";
        String json = LogContentParser.parseDiffJson(content);
        assertTrue(json.contains("\"field\":\"使用人\""));
        assertTrue(json.contains("\"before\":\"\""));
        assertTrue(json.contains("\"after\":\"谷仍山\""));
        assertTrue(json.contains("\"field\":\"使用部门\""));
        assertTrue(json.contains("\"after\":\"IT部\""));
    }

    @Test
    void 空占位符原样保留() {
        String content = "【规格型号】字段由【<空>】变更为【21.5英寸HDMI接口】;";
        String json = LogContentParser.parseDiffJson(content);
        assertTrue(json.contains("\"before\":\"<空>\""));
        assertTrue(json.contains("\"after\":\"21.5英寸HDMI接口\""));
    }

    @Test
    void 机器人双引号格式() {
        String content = "【资产编码更改_拷贝 单据 (202607060001) ，未命名的自动化 机器人】 将 【资产编码】 字段由 \"SFBGKT0279\" 变更为 \"SKBGKT0279\";";
        String json = LogContentParser.parseDiffJson(content);
        assertTrue(json.contains("\"field\":\"资产编码\""));
        assertTrue(json.contains("\"before\":\"SFBGKT0279\""));
        assertTrue(json.contains("\"after\":\"SKBGKT0279\""));
        // 前缀说明段【资产编码更改_拷贝 …】不应被误解析为字段
        assertEquals(1, json.split("\"field\"").length - 1);
    }

    @Test
    void 机器人裸值格式() {
        String content = "【资产编码更改 (202605210001)  资产编码更新 机器人】 将 【资产编码】 字段由 SFBGIT3244 变更为 SKBGDN241;";
        String json = LogContentParser.parseDiffJson(content);
        assertTrue(json.contains("\"before\":\"SFBGIT3244\""));
        assertTrue(json.contains("\"after\":\"SKBGDN241\""));
        assertEquals(1, json.split("\"field\"").length - 1);
    }

    @Test
    void 清理格式状态字段() {
        String content = "资产\"荣耀笔记本\"被清理处置; 状态由 \"闲置\" 变更为 \"报废\"";
        String json = LogContentParser.parseDiffJson(content);
        assertTrue(json.contains("\"field\":\"状态\""));
        assertTrue(json.contains("\"before\":\"闲置\""));
        assertTrue(json.contains("\"after\":\"报废\""));
    }

    @Test
    void 自由文本无diff() {
        assertNull(LogContentParser.parseDiffJson("从管理员\"丘碧玲\"调出，将资产从\"森科五金(深圳)有限公司\"调入到\"森科五金(深圳)有限公司\"，\"白救通\"名下"));
        assertNull(LogContentParser.parseDiffJson("盘点单号: AIN202606100001（未盘）"));
    }

    @Test
    void 空内容无diff() {
        assertNull(LogContentParser.parseDiffJson(null));
        assertNull(LogContentParser.parseDiffJson("  "));
    }
}
