package com.sk.asset.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 操作人文本解析规则测试（样本取自旧系统真实数据）。
 */
class OperatorTextParserTest {

    @Test
    void 工号与姓名空格分隔() {
        assertEquals("SK6970", OperatorTextParser.extractJobNo("SK6970 鞠天飞 超管"));
        assertEquals("鞠天飞", OperatorTextParser.extractName("SK6970 鞠天飞 超管"));
    }

    @Test
    void 部门前缀与工号姓名粘连() {
        assertEquals("SK544", OperatorTextParser.extractJobNo("四楼生产部 SK544张艳喜+盘点（廖）"));
        assertEquals("张艳喜", OperatorTextParser.extractName("四楼生产部 SK544张艳喜+盘点（廖）"));
    }

    @Test
    void 盘点后缀剥离() {
        assertEquals("SK9628", OperatorTextParser.extractJobNo("自动化部 SK9628 李苗+盘点"));
        assertEquals("李苗", OperatorTextParser.extractName("自动化部 SK9628 李苗+盘点"));
    }

    @Test
    void 工号姓名无空格粘连() {
        assertEquals("SK7237", OperatorTextParser.extractJobNo("IT SK7237周锦玲"));
        assertEquals("周锦玲", OperatorTextParser.extractName("IT SK7237周锦玲"));
    }

    @Test
    void 无工号取末段姓名() {
        assertNull(OperatorTextParser.extractJobNo("设备外维 杨建伟"));
        assertEquals("杨建伟", OperatorTextParser.extractName("设备外维 杨建伟"));
        assertEquals("黄冬梅", OperatorTextParser.extractName("总办 黄冬梅+盘点"));
    }

    @Test
    void 纯姓名() {
        assertEquals("鞠天飞", OperatorTextParser.extractName("鞠天飞"));
        assertNull(OperatorTextParser.extractJobNo("鞠天飞"));
    }

    @Test
    void 空文本() {
        assertEquals("", OperatorTextParser.stripDecorations(null));
        assertNull(OperatorTextParser.extractJobNo(""));
        assertNull(OperatorTextParser.extractName(""));
    }

    @Test
    void 括号补注与盘点后缀同时存在() {
        assertEquals("SK7813", OperatorTextParser.extractJobNo("PMC部 SK7813周旭欢+盘点（廖）"));
        assertEquals("周旭欢", OperatorTextParser.extractName("PMC部 SK7813周旭欢+盘点（廖）"));
    }
}
