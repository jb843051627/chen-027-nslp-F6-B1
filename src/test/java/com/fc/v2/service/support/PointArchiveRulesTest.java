package com.fc.v2.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 建档五道筛与数量唯一轧法纯口径测试。
 */
public class PointArchiveRulesTest {

    @Test
    public void lackIsOnlyNeedMinusGot() {
        // 30 配 12 得 18；20 配 7 得 13；两数一样得 0
        assertEquals(18, PointArchiveRules.lack(30, 12));
        assertEquals(13, PointArchiveRules.lack(20, 7));
        assertEquals(0, PointArchiveRules.lack(9, 9));
    }

    @Test
    public void quantityNamesEachBadColumn() {
        // 干净
        assertTrue(PointArchiveRules.quantityErrors("30", "12").isEmpty());
        assertTrue(PointArchiveRules.quantityErrors("20", null).isEmpty()); // 达标空着按 0

        List<String> blankNeed = PointArchiveRules.quantityErrors(null, "1");
        assertEquals(1, blankNeed.size());
        assertTrue(blankNeed.get(0).contains("核定应测次数"));
        assertTrue(blankNeed.get(0).contains("没填"));

        List<String> notInt = PointArchiveRules.quantityErrors("12.5", "1");
        assertTrue(notInt.get(0).contains("核定应测次数"));
        assertTrue(notInt.get(0).contains("整数"));

        List<String> neg = PointArchiveRules.quantityErrors("-3", "0");
        assertTrue(neg.get(0).contains("负数"));

        // 核定比达标小 / 达标反超核定，同一道话都点到
        List<String> over = PointArchiveRules.quantityErrors("5", "9");
        assertEquals(1, over.size());
        assertTrue(over.get(0).contains("比已核验达标的次数"));
        assertTrue(over.get(0).contains("反超"));

        // 多毛病一次说全，不许并成「数据不对」
        List<String> many = PointArchiveRules.quantityErrors("abc", "-2");
        assertTrue(many.size() >= 2);
    }

    @Test
    public void codeScreen() {
        assertTrue(PointArchiveRules.codeBlank("  "));
        assertFalse(PointArchiveRules.codeBlank("PT0001"));
        // 另有同码在册 → 拒
        String dup = PointArchiveRules.screenCode(false, true);
        assertTrue(dup.contains("拒掉"));
        // 操作对象正是自己（外部已摘自身，传 false）→ 放过
        assertNull(PointArchiveRules.screenCode(false, false));
        // 空码先点空
        assertTrue(PointArchiveRules.screenCode(true, false).contains("空着"));
    }

    @Test
    public void zoneScreenAndNameAlign() {
        assertNull(PointArchiveRules.screenZone(zone(0)));
        String noZone = PointArchiveRules.screenZone(null);
        assertTrue(noZone.contains("区册代号这一栏对不上"));
        String withdrawn = PointArchiveRules.screenZone(zone(1));
        assertTrue(withdrawn.contains("已并走撤出"));
        assertTrue(withdrawn.contains("不再收新行"));

        // 冗余全称以区册为准
        assertEquals("册上全称", PointArchiveRules.alignZoneName(zone(0, "册上全称"), "行上旧全称"));
        assertTrue(PointArchiveRules.zoneNameMismatch(zone(0, "册上全称"), "行上旧全称"));
        assertFalse(PointArchiveRules.zoneNameMismatch(zone(0, "同一名"), "同一名"));
    }

    @Test
    public void slotAndAttribution() {
        assertTrue(PointArchiveRules.screenSlot(true).contains("第二行开不出来"));
        assertNull(PointArchiveRules.screenSlot(false));
        assertTrue(PointArchiveRules.screenCreator(null).contains("落笔人"));
        assertTrue(PointArchiveRules.screenUpdater("").contains("落笔人"));
        assertNull(PointArchiveRules.screenCreator("建档岗甲"));
    }

    private static com.fc.v2.model.auto.TNslpZoneBook zone(int status) {
        return zone(status, "在册全称");
    }

    private static com.fc.v2.model.auto.TNslpZoneBook zone(int status, String name) {
        com.fc.v2.model.auto.TNslpZoneBook z = new com.fc.v2.model.auto.TNslpZoneBook();
        z.setZoneNo("QN00");
        z.setZoneName(name);
        z.setZoneKind("甲层");
        z.setStatus(Integer.valueOf(status));
        z.setDelFlag(Integer.valueOf(0));
        return z;
    }
}
