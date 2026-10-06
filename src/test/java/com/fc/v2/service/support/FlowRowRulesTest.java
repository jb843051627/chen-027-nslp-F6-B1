package com.fc.v2.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 核收纯口径测试：顶的认定、点位码长规范、两种切法、退回理由的固定次序、
 * 满两个年度的线。
 */
public class FlowRowRulesTest {

    private static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static Date t(String s) {
        try {
            return F.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    // ------------------------------------------------------------------
    // 顶的认定：只认抬头那一格，空/负/离谱都按 300 认
    // ------------------------------------------------------------------

    @Test
    public void capRecognition() {
        assertEquals(300, FlowRowRules.recognizeCap(null));
        assertEquals(300, FlowRowRules.recognizeCap(Integer.valueOf(0)));
        assertEquals(300, FlowRowRules.recognizeCap(Integer.valueOf(-8)));
        // 离谱的大数当没填
        assertEquals(300, FlowRowRules.recognizeCap(Integer.valueOf(FlowRowRules.CAP_ABSURD + 1)));
        assertEquals(300, FlowRowRules.recognizeCap(Integer.valueOf(Integer.MAX_VALUE)));
        // 压线不算离谱，照填的认
        assertEquals(FlowRowRules.CAP_ABSURD,
                FlowRowRules.recognizeCap(Integer.valueOf(FlowRowRules.CAP_ABSURD)));
        assertEquals(150, FlowRowRules.recognizeCap(Integer.valueOf(150)));
        assertEquals(1, FlowRowRules.recognizeCap(Integer.valueOf(1)));
    }

    // ------------------------------------------------------------------
    // 点位码长规范：不够算对不上，超出算违规
    // ------------------------------------------------------------------

    @Test
    public void codeLengthSpec() {
        assertTrue(FlowRowRules.codeBlank(null));
        assertTrue(FlowRowRules.codeBlank("   "));
        assertFalse(FlowRowRules.codeBlank("PT0001"));

        assertTrue(FlowRowRules.codeTooShort("PT01"));
        assertTrue(FlowRowRules.codeTooShort("12345"));
        assertFalse(FlowRowRules.codeTooShort("PT0001"));
        assertFalse(FlowRowRules.codeTooShort(null));

        String len20 = "PT000000000000000001";
        assertEquals(20, len20.length());
        assertFalse(FlowRowRules.codeTooLong(len20));
        assertTrue(FlowRowRules.codeTooLong(len20 + "X"));
        assertFalse(FlowRowRules.codeTooLong(null));
    }

    // ------------------------------------------------------------------
    // 切法归一：一拨只许一种
    // ------------------------------------------------------------------

    @Test
    public void parseKindNormalize() {
        assertEquals(FlowRowRules.PARSE_DELIMITER, FlowRowRules.normalizeParseKind("delimiter"));
        assertEquals(FlowRowRules.PARSE_DELIMITER, FlowRowRules.normalizeParseKind(" DELIMITER "));
        assertEquals(FlowRowRules.PARSE_DELIMITER, FlowRowRules.normalizeParseKind("分隔符"));
        assertEquals(FlowRowRules.PARSE_FIXED, FlowRowRules.normalizeParseKind("fixed"));
        assertEquals(FlowRowRules.PARSE_FIXED, FlowRowRules.normalizeParseKind("定宽"));
        // 第三种切法、没报切法，一律归空
        assertNull(FlowRowRules.normalizeParseKind("ROT13"));
        assertNull(FlowRowRules.normalizeParseKind(""));
        assertNull(FlowRowRules.normalizeParseKind(null));
    }

    // ------------------------------------------------------------------
    // 报文解析：切得动回读数串，切不动回 null
    // ------------------------------------------------------------------

    @Test
    public void parseByDelimiter() {
        List<BigDecimal> v = FlowRowRules.parsePayload("55.0,56.1", "DELIMITER", null);
        assertEquals(2, v.size());
        assertEquals(bd("55.0"), v.get(0));
        // 中英文逗号、分号、顿号、竖线、空白都认
        v = FlowRowRules.parsePayload("55，56；57、58|59 60", "分隔符", null);
        assertEquals(6, v.size());
        assertEquals(bd("60"), v.get(5));
        // 负数切得出来，负不负由定去处那一步判
        v = FlowRowRules.parsePayload("55.0,-3", "DELIMITER", null);
        assertEquals(bd("-3"), v.get(1));
        // 切不动：空串、没报切法、夹了不是数的段
        assertNull(FlowRowRules.parsePayload("", "DELIMITER", null));
        assertNull(FlowRowRules.parsePayload(null, "DELIMITER", null));
        assertNull(FlowRowRules.parsePayload("55.0", null, null));
        assertNull(FlowRowRules.parsePayload("55.0,abc", "DELIMITER", null));
    }

    @Test
    public void parseByFixedWidth() {
        List<BigDecimal> v = FlowRowRules.parsePayload("5560", "FIXED", Integer.valueOf(2));
        assertEquals(2, v.size());
        assertEquals(bd("55"), v.get(0));
        assertEquals(bd("60"), v.get(1));
        // 宽度缺了、宽度为零、切完剩尾巴、段里不是数，都算切不动
        assertNull(FlowRowRules.parsePayload("5560", "FIXED", null));
        assertNull(FlowRowRules.parsePayload("5560", "FIXED", Integer.valueOf(0)));
        assertNull(FlowRowRules.parsePayload("556", "FIXED", Integer.valueOf(2)));
        assertNull(FlowRowRules.parsePayload("55ab", "FIXED", Integer.valueOf(2)));
    }

    // ------------------------------------------------------------------
    // 定去处：次序固定，一条都不沾才收下
    // ------------------------------------------------------------------

    @Test
    public void decideReturnOrder() {
        List<BigDecimal> ok = Arrays.asList(bd("55"));

        // 干干净净：收下（回 null）
        assertNull(FlowRowRules.decideReturn("PT0001", bd("3"), bd("2"), true, 10, ok));

        // 点位码没填压过一切（哪怕读数也是 0）
        assertEquals(FlowRowRules.REASON_CODE_BLANK,
                FlowRowRules.decideReturn(" ", bd("0"), bd("0"), false, null, ok));
        // 码长超出规范
        assertEquals(FlowRowRules.REASON_CODE_TOO_LONG,
                FlowRowRules.decideReturn("PT000000000000000000001", bd("1"), bd("1"), false, null, ok));
        // 两数没一起到：只到一个不行，两个都没到也不行
        assertEquals(FlowRowRules.REASON_NUM_INCOMPLETE,
                FlowRowRules.decideReturn("PT0001", bd("3"), null, true, 10, ok));
        assertEquals(FlowRowRules.REASON_NUM_INCOMPLETE,
                FlowRowRules.decideReturn("PT0001", null, bd("3"), true, 10, ok));
        assertEquals(FlowRowRules.REASON_NUM_INCOMPLETE,
                FlowRowRules.decideReturn("PT0001", null, null, true, 10, ok));
        // 读数为负：次数为负、报文里切出负读数，都算
        assertEquals(FlowRowRules.REASON_NEGATIVE,
                FlowRowRules.decideReturn("PT0001", bd("-1"), bd("0"), true, 10, ok));
        assertEquals(FlowRowRules.REASON_NEGATIVE,
                FlowRowRules.decideReturn("PT0001", bd("2"), bd("1"), true, 10,
                        Arrays.asList(bd("55"), bd("-3"))));
        // 读数次数为 0
        assertEquals(FlowRowRules.REASON_ZERO,
                FlowRowRules.decideReturn("PT0001", bd("0"), bd("0"), true, 10, ok));
        // 点位码对不上（码长不够的也走这条）
        assertEquals(FlowRowRules.REASON_CODE_UNMATCHED,
                FlowRowRules.decideReturn("PT9999", bd("3"), bd("1"), false, null, ok));
        // 读数次数高过核定上限，理由里带上限值
        String over = FlowRowRules.decideReturn("PT0001", bd("11"), bd("1"), true, 10, ok);
        assertTrue(over.startsWith(FlowRowRules.REASON_OVER_CAP));
        assertTrue(over.contains("10"));
        // 压平上限不算高过
        assertNull(FlowRowRules.decideReturn("PT0001", bd("10"), bd("1"), true, 10, ok));
        // 其余都干净但报文切不动
        assertEquals(FlowRowRules.REASON_PARSE_FAIL,
                FlowRowRules.decideReturn("PT0001", bd("3"), bd("1"), true, 10, null));
    }

    // ------------------------------------------------------------------
    // 去处三种写法与满两个年度的线
    // ------------------------------------------------------------------

    @Test
    public void statusAndTwoYearLine() {
        assertTrue(FlowRowRules.validStatus(Integer.valueOf(0)));
        assertTrue(FlowRowRules.validStatus(Integer.valueOf(1)));
        assertTrue(FlowRowRules.validStatus(Integer.valueOf(2)));
        assertFalse(FlowRowRules.validStatus(Integer.valueOf(3)));
        assertFalse(FlowRowRules.validStatus(null));

        assertEquals("未勾", FlowRowRules.statusText(Integer.valueOf(0)));
        assertEquals("收下", FlowRowRules.statusText(Integer.valueOf(1)));
        assertEquals("退回", FlowRowRules.statusText(Integer.valueOf(2)));
        assertEquals("写法不明", FlowRowRules.statusText(Integer.valueOf(9)));

        // 满两个年度：2026-10-04 往前推两个整年，正压 2024-10-04 那条线
        assertEquals(t("2024-10-04 00:00:00"), FlowRowRules.twoYearsBefore(t("2026-10-04 00:00:00")));
    }
}
