package com.sk.asset.migration;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 旧系统日志 content → diff JSON 解析（M08 迁移规则）。
 *
 * <p>已知格式（实测 1773 条日志）：</p>
 * <pre>
 *   【使用人】字段由【】变更为【谷仍山】;【使用部门】字段由【】变更为【IT部】;    （领用/变更/导入修改，多段）
 *   【规格型号】字段由【&lt;空&gt;】变更为【21.5英寸HDMI接口】;                        （&lt;空&gt; 原样保留）
 *   【资产编码】字段由 "SFBGKT0279" 变更为 "SKBGKT0279";                        （数据机器人，双引号）
 *   【资产编码】字段由 SFBGIT3244 变更为 SKBGDN241;                            （数据机器人Pro，裸值）
 *   状态由 "闲置" 变更为 "报废"                                                （清理，字段固定为"状态"）
 *   从管理员"丘碧玲"调出，将资产…                                             （资产调拨/盘点处理，自由文本 → 无 diff）
 * </pre>
 *
 * <p>产出结构：{@code [{"field":"使用人","before":"","after":"谷仍山"}]}；无可解析片段返回 null。</p>
 */
public final class LogContentParser {

    private LogContentParser() {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 【字段】字段由【旧】变更为【新】 */
    private static final Pattern P_BRACKET = Pattern.compile(
            "【([^】]+)】\\s*字段由\\s*【([^】]*)】\\s*变更为\\s*【([^】]*)】");
    /** 【字段】字段由 "旧" 变更为 "新" */
    private static final Pattern P_QUOTED = Pattern.compile(
            "【([^】]+)】\\s*字段由\\s*\"([^\"]*)\"\\s*变更为\\s*\"([^\"]*)\"");
    /** 【字段】字段由 旧 变更为 新;（裸值，值内不含【】"与分号） */
    private static final Pattern P_BARE = Pattern.compile(
            "【([^】]+)】\\s*字段由\\s*([^【】\";；]+?)\\s*变更为\\s*([^【】\";；]+?)\\s*[;；]");
    /** 状态由 "旧" 变更为 "新"（清理格式，字段固定为"状态"） */
    private static final Pattern P_STATUS = Pattern.compile(
            "状态由\\s*\"([^\"]*)\"\\s*变更为\\s*\"([^\"]*)\"");

    public static String parseDiffJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        List<Match> matches = new ArrayList<>();
        collect(matches, P_BRACKET.matcher(content), false);
        collect(matches, P_QUOTED.matcher(content), false);
        collect(matches, P_BARE.matcher(content), false);
        // 清理格式无字段名占位，固定补"状态"字段
        collect(matches, P_STATUS.matcher(content), true);
        if (matches.isEmpty()) {
            return null;
        }
        matches.sort(Comparator.comparingInt(m -> m.start));
        try {
            List<Map<String, String>> diffs = new ArrayList<>();
            for (Match m : matches) {
                Map<String, String> d = new LinkedHashMap<>();
                d.put("field", m.field);
                d.put("before", m.before);
                d.put("after", m.after);
                diffs.add(d);
            }
            return MAPPER.writeValueAsString(diffs);
        } catch (Exception e) {
            return null;
        }
    }

    private static void collect(List<Match> out, Matcher matcher, boolean fixedField) {
        while (matcher.find()) {
            if (fixedField) {
                out.add(new Match(matcher.start(), "状态", matcher.group(1), matcher.group(2)));
            } else {
                out.add(new Match(matcher.start(), matcher.group(1), matcher.group(2), matcher.group(3)));
            }
        }
    }

    private record Match(int start, String field, String before, String after) {
    }
}
