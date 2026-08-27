package com.sk.asset.migration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 旧系统操作人文本解析（M08 迁移规则）。
 *
 * <p>已知格式（实测 1773 条日志 + 资产 Creator 列）：</p>
 * <pre>
 *   "SK6970 鞠天飞 超管"                    → 工号 SK6970，姓名 鞠天飞
 *   "四楼生产部 SK544张艳喜+盘点（廖）"       → 工号 SK544，姓名 张艳喜（+盘点后缀剥离）
 *   "IT SK7237周锦玲"                       → 工号 SK7237，姓名 周锦玲（工号与姓名粘连）
 *   "自动化部 SK9628 李苗+盘点"              → 工号 SK9628，姓名 李苗
 *   "总办 黄冬梅+盘点"                       → 无工号，姓名 黄冬梅
 *   "设备外维 杨建伟"                        → 无工号，姓名 杨建伟
 *   "鞠天飞"                                → 无工号，姓名 鞠天飞
 * </pre>
 */
public final class OperatorTextParser {

    private OperatorTextParser() {
    }

    private static final Pattern JOB_NO = Pattern.compile("SK(\\d+)");
    private static final Pattern INVENTORY_SUFFIX = Pattern.compile("\\+盘点.*$");
    private static final Pattern TRAILING_PAREN = Pattern.compile("[（(][^（）()]*[）)]\\s*$");
    private static final Pattern CJK_RUN = Pattern.compile("[\\u4e00-\\u9fa5·]{2,4}");

    /** 剥离 "+盘点…" 后缀与尾部括号补注（如 "（廖）"） */
    public static String stripDecorations(String label) {
        if (label == null) {
            return "";
        }
        String s = label.trim();
        s = INVENTORY_SUFFIX.matcher(s).replaceAll("");
        s = TRAILING_PAREN.matcher(s).replaceAll("");
        return s.trim();
    }

    /** 提取工号（含 SK 前缀，如 SK544）；无工号返回 null */
    public static String extractJobNo(String label) {
        String cleaned = stripDecorations(label);
        Matcher m = JOB_NO.matcher(cleaned);
        return m.find() ? "SK" + m.group(1) : null;
    }

    /** 提取操作人姓名；无法提取返回 null */
    public static String extractName(String label) {
        String cleaned = stripDecorations(label);
        if (cleaned.isEmpty()) {
            return null;
        }
        Matcher job = JOB_NO.matcher(cleaned);
        if (job.find()) {
            String rest = cleaned.substring(job.end());
            // 工号与姓名粘连（如 "SK544张艳喜"）：取紧随其后的连续汉字段
            if (!rest.isEmpty() && isCjk(rest.charAt(0))) {
                Matcher name = CJK_RUN.matcher(rest);
                if (name.find() && name.start() == 0) {
                    return name.group();
                }
                // 粘连但超长（>4 字）：截前 4 字（人名上限，防误吞部门名）
                StringBuilder sb = new StringBuilder();
                for (char c : rest.toCharArray()) {
                    if (isCjk(c) && sb.length() < 4) {
                        sb.append(c);
                    } else {
                        break;
                    }
                }
                return sb.isEmpty() ? null : sb.toString();
            }
            // 空格分隔：取工号后第一个非空片段
            for (String token : rest.split("\\s+")) {
                if (!token.isBlank()) {
                    return trimToName(token);
                }
            }
            return null;
        }
        // 无工号：取最后一个空格分隔片段（前缀为部门名，如 "设备外维 杨建伟"）
        String[] tokens = cleaned.split("\\s+");
        return tokens.length == 0 ? null : trimToName(tokens[tokens.length - 1]);
    }

    private static String trimToName(String token) {
        String t = token.replaceAll("[+＋].*$", "").trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean isCjk(char c) {
        return c >= 0x4e00 && c <= 0x9fa5;
    }
}
