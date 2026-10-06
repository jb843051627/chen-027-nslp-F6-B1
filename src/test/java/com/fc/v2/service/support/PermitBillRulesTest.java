package com.fc.v2.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 签批单纯口径测试：三关各数各的、先落为准与一并认可不混用、同人同轮第二句不采信、
 * 回放认关认轮、压回抹本轮旧笔留底、止住/点齐冻住。
 */
public class PermitBillRulesTest {

    private static PermitBillRules.Event open() {
        return new PermitBillRules.Event(PermitBillRules.ACT_OPEN,
                PermitBillRules.NODE_INTAKE, 1, false);
    }

    private static PermitBillRules.Event sign(int node, int round, boolean agree) {
        return sign(node, round, agree, false);
    }

    private static PermitBillRules.Event sign(int node, int round, boolean agree, boolean voided) {
        return new PermitBillRules.Event(PermitBillRules.ACT_SIGN, node, round, voided);
    }

    private static PermitBillRules.Event advance(int node, int round) {
        return new PermitBillRules.Event(PermitBillRules.ACT_ADVANCE, node, round, false);
    }

    private static PermitBillRules.Event rollback(int node, int round) {
        return new PermitBillRules.Event(PermitBillRules.ACT_ROLLBACK, node, round, false);
    }

    private static PermitBillRules.Event seal() {
        return new PermitBillRules.Event(PermitBillRules.ACT_SEAL,
                PermitBillRules.NODE_CITY, 1, false);
    }

    private static PermitBillRules.Event stop(int node, int round) {
        return new PermitBillRules.Event(PermitBillRules.ACT_STOP, node, round, false);
    }

    @Test
    public void threeNodesHaveFixedNeedAndModes() {
        assertEquals(1, PermitBillRules.needCount(0));
        assertEquals(2, PermitBillRules.needCount(1));
        assertEquals(2, PermitBillRules.needCount(2));
        assertEquals(PermitBillRules.MODE_SOLE, PermitBillRules.signMode(0));
        assertEquals(PermitBillRules.MODE_FIRST_WINS, PermitBillRules.signMode(1));
        assertEquals(PermitBillRules.MODE_ALL_APPROVE, PermitBillRules.signMode(2));
        assertEquals("区里收件席", PermitBillRules.nodeName(0));
        assertEquals("区局认定席", PermitBillRules.nodeName(1));
        assertEquals("市局存备席", PermitBillRules.nodeName(2));
        assertEquals("关数不明", PermitBillRules.nodeName(3));
    }

    @Test
    public void vetoOnlyExactText() {
        assertTrue(PermitBillRules.isVetoOpinion("不可"));
        assertTrue(PermitBillRules.isVetoOpinion(" 不可 "));
        assertFalse(PermitBillRules.isVetoOpinion("不可，材料不全"));
        assertFalse(PermitBillRules.isVetoOpinion("不同意"));
        assertFalse(PermitBillRules.isVetoOpinion(""));
        assertFalse(PermitBillRules.isVetoOpinion(null));
    }

    // ---- 收件席：一名即过 -------------------------------------------------

    @Test
    public void intakeNodeOneApproverCompletesVetoStops() {
        assertEquals(PermitBillRules.Decision.ACCEPT_COMPLETE,
                PermitBillRules.decide(0, 0, null, false, true));
        assertEquals(PermitBillRules.Decision.ACCEPT_STOP,
                PermitBillRules.decide(0, 0, null, false, false));
        // 已收满一名，第二笔不收；同人再来也不收
        assertEquals(PermitBillRules.Decision.REJECT_FULL,
                PermitBillRules.decide(0, 1, Boolean.TRUE, false, true));
        assertEquals(PermitBillRules.Decision.REJECT_DUP,
                PermitBillRules.decide(0, 0, null, true, true));
    }

    // ---- 认定席：先落为准 -------------------------------------------------

    @Test
    public void districtFirstOpinionWinsSecondStillCounted() {
        // 头一句可：等第二句
        assertEquals(PermitBillRules.Decision.ACCEPT_WAIT,
                PermitBillRules.decide(1, 0, null, false, true));
        // 头一句不可：整张止住
        assertEquals(PermitBillRules.Decision.ACCEPT_STOP,
                PermitBillRules.decide(1, 0, null, false, false));
        // 头一句已是「可」，第二句说什么都收、都累笔，但结论跟头一句走 → 算齐
        assertEquals(PermitBillRules.Decision.ACCEPT_COMPLETE,
                PermitBillRules.decide(1, 1, Boolean.TRUE, false, true));
        assertEquals(PermitBillRules.Decision.ACCEPT_COMPLETE,
                PermitBillRules.decide(1, 1, Boolean.TRUE, false, false));
        // 同人同轮第二句不采信
        assertEquals(PermitBillRules.Decision.REJECT_DUP,
                PermitBillRules.decide(1, 1, Boolean.TRUE, true, true));
        // 两句都落过了，第三句不收
        assertEquals(PermitBillRules.Decision.REJECT_FULL,
                PermitBillRules.decide(1, 2, Boolean.TRUE, false, true));
    }

    // ---- 存备席：两条一并认可 ---------------------------------------------

    @Test
    public void cityRequiresBothApproveSplitWaits() {
        assertEquals(PermitBillRules.Decision.ACCEPT_WAIT,
                PermitBillRules.decide(2, 0, null, false, true));
        assertEquals(PermitBillRules.Decision.ACCEPT_STOP,
                PermitBillRules.decide(2, 0, null, false, false));
        // 两条都可才算齐
        assertEquals(PermitBillRules.Decision.ACCEPT_COMPLETE,
                PermitBillRules.decide(2, 1, Boolean.TRUE, false, true));
        // 一可一不可：停在本关等，不前不止
        assertEquals(PermitBillRules.Decision.ACCEPT_WAIT_SPLIT,
                PermitBillRules.decide(2, 1, Boolean.TRUE, false, false));
        // 分歧停等后本关已两笔，再递一律不收（解开只走压回）
        assertEquals(PermitBillRules.Decision.REJECT_FULL,
                PermitBillRules.decide(2, 2, Boolean.TRUE, false, true));
        assertEquals(PermitBillRules.Decision.REJECT_DUP,
                PermitBillRules.decide(2, 1, Boolean.TRUE, true, true));
    }

    // ---- 回放：正常走满三关点齐 -------------------------------------------

    @Test
    public void replayHappyPathSealsAtCity() {
        List<PermitBillRules.Event> evs = new ArrayList<PermitBillRules.Event>();
        evs.add(open());
        evs.add(sign(0, 1, true));
        evs.add(advance(1, 1));
        evs.add(sign(1, 1, true));
        evs.add(sign(1, 1, false)); // 认定席第二句哪怕不认可，结论仍跟头一句走
        evs.add(advance(2, 1));
        evs.add(sign(2, 1, true));
        evs.add(sign(2, 1, true));
        evs.add(seal());
        PermitBillRules.ReplayResult r = PermitBillRules.replay(evs);
        assertEquals(Integer.valueOf(2), r.getNode());
        assertEquals(PermitBillRules.STATUS_SEALED, r.getStatus());
        assertEquals(1, r.getLiveCount(0));
        assertEquals(2, r.getLiveCount(1));
        assertEquals(2, r.getLiveCount(2));
        assertEquals(5, r.getSignCount());
        // 点齐之后再添什么都不动
        evs.add(advance(2 + 1, 1));
        evs.add(sign(2, 1, false));
        PermitBillRules.ReplayResult r2 = PermitBillRules.replay(evs);
        assertEquals(PermitBillRules.STATUS_SEALED, r2.getStatus());
        assertEquals(Integer.valueOf(2), r2.getNode());
    }

    // ---- 回放：越关推进/跳关落笔不采信 ------------------------------------

    @Test
    public void replayIgnoresSkipping() {
        List<PermitBillRules.Event> evs = new ArrayList<PermitBillRules.Event>();
        evs.add(open());
        // 跳过认定席直接推进存备席：不采信，整单还在收件席
        evs.add(new PermitBillRules.Event(PermitBillRules.ACT_ADVANCE, 2, 1, false));
        assertEquals(Integer.valueOf(0), PermitBillRules.replay(evs).getNode());
        // 存备席提前递来的笔（轮次对不上到达轮）不进活笔
        evs.add(sign(2, 1, true));
        PermitBillRules.ReplayResult r = PermitBillRules.replay(evs);
        assertEquals(Integer.valueOf(0), r.getNode());
        assertEquals(0, r.getLiveCount(2));
    }

    // ---- 回放：压回只退一关、只抹本轮笔、重走算新一轮 ----------------------

    @Test
    public void replayRollbackVoidsOnlyCurrentRound() {
        List<PermitBillRules.Event> evs = new ArrayList<PermitBillRules.Event>();
        evs.add(open());
        evs.add(sign(0, 1, true));
        evs.add(advance(1, 1));
        evs.add(sign(1, 1, true)); // 认定席头一轮先落了一句可
        // 压回：头一轮那句在库里被置 void_flag=1，行仍在
        evs.set(3, sign(1, 1, true, true));
        evs.add(rollback(0, 2));
        PermitBillRules.ReplayResult r = PermitBillRules.replay(evs);
        assertEquals(Integer.valueOf(0), r.getNode());
        assertEquals(1, r.getRollbackCount());
        assertEquals(0, r.getLiveCount(1)); // 本轮笔抹去
        assertEquals(2, r.getCurrentRound(0)); // 收件席重走算第二轮
        // 收件席第二轮重签再推进：认定席进入第二轮
        evs.add(sign(0, 2, true));
        evs.add(advance(1, 2));
        evs.add(sign(1, 2, false)); // 第二轮头一句不可 → 止住
        evs.add(stop(1, 2));
        PermitBillRules.ReplayResult r2 = PermitBillRules.replay(evs);
        assertEquals(Integer.valueOf(1), r2.getNode());
        assertEquals(PermitBillRules.STATUS_STOPPED, r2.getStatus());
        assertEquals(1, r2.getLiveCount(1)); // 只数第二轮这一句
        assertEquals(2, r2.getCurrentRound(1));
        // 止住后推进不动
        evs.add(advance(2, 1));
        assertEquals(Integer.valueOf(1), PermitBillRules.replay(evs).getNode());
    }

    // ---- 回放：收件席一句不可即止住、空串当未立单 --------------------------

    @Test
    public void replayStopAtFirstNodeAndEmptyTrail() {
        List<PermitBillRules.Event> evs = new ArrayList<PermitBillRules.Event>();
        evs.add(open());
        evs.add(sign(0, 1, false));
        evs.add(stop(0, 1));
        PermitBillRules.ReplayResult r = PermitBillRules.replay(evs);
        assertEquals(PermitBillRules.STATUS_STOPPED, r.getStatus());
        assertTrue(PermitBillRules.isFrozen(r.getStatus()));
        // 一笔留痕都没有：没立起单，node 回 null（不是收件席）
        assertEquals(null, PermitBillRules.replay(new ArrayList<PermitBillRules.Event>()).getNode());
    }

    @Test
    public void frozenAndRollbackGuards() {
        assertFalse(PermitBillRules.canRollback(PermitBillRules.STATUS_RUNNING, 0));
        assertTrue(PermitBillRules.canRollback(PermitBillRules.STATUS_RUNNING, 1));
        assertTrue(PermitBillRules.canRollback(PermitBillRules.STATUS_RUNNING, 2));
        assertFalse(PermitBillRules.canRollback(PermitBillRules.STATUS_SEALED, 2));
        assertFalse(PermitBillRules.canRollback(PermitBillRules.STATUS_STOPPED, 1));
        assertTrue(PermitBillRules.isFrozen(PermitBillRules.STATUS_SEALED));
        assertTrue(PermitBillRules.isFrozen(PermitBillRules.STATUS_STOPPED));
        assertFalse(PermitBillRules.isFrozen(PermitBillRules.STATUS_RUNNING));
    }
}
