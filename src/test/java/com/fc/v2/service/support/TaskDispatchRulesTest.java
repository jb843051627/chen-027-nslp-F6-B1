package com.fc.v2.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * 走表纯口径测试：窗口、届期、提前、可数日、记号格式、答话，一处一处照文对。
 */
public class TaskDispatchRulesTest {

    private static Date d(String s) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
        } catch (java.text.ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    // ------------------------------------------------------------------
    // 那一截：02:00 起算开，05:00 一到即收手
    // ------------------------------------------------------------------

    @Test
    public void window_openOnlyBetween02And05() {
        assertFalse(TaskDispatchRules.isOpen(d("2026-10-06 01:59:59")));
        assertTrue(TaskDispatchRules.isOpen(d("2026-10-06 02:00:00")));
        assertTrue(TaskDispatchRules.isOpen(d("2026-10-06 04:59:59")));
        assertFalse(TaskDispatchRules.isOpen(d("2026-10-06 05:00:00")));
        assertFalse(TaskDispatchRules.isOpen(d("2026-10-06 23:30:00")));
        assertFalse(TaskDispatchRules.isOpen(null));
    }

    // ------------------------------------------------------------------
    // 届期：走到这一刻就算到，恰好等于这一刻的也算
    // ------------------------------------------------------------------

    @Test
    public void due_equalCounts() {
        assertTrue(TaskDispatchRules.isDue(d("2026-10-06 03:00:00"), d("2026-10-06 03:00:00")));
        assertTrue(TaskDispatchRules.isDue(d("2026-10-05 23:59:59"), d("2026-10-06 03:00:00")));
        assertFalse(TaskDispatchRules.isDue(d("2026-10-06 03:00:01"), d("2026-10-06 03:00:00")));
        assertFalse(TaskDispatchRules.isDue(null, d("2026-10-06 03:00:00")));
        assertFalse(TaskDispatchRules.isDue(d("2026-10-06 03:00:00"), null));
    }

    // ------------------------------------------------------------------
    // 提前几日：空着不提前、零头不算、负数不提前
    // ------------------------------------------------------------------

    @Test
    public void earlyDays_fieldSpeaksForItself() {
        assertEquals(0, TaskDispatchRules.earlyDaysOf(null));
        assertEquals(0, TaskDispatchRules.earlyDaysOf(new BigDecimal("0")));
        assertEquals(0, TaskDispatchRules.earlyDaysOf(new BigDecimal("0.99")));
        assertEquals(0, TaskDispatchRules.earlyDaysOf(new BigDecimal("-2")));
        assertEquals(3, TaskDispatchRules.earlyDaysOf(new BigDecimal("3.75")));
        assertEquals(10, TaskDispatchRules.earlyDaysOf(new BigDecimal("10")));
    }

    // ------------------------------------------------------------------
    // 可数日：法定节假日不算、停办日（周六周日）不算
    // ------------------------------------------------------------------

    @Test
    public void countableDay_holidayAndSuspensionExcluded() {
        assertFalse(TaskDispatchRules.isCountableDay(LocalDate.of(2026, 10, 1)));   // 国庆
        assertFalse(TaskDispatchRules.isCountableDay(LocalDate.of(2026, 10, 7)));   // 国庆
        assertFalse(TaskDispatchRules.isCountableDay(LocalDate.of(2026, 2, 17)));   // 春节
        assertFalse(TaskDispatchRules.isCountableDay(LocalDate.of(2026, 10, 10)));  // 周六
        assertFalse(TaskDispatchRules.isCountableDay(LocalDate.of(2026, 10, 11)));  // 周日
        assertTrue(TaskDispatchRules.isCountableDay(LocalDate.of(2026, 10, 8)));    // 周四
        assertTrue(TaskDispatchRules.isCountableDay(LocalDate.of(2026, 10, 9)));    // 周五
        assertTrue(TaskDispatchRules.isCountableDay(LocalDate.of(2026, 9, 30)));    // 周三
    }

    @Test
    public void effectiveDue_walksBackOverHolidaysAndWeekends() {
        // 不许提前：就是届期那一刻
        assertEquals(d("2026-10-08 10:00:00"),
                TaskDispatchRules.effectiveDueAt(d("2026-10-08 10:00:00"), null));
        // 许提前 1 日：10.1–10.7 法定节假日 → 落在 9.30，时刻不动
        assertEquals(d("2026-09-30 10:00:00"),
                TaskDispatchRules.effectiveDueAt(d("2026-10-08 10:00:00"), new BigDecimal("1")));
        // 周一届期许提前 1 日：周日、周六停办 → 落在上周五
        assertEquals(d("2026-10-09 09:00:00"),
                TaskDispatchRules.effectiveDueAt(d("2026-10-12 09:00:00"), new BigDecimal("1")));
        // 届期空着：没有可办那一刻
        assertNull(TaskDispatchRules.effectiveDueAt(null, new BigDecimal("3")));
    }

    // ------------------------------------------------------------------
    // 挑不挑得中：只挑还搁着的
    // ------------------------------------------------------------------

    @Test
    public void pickable_onlyWaitingAndDue() {
        Date at = d("2026-10-06 03:00:00");
        Date past = d("2026-10-01 00:00:00");
        Date future = d("2026-12-01 00:00:00");
        assertTrue(TaskDispatchRules.pickable(0, 0, past, null, at));
        assertFalse(TaskDispatchRules.pickable(1, 0, past, null, at));   // 已发出不再碰
        assertFalse(TaskDispatchRules.pickable(2, 0, past, null, at));   // 发不成不再碰
        assertFalse(TaskDispatchRules.pickable(null, 0, past, null, at));
        assertFalse(TaskDispatchRules.pickable(0, 1, past, null, at));   // 已删除
        assertFalse(TaskDispatchRules.pickable(0, 0, future, null, at)); // 没届期
        assertFalse(TaskDispatchRules.pickable(0, 0, null, null, at));   // 没届期那一日
    }

    // ------------------------------------------------------------------
    // 先办哪一头：最届期的在前，同刻按主键
    // ------------------------------------------------------------------

    @Test
    public void pickOrder_mostDueFirst() {
        Date a = d("2026-10-01 00:00:00");
        Date b = d("2026-10-02 00:00:00");
        assertTrue(TaskDispatchRules.comparePickOrder(a, 1L, b, 2L) < 0);
        assertTrue(TaskDispatchRules.comparePickOrder(b, 1L, a, 2L) > 0);
        assertTrue(TaskDispatchRules.comparePickOrder(a, 1L, a, 2L) < 0);
        assertTrue(TaskDispatchRules.comparePickOrder(a, 2L, a, 1L) > 0);
        assertTrue(TaskDispatchRules.comparePickOrder(null, 1L, a, 2L) > 0);
    }

    // ------------------------------------------------------------------
    // 记号：发给谁、记要补句、失败原由
    // ------------------------------------------------------------------

    @Test
    public void recipient_onlyFromThisRow() {
        assertEquals("岗甲", TaskDispatchRules.recipientOf(" 岗甲 "));
        assertNull(TaskDispatchRules.recipientOf(null));
        assertNull(TaskDispatchRules.recipientOf("   "));
    }

    @Test
    public void sendNote_carriesMomentAndRecipient() {
        assertEquals("［已发出 2026-10-06 02:30:00 发给 岗甲］",
                TaskDispatchRules.sendNote(d("2026-10-06 02:30:00"), "岗甲"));
        assertEquals("原句［已发出 2026-10-06 02:30:00 发给 岗甲］",
                TaskDispatchRules.appendSendNote("原句", d("2026-10-06 02:30:00"), "岗甲"));
        assertEquals("［已发出 2026-10-06 02:30:00 发给 岗甲］",
                TaskDispatchRules.appendSendNote(null, d("2026-10-06 02:30:00"), "岗甲"));
    }

    @Test
    public void failNote_oneSentenceCapped() {
        assertEquals("发不成：行上没落着人名，不知发给谁",
                TaskDispatchRules.failNote("行上没落着人名，不知发给谁"));
        assertEquals("发不成：原由没留下", TaskDispatchRules.failNote(null));
        StringBuilder longReason = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            longReason.append("长");
        }
        assertEquals(TaskDispatchRules.REMARK_CAP, TaskDispatchRules.failNote(longReason.toString()).length());
    }

    // ------------------------------------------------------------------
    // 答话：只由三个数放出来
    // ------------------------------------------------------------------

    @Test
    public void reply_derivedFromNumbersOnly() {
        assertEquals("本轮未改动安排单", TaskDispatchRules.replyOf(0, 0, 0));
        assertEquals("本轮未改动安排单", TaskDispatchRules.replyOf(3, 0, 0));
        assertEquals("本轮已改动安排单：挑中3行、发出2行、没成1行",
                TaskDispatchRules.replyOf(3, 2, 1));
        assertFalse(TaskDispatchRules.modified(0, 0));
        assertTrue(TaskDispatchRules.modified(1, 0));
        assertTrue(TaskDispatchRules.modified(0, 1));
    }
}
