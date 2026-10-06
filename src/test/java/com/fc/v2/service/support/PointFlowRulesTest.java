package com.fc.v2.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 点位三态纯口径测试：步子只由留痕放出、退回留痕、压卡冻结、叫停腾额、旧行空步兼容。
 */
public class PointFlowRulesTest {

    private static PointFlowRules.Event e(String action) {
        return new PointFlowRules.Event(action, 0);
    }

    @Test
    public void legacyEmptyTrailIsNotSealed() {
        // 纸质簿搬进来的旧行：一笔留痕都没有 → 步 null，不是压卡，但占名额
        PointFlowRules.ReplayResult r = PointFlowRules.replay(new ArrayList<PointFlowRules.Event>());
        assertNull(r.getStep());
        assertFalse(r.isSealed());
        assertNull(PointFlowRules.replay(null).getStep());

        assertTrue(PointFlowRules.occupiesSlot(null, 0, 0));
        assertFalse(PointFlowRules.occupiesSlot(null, 0, 1)); // 删了的不算
    }

    @Test
    public void forwardFromPendingToMeasured() {
        List<PointFlowRules.Event> evs = new ArrayList<PointFlowRules.Event>();
        evs.add(e(PointFlowRules.ACT_START));
        assertEquals(Integer.valueOf(0), PointFlowRules.replay(evs).getStep());

        evs.add(e(PointFlowRules.ACT_FORWARD));
        PointFlowRules.ReplayResult r = PointFlowRules.replay(evs);
        assertEquals(Integer.valueOf(1), r.getStep());
        assertEquals(1, r.getForwardCount());
    }

    @Test
    public void rollbackKeepsOldTrailAndReturnsToPending() {
        List<PointFlowRules.Event> evs = new ArrayList<PointFlowRules.Event>();
        evs.add(e(PointFlowRules.ACT_START));
        evs.add(e(PointFlowRules.ACT_FORWARD));
        evs.add(e(PointFlowRules.ACT_BACK));
        PointFlowRules.ReplayResult r = PointFlowRules.replay(evs);
        // 先前那一趟的推进记录仍在（forwardCount=1），当下退回待测（不是压卡）
        assertEquals(Integer.valueOf(0), r.getStep());
        assertEquals(1, r.getForwardCount());
        assertEquals(1, r.getBackCount());
        assertFalse(r.isSealed());

        // 退回后重新推进，仍只到已测；不会把退回后的多余推进并成一排
        evs.add(e(PointFlowRules.ACT_FORWARD));
        assertEquals(Integer.valueOf(1), PointFlowRules.replay(evs).getStep());
    }

    @Test
    public void sealOnlyFromMeasuredAndFreezes() {
        // 待测直接压卡不生效
        List<PointFlowRules.Event> tooSoon = new ArrayList<PointFlowRules.Event>();
        tooSoon.add(e(PointFlowRules.ACT_START));
        tooSoon.add(e(PointFlowRules.ACT_SEAL));
        PointFlowRules.ReplayResult r0 = PointFlowRules.replay(tooSoon);
        assertEquals(Integer.valueOf(0), r0.getStep());
        assertFalse(r0.isSealed());

        // 已测压卡到头，之后的推进/退回都不动
        List<PointFlowRules.Event> evs = new ArrayList<PointFlowRules.Event>();
        evs.add(e(PointFlowRules.ACT_START));
        evs.add(e(PointFlowRules.ACT_FORWARD));
        evs.add(e(PointFlowRules.ACT_SEAL));
        evs.add(e(PointFlowRules.ACT_FORWARD));
        evs.add(e(PointFlowRules.ACT_BACK));
        PointFlowRules.ReplayResult r = PointFlowRules.replay(evs);
        assertEquals(Integer.valueOf(2), r.getStep());
        assertTrue(r.isSealed());
        assertTrue(PointFlowRules.isSealed(r.getStep()));
        assertTrue(PointFlowRules.frozen(r.getStep(), 0));
        // 压卡腾出名额
        assertFalse(PointFlowRules.occupiesSlot(r.getStep(), 0, 0));
    }

    @Test
    public void stopFreesSlotButIsNotSeal() {
        List<PointFlowRules.Event> evs = new ArrayList<PointFlowRules.Event>();
        evs.add(e(PointFlowRules.ACT_START));
        evs.add(e(PointFlowRules.ACT_STOP));
        PointFlowRules.ReplayResult r = PointFlowRules.replay(evs);
        assertTrue(r.isStopped());
        assertFalse(r.isSealed());
        // 叫停后由 frozen 挡住推进（forwardTarget 只看步，停不停在 frozen/服务处判）
        assertTrue(PointFlowRules.frozen(r.getStep(), 1));
        assertFalse(PointFlowRules.canRollback(r.getStep(), 1));
        // 叫停腾名额、行仍在册（del_flag 仍 0，只是不占在走名额）
        assertFalse(PointFlowRules.occupiesSlot(r.getStep(), 1, 0));
        assertTrue(PointFlowRules.occupiesSlot(r.getStep(), 1, 1) == false);
    }

    @Test
    public void guardsAndNames() {
        assertEquals("待测", PointFlowRules.stepName(0));
        assertEquals("已测", PointFlowRules.stepName(1));
        assertEquals("压卡", PointFlowRules.stepName(2));
        assertEquals("步数不明", PointFlowRules.stepName(null));

        assertTrue(PointFlowRules.canRollback(1, 0));
        assertFalse(PointFlowRules.canRollback(0, 0));
        assertFalse(PointFlowRules.canRollback(1, 1)); // 叫停的退不了
        assertTrue(PointFlowRules.canSeal(1, 0));
        assertFalse(PointFlowRules.canSeal(2, 0));
    }
}
