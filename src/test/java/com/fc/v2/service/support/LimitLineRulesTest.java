package com.fc.v2.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.TNslpLimitLine;

/**
 * 限值线判层纯口径测试：四句贴层、精度对齐、量程、脏配置、在位窗口、并列裁决。
 */
public class LimitLineRulesTest {

    private static Date t(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TNslpLimitLine line(String code, Object th1, Object th2, Object th3,
                                       String start, String end, Integer priority, Integer status) {
        TNslpLimitLine r = new TNslpLimitLine();
        r.setId((long) (code == null ? 1 : code.hashCode() & 0x7fffffff));
        r.setRuleCode(code);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1.toString()));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2.toString()));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3.toString()));
        r.setEffStart(start == null ? null : t(start));
        r.setEffEnd(end == null ? null : t(end));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    // 题给验收：45/55/70 → 44.0 第1层，54.9 第2层，55.0 与 55.1 第3层，70.0 第4层
    @Test
    public void fourSentencesBoundaries() {
        TNslpLimitLine r = line("L45", 45, 55, 70, "2020-01-01 00:00:00", null, 1, 0);
        assertEquals(1, LimitLineRules.levelOf(r, new BigDecimal("44.0")));
        assertEquals(1, LimitLineRules.levelOf(r, new BigDecimal("30.0")));
        assertEquals(2, LimitLineRules.levelOf(r, new BigDecimal("54.9")));
        assertEquals(3, LimitLineRules.levelOf(r, new BigDecimal("55.0")));
        assertEquals(3, LimitLineRules.levelOf(r, new BigDecimal("55.1")));
        // 69.94 对齐 69.9，仍不到 70.0 → 第3层；69.999 对齐 70.0（逢半进一）→ 第4层
        assertEquals(3, LimitLineRules.levelOf(r, new BigDecimal("69.94")));
        assertEquals(4, LimitLineRules.levelOf(r, new BigDecimal("69.999")));
        assertEquals(4, LimitLineRules.levelOf(r, new BigDecimal("70.0")));
        assertEquals(4, LimitLineRules.levelOf(r, new BigDecimal("120.0")));
    }

    // 前年事故：取数处进位、比数处不进位 → 54.996 对 55.0 必须同按 55.0 贴，出第3层
    @Test
    public void alignHalfUpBothSides() {
        TNslpLimitLine r = line("L45", "45.0", "54.996", 70, "2020-01-01 00:00:00", null, 1, 0);
        // 定值对齐后 th2=55.0，读数 54.996 对齐也是 55.0，含等号收紧档 → 第3层
        assertEquals(3, LimitLineRules.levelOf(r, new BigDecimal("54.996")));
        assertEquals(new BigDecimal("55.0"), LimitLineRules.alignOne(new BigDecimal("54.95")));
        // 逢半进一
        assertEquals(new BigDecimal("54.9"), LimitLineRules.alignOne(new BigDecimal("54.94")));
    }

    @Test
    public void outOfRangeNoLevel() {
        TNslpLimitLine r = line("L45", 45, 55, 70, "2020-01-01 00:00:00", null, 1, 0);
        assertEquals(0, LimitLineRules.levelOf(r, new BigDecimal("29.9")));
        assertEquals(0, LimitLineRules.levelOf(r, new BigDecimal("120.1")));
        // 对齐后越界也算越界：120.04 对齐 120.0 仍在界；120.05 对齐 120.1 越界
        assertEquals(4, LimitLineRules.levelOf(r, new BigDecimal("120.04")));
        assertEquals(0, LimitLineRules.levelOf(r, new BigDecimal("120.05")));
    }

    @Test
    public void dirtyThresholdsRejectedWithSpecificMessages() {
        // 缺档：要点清缺哪一格
        String miss = LimitLineRules.checkThresholds(new BigDecimal("45"), null, new BigDecimal("70"));
        assertNotNull(miss);
        assertTrue(miss.contains("够线限值"), miss);
        // 后一档没大过前一档：点清哪一格
        String notBigger = LimitLineRules.checkThresholds(new BigDecimal("45"), new BigDecimal("45"), new BigDecimal("70"));
        assertNotNull(notBigger);
        assertTrue(notBigger.contains("够线限值") && notBigger.contains("没有大过"), notBigger);
        // 封顶高过 120：脏配置剔掉
        String overCap = LimitLineRules.checkThresholds(new BigDecimal("45"), new BigDecimal("55"), new BigDecimal("120.1"));
        assertNotNull(overCap);
        assertTrue(overCap.contains("封顶限值") && overCap.contains("120"), overCap);
        // 干净
        assertNull(LimitLineRules.checkThresholds(new BigDecimal("45"), new BigDecimal("55"), new BigDecimal("70")));
        // 不许只写「数值不对」
        assertFalse((miss + notBigger + overCap).contains("数值不对"));
    }

    @Test
    public void activeWindowHeadInclusiveTailExclusive() {
        TNslpLimitLine r = line("L", 45, 55, 70, "2024-01-01 00:00:00", "2025-01-01 00:00:00", 1, 0);
        assertFalse(LimitLineRules.activeAt(r, t("2023-12-31 23:59:59")));
        // 头含：起用那刻在位
        assertTrue(LimitLineRules.activeAt(r, t("2024-01-01 00:00:00")));
        // 尾不含：交班那刻已不在位
        assertFalse(LimitLineRules.activeAt(r, t("2025-01-01 00:00:00")));
        assertTrue(LimitLineRules.activeAt(r, t("2024-12-31 23:59:59")));
        // 撤下线不在位
        r.setStatus(1);
        assertFalse(LimitLineRules.activeAt(r, t("2024-06-01 00:00:00")));
        r.setStatus(0);
        // 交班日留空：一直管下去
        TNslpLimitLine forever = line("L", 45, 55, 70, "2024-01-01 00:00:00", null, 1, 0);
        assertTrue(LimitLineRules.activeAt(forever, t("2099-06-01 00:00:00")));
    }

    @Test
    public void winnerPriorityThenCode() {
        TNslpLimitLine a = line("AAAA", 45, 55, 70, "2024-01-01 00:00:00", null, 5, 0);
        TNslpLimitLine b = line("BBBB", 45, 55, 70, "2024-01-01 00:00:00", null, 5, 0);
        TNslpLimitLine c = line("CCCC", 45, 55, 70, "2024-01-01 00:00:00", null, 9, 0);
        List<TNslpLimitLine> lines = new ArrayList<TNslpLimitLine>(Arrays.asList(a, b, c));
        java.util.Collections.sort(lines, LimitLineRules.winnerOrder());
        // 交班次序大的先开口
        assertEquals("CCCC", lines.get(0).getRuleCode());
        // 次序并列，线代号字面靠后的那条说了算
        lines = new ArrayList<TNslpLimitLine>(Arrays.asList(a, b));
        java.util.Collections.sort(lines, LimitLineRules.winnerOrder());
        assertEquals("BBBB", lines.get(0).getRuleCode());
    }

    @Test
    public void answerWords() {
        assertEquals("第1层", LimitLineRules.answerOf(1));
        assertEquals("第4层", LimitLineRules.answerOf(4));
        assertEquals("不归层", LimitLineRules.answerOf(0));
    }
}
