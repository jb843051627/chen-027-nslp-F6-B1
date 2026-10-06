package com.fc.v2.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.TNslpDocTpl;

/**
 * 文书版次与出文纯口径测试：效期窗选版、版次大者开口、撤版不进、缺项逐项点名、
 * miss 唯一轧法。
 */
public class DocTplRulesTest {

    private static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static Date t(String s) {
        try {
            return F.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static TNslpDocTpl tpl(long id, int ver, int status, String start, String end) {
        TNslpDocTpl d = new TNslpDocTpl();
        d.setId(Long.valueOf(id));
        d.setTplName(DocTplRules.NAME_OVER_STANDARD);
        d.setVerNo(Integer.valueOf(ver));
        d.setVarNum(Integer.valueOf(2));
        d.setStatus(Integer.valueOf(status));
        d.setEffStart(t(start));
        d.setEffEnd(end == null ? null : t(end));
        return d;
    }

    @Test
    public void activeWindowHeadInclusiveTailExclusive() {
        TNslpDocTpl v1 = tpl(1, 1, 0, "2025-01-01 00:00:00", "2026-01-01 00:00:00");
        // 起用当日含
        assertTrue(DocTplRules.activeAt(v1, t("2025-01-01 00:00:00")));
        // 交班当日不含
        assertFalse(DocTplRules.activeAt(v1, t("2026-01-01 00:00:00")));
        // 窗内
        assertTrue(DocTplRules.activeAt(v1, t("2025-06-01 00:00:00")));
        // 起用前
        assertFalse(DocTplRules.activeAt(v1, t("2024-12-31 23:59:59")));
        // 撤版不进
        assertFalse(DocTplRules.activeAt(tpl(2, 2, 1, "2025-01-01 00:00:00", null),
                t("2025-06-01 00:00:00")));
        // eff_end 留空一直用下去
        assertTrue(DocTplRules.activeAt(tpl(3, 1, 0, "2025-01-01 00:00:00", null),
                t("2030-01-01 00:00:00")));
    }

    @Test
    public void higherVersionWinsAndTieBreaksById() {
        TNslpDocTpl v1 = tpl(1, 1, 0, "2025-01-01 00:00:00", null);
        TNslpDocTpl v3 = tpl(3, 3, 0, "2025-01-01 00:00:00", null);
        TNslpDocTpl v2 = tpl(2, 2, 0, "2025-01-01 00:00:00", null);
        assertTrue(DocTplRules.winnerOrder().compare(v3, v1) < 0);
        // 同版次主键新者开口
        TNslpDocTpl a = tpl(5, 2, 0, "2025-01-01 00:00:00", null);
        assertTrue(DocTplRules.winnerOrder().compare(a, v2) < 0);
    }

    @Test
    public void missIsVarMinusFill() {
        assertEquals(0, DocTplRules.miss(2, 2));
        assertEquals(2, DocTplRules.miss(2, 0));
        assertEquals(1, DocTplRules.miss(3, 2));
    }

    @Test
    public void missingItemsNamedOneByOne() {
        // 题名空、限值线没挂：两项都点
        List<String> both = DocTplRules.missingItems(2, "  ", null, null);
        assertEquals(Arrays.asList(DocTplRules.ITEM_POINT_TITLE, DocTplRules.ITEM_LIMIT_LINE), both);
        // 都齐：空单
        assertTrue(DocTplRules.missingItems(2, "临江1号", "RL-DAY", null).isEmpty());
        // 第 3 项 extra 取不到
        Map<Integer, String> extra = new HashMap<Integer, String>();
        extra.put(3, "  ");
        List<String> three = DocTplRules.missingItems(3, "临江1号", "RL-DAY", extra);
        assertEquals(Arrays.asList("第3应填项"), three);
    }

    @Test
    public void tplHealthCheck() {
        assertNull(DocTplRules.checkTpl(tpl(1, 1, 0, "2025-01-01 00:00:00", null)));
        TNslpDocTpl bad = tpl(1, 1, 0, "2025-01-01 00:00:00", null);
        bad.setVarNum(Integer.valueOf(1));
        assertTrue(DocTplRules.checkTpl(bad).contains("应填项数"));
        TNslpDocTpl noStart = tpl(1, 1, 0, "2025-01-01 00:00:00", null);
        noStart.setEffStart(null);
        assertTrue(DocTplRules.checkTpl(noStart).contains("起用那一一日"));
    }

    @Test
    public void knownNames() {
        assertTrue(DocTplRules.isKnownName("超标告知"));
        assertTrue(DocTplRules.isKnownName("责令限期改正"));
        assertTrue(DocTplRules.isKnownName("复查结论"));
        assertFalse(DocTplRules.isKnownName("别的"));
    }
}
