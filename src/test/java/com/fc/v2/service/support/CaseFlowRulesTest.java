package com.fc.v2.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fc.v2.model.auto.TNslpPoint;

/**
 * 办件流转纯口径测试：四档钉死、立件点名、各关门槛、同关一句按语、
 * 封关两件、留痕放档。
 */
public class CaseFlowRulesTest {

    // ------------------------------------------------------------------
    // 四档钉死，档名只此一处
    // ------------------------------------------------------------------

    @Test
    public void stagesAreFixedFour() {
        assertEquals(4, CaseFlowRules.STAGE_COUNT);
        assertEquals("受件", CaseFlowRules.stageName(0));
        assertEquals("核查", CaseFlowRules.stageName(1));
        assertEquals("责令", CaseFlowRules.stageName(2));
        assertEquals("复查收尾", CaseFlowRules.stageName(3));
        assertEquals("档数不明", CaseFlowRules.stageName(null));
        assertEquals("档数不明", CaseFlowRules.stageName(9));
    }

    // ------------------------------------------------------------------
    // 立：缺哪一样点哪一样的名，光说信息不全不认
    // ------------------------------------------------------------------

    @Test
    public void intakeNamesEachMissingOne() {
        // 一样不缺：起得来
        assertTrue(CaseFlowRules.intakeMissing("PT0001", "QN00", 1, "56.2").isEmpty());

        List<String> all = CaseFlowRules.intakeMissing("  ", "", null, " ");
        assertEquals(4, all.size());
        assertTrue(all.get(0).contains("点位码"));
        assertTrue(all.get(1).contains("区册代号"));
        assertTrue(all.get(2).contains("处数"));
        assertTrue(all.get(3).contains("核查读数"));

        // 单独缺一样，点一样的名
        assertTrue(CaseFlowRules.intakeMissing(null, "QN00", 1, "56").get(0).contains("点位码"));
        assertTrue(CaseFlowRules.intakeMissing("PT0001", null, 1, "56").get(0).contains("区册代号"));
        assertTrue(CaseFlowRules.intakeMissing("PT0001", "QN00", null, "56").get(0).contains("处数"));
        assertTrue(CaseFlowRules.intakeMissing("PT0001", "QN00", 1, null).get(0).contains("核查读数"));

        // 处数记 0、读数不成数，也点名
        assertTrue(CaseFlowRules.intakeMissing("PT0001", "QN00", 0, "56").get(0).contains("至少一处"));
        assertTrue(CaseFlowRules.intakeMissing("PT0001", "QN00", -2, "56").get(0).contains("至少一处"));
        assertTrue(CaseFlowRules.intakeMissing("PT0001", "QN00", 1, "五十六").get(0).contains("得是个数"));
    }

    @Test
    public void readingParsedToNumber() {
        assertEquals(new BigDecimal("56.2"), CaseFlowRules.toReading(" 56.2 "));
        assertNull(CaseFlowRules.toReading(null));
        assertNull(CaseFlowRules.toReading("abc"));
    }

    @Test
    public void pointOnRegisterCheck() {
        assertFalse(CaseFlowRules.pointOnRegister(null));
        TNslpPoint p = new TNslpPoint();
        p.setDelFlag(0);
        assertTrue(CaseFlowRules.pointOnRegister(p));
        p.setDelFlag(1);
        assertFalse(CaseFlowRules.pointOnRegister(p));
    }

    // ------------------------------------------------------------------
    // 挪：只许紧邻的下一关；门槛摆在前头
    // ------------------------------------------------------------------

    @Test
    public void forwardAdjacentOnly() {
        assertTrue(CaseFlowRules.adjacentForward(0, 1));
        assertTrue(CaseFlowRules.adjacentForward(2, 3));
        // 越过中间关直接送再下一关：不生效
        assertFalse(CaseFlowRules.adjacentForward(0, 2));
        assertFalse(CaseFlowRules.adjacentForward(1, 3));
        // 原地、倒退都不叫往前
        assertFalse(CaseFlowRules.adjacentForward(1, 1));
        assertFalse(CaseFlowRules.adjacentForward(3, 4));
    }

    @Test
    public void rollbackOneStepOnly() {
        assertTrue(CaseFlowRules.canRollback(1));
        assertTrue(CaseFlowRules.canRollback(3));
        // 已经站在第一关的，没什么可再收
        assertFalse(CaseFlowRules.canRollback(0));
    }

    @Test
    public void guardsNameTheThreshold() {
        // 进核查：撤了名的进不去
        String g1 = CaseFlowRules.guardForward(0, false, null);
        assertTrue(g1.contains("核查"));
        assertTrue(g1.contains("撤名"));
        assertNull(CaseFlowRules.guardForward(0, true, null));

        // 核查→责令不再另设门槛
        assertNull(CaseFlowRules.guardForward(1, false, null));

        // 进复查收尾：责令关核收没记成「可」就开不了工
        String g2 = CaseFlowRules.guardForward(2, false, "否");
        assertTrue(g2.contains("复查收尾"));
        assertTrue(g2.contains("可"));
        assertTrue(g2.contains("责令"));
        assertNull(CaseFlowRules.guardForward(2, false, "可"));
    }

    // ------------------------------------------------------------------
    // 封关两件：两名见证岗各一笔＋凭证一串
    // ------------------------------------------------------------------

    @Test
    public void sealNeedsTwoWitnessesAndProof() {
        assertTrue(CaseFlowRules.sealMissing("甲", "乙", "PZ001").isEmpty());
        List<String> none = CaseFlowRules.sealMissing(null, null, null);
        assertEquals(3, none.size());
        assertTrue(none.get(0).contains("第一名"));
        assertTrue(none.get(1).contains("第二名"));
        assertTrue(none.get(2).contains("复测凭证"));

        // 同一个名字抵不了两笔
        List<String> same = CaseFlowRules.sealMissing("张三", "张三", "PZ001");
        assertEquals(1, same.size());
        assertTrue(same.get(0).contains("两岗"));

        // 缺一名见证：点这名
        assertEquals(1, CaseFlowRules.sealMissing(null, "乙", "PZ001").size());
        assertEquals(1, CaseFlowRules.sealMissing("甲", "  ", "PZ001").size());
    }

    // ------------------------------------------------------------------
    // 同一关第二句：本行留头一句，第二句盖不上
    // ------------------------------------------------------------------

    @Test
    public void noteMaskKeepsFirstOnlyPerStageAndResetsOnRollback() {
        int mask = 0;
        assertFalse(CaseFlowRules.hasNote(mask, 1));
        mask = CaseFlowRules.withNote(mask, 1);
        assertTrue(CaseFlowRules.hasNote(mask, 1));
        // 第二句来时那一位仍在：本行不再盖
        mask = CaseFlowRules.withNote(mask, 1);
        assertEquals(CaseFlowRules.noteBit(1), mask);
        // 各关各一位，互不挤
        mask = CaseFlowRules.withNote(mask, 3);
        assertTrue(CaseFlowRules.hasNote(mask, 1));
        assertTrue(CaseFlowRules.hasNote(mask, 3));
        // 收回第 1 关：位腾掉，重新挪到时从头累计
        mask = CaseFlowRules.withoutNote(mask, 1);
        assertFalse(CaseFlowRules.hasNote(mask, 1));
        assertTrue(CaseFlowRules.hasNote(mask, 3));
    }

    // ------------------------------------------------------------------
    // 封住之后只许看
    // ------------------------------------------------------------------

    @Test
    public void frozenReadonly() {
        assertFalse(CaseFlowRules.isFrozen(CaseFlowRules.STATUS_ACTIVE, 0));
        assertTrue(CaseFlowRules.isFrozen(CaseFlowRules.STATUS_TERMINAL, 0));
        assertTrue(CaseFlowRules.isFrozen(CaseFlowRules.STATUS_TERMINAL, 1));
        assertTrue(CaseFlowRules.isFrozen(CaseFlowRules.STATUS_ACTIVE, 1));

        // 走满末关封住：close_kind 空、未作废
        assertTrue(CaseFlowRules.isSealed(CaseFlowRules.STATUS_TERMINAL, null, 0));
        // 撤回、并走虽收口，不算正常封住
        assertFalse(CaseFlowRules.isSealed(CaseFlowRules.STATUS_TERMINAL, 1, 0));
        assertFalse(CaseFlowRules.isSealed(CaseFlowRules.STATUS_TERMINAL, 2, 0));
        // 作废也不算封住的那种，但一样动手不得（isFrozen 兜住）
        assertFalse(CaseFlowRules.isSealed(CaseFlowRules.STATUS_ACTIVE, null, 1));
    }

    // ------------------------------------------------------------------
    // 捋：档数由留痕这一串放出来，不另算一套
    // ------------------------------------------------------------------

    @Test
    public void replayFullJourney() {
        List<CaseFlowRules.Event> events = new ArrayList<CaseFlowRules.Event>(Arrays.asList(
                ev(CaseFlowRules.ACT_RECEIVE, 0),
                ev(CaseFlowRules.ACT_FORWARD, 1),
                ev(CaseFlowRules.ACT_NOTE, 1),
                ev(CaseFlowRules.ACT_FORWARD, 2),
                ev(CaseFlowRules.ACT_FORWARD, 3),
                ev(CaseFlowRules.ACT_SEAL, 3)));
        CaseFlowRules.ReplayResult r = CaseFlowRules.replay(events);
        assertEquals(3, r.getStage());
        assertTrue(r.isTerminal());
        assertNull(r.getCloseKind());
        assertFalse(r.isVoided());
        assertEquals(3, r.getForwardCount());
        assertEquals(0, r.getBackCount());
    }

    @Test
    public void replayRollbackThenForwardAgain() {
        // 从复查收尾一关一关收回，再从头捋一遍：档数还得对
        CaseFlowRules.ReplayResult r = CaseFlowRules.replay(Arrays.asList(
                ev(CaseFlowRules.ACT_RECEIVE, 0),
                ev(CaseFlowRules.ACT_FORWARD, 1),
                ev(CaseFlowRules.ACT_FORWARD, 2),
                ev(CaseFlowRules.ACT_FORWARD, 3),
                ev(CaseFlowRules.ACT_BACK, 2),
                ev(CaseFlowRules.ACT_BACK, 1),
                ev(CaseFlowRules.ACT_BACK, 0),
                ev(CaseFlowRules.ACT_FORWARD, 1),
                ev(CaseFlowRules.ACT_FORWARD, 2),
                ev(CaseFlowRules.ACT_FORWARD, 3),
                ev(CaseFlowRules.ACT_SEAL, 3)));
        assertEquals(3, r.getStage());
        assertTrue(r.isTerminal());
        assertEquals(6, r.getForwardCount());
        assertEquals(3, r.getBackCount());
    }

    @Test
    public void replayWithdrawAndMergeAreTerminalAtCurrentStage() {
        // 不必再往前挪：停在哪关收在哪关
        CaseFlowRules.ReplayResult w = CaseFlowRules.replay(Arrays.asList(
                ev(CaseFlowRules.ACT_RECEIVE, 0),
                ev(CaseFlowRules.ACT_FORWARD, 1),
                ev(CaseFlowRules.ACT_WITHDRAW, 1)));
        assertTrue(w.isTerminal());
        assertEquals(1, w.getStage());
        assertEquals(Integer.valueOf(CaseFlowRules.CLOSE_KIND_WITHDRAW), w.getCloseKind());

        CaseFlowRules.ReplayResult m = CaseFlowRules.replay(Arrays.asList(
                ev(CaseFlowRules.ACT_RECEIVE, 0),
                ev(CaseFlowRules.ACT_FORWARD, 1),
                ev(CaseFlowRules.ACT_FORWARD, 2),
                ev(CaseFlowRules.ACT_MERGE, 2)));
        assertTrue(m.isTerminal());
        assertEquals(2, m.getStage());
        assertEquals(Integer.valueOf(CaseFlowRules.CLOSE_KIND_MERGED), m.getCloseKind());

        // 作废另记
        CaseFlowRules.ReplayResult v = CaseFlowRules.replay(Arrays.asList(
                ev(CaseFlowRules.ACT_RECEIVE, 0),
                ev(CaseFlowRules.ACT_FORWARD, 1),
                ev(CaseFlowRules.ACT_FORWARD, 2),
                ev(CaseFlowRules.ACT_FORWARD, 3),
                ev(CaseFlowRules.ACT_VOID, 3)));
        assertTrue(v.isVoided());
        assertFalse(v.isTerminal());
    }

    @Test
    public void replayEmptyStaysAtReceived() {
        CaseFlowRules.ReplayResult r = CaseFlowRules.replay(null);
        assertEquals(0, r.getStage());
        assertFalse(r.isTerminal());
    }

    private static CaseFlowRules.Event ev(String action, int stage) {
        return new CaseFlowRules.Event(action, stage);
    }
}
