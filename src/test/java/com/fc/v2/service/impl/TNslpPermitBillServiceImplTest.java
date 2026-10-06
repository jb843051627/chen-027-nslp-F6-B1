package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TNslpPermitBillMapper;
import com.fc.v2.mapper.auto.TNslpPermitSignMapper;
import com.fc.v2.mapper.auto.TNslpPointChangeMapper;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.model.auto.TNslpPermitBill;
import com.fc.v2.model.auto.TNslpPermitSign;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.auto.TNslpPointChange;
import com.fc.v2.model.custom.PermitLedger;
import com.fc.v2.model.custom.PermitOpenSubmit;
import com.fc.v2.model.custom.PermitSignSubmit;
import com.fc.v2.model.custom.PermitTrace;
import com.fc.v2.service.support.PermitBillRules;

/**
 * 签批单服务测试：立・签・压回・捋照文走一遍。
 *
 * 四个 mapper 用内存账顶替，QueryWrapper 片段按列名逐个过滤；改主档与追加履历
 * 在同一次落笔内接连落账（事务由 Spring 管，单测看落账次序）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TNslpPermitBillServiceImplTest {

    @Mock
    private TNslpPermitBillMapper billMapper;
    @Mock
    private TNslpPermitSignMapper signMapper;
    @Mock
    private TNslpPointMapper pointMapper;
    @Mock
    private TNslpPointChangeMapper changeMapper;
    @InjectMocks
    private TNslpPermitBillServiceImpl service;

    private List<TNslpPermitBill> billStore;
    private List<TNslpPermitSign> signStore;
    private List<TNslpPoint> pointStore;
    private List<TNslpPointChange> changeStore;
    private long idSeq;

    private static final Pattern CLAUSE = Pattern.compile(
            "(\\w+)\\s*(<=|>=|=|<|>)\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}");

    private static boolean eq(Object field, Object val) {
        if (field == null || val == null) {
            return field == val;
        }
        return field.equals(val) || String.valueOf(field).equals(String.valueOf(val));
    }

    private static List<String[]> clauses(QueryWrapper<?> w) {
        w.getSqlSegment();
        Map<String, Object> params = w.getParamNameValuePairs();
        List<String[]> out = new ArrayList<String[]>();
        Matcher m = CLAUSE.matcher(w.getSqlSegment() == null ? "" : w.getSqlSegment());
        while (m.find()) {
            out.add(new String[]{m.group(1), m.group(2), String.valueOf(params.get(m.group(3)))});
        }
        return out;
    }

    private boolean billMatches(TNslpPermitBill b, List<String[]> cs) {
        for (String[] c : cs) {
            Object v;
            switch (c[0]) {
                case "id": v = b.getId(); break;
                case "point_no": v = b.getPointNo(); break;
                case "status": v = b.getStatus(); break;
                case "del_flag": v = b.getDelFlag(); break;
                default: v = null;
            }
            if (!eq(v, c[2])) {
                return false;
            }
        }
        return true;
    }

    private boolean signMatches(TNslpPermitSign s, List<String[]> cs) {
        for (String[] c : cs) {
            Object v;
            switch (c[0]) {
                case "bill_id": v = s.getBillId(); break;
                case "action": v = s.getAction(); break;
                case "node_no": v = s.getNodeNo(); break;
                case "round_no": v = s.getRoundNo(); break;
                case "void_flag": v = s.getVoidFlag(); break;
                case "del_flag": v = s.getDelFlag(); break;
                case "sign_time": v = s.getSignTime(); break;
                default: v = null;
            }
            if (!eq(v, c[2])) {
                return false;
            }
        }
        return true;
    }

    private boolean pointMatches(TNslpPoint p, List<String[]> cs) {
        for (String[] c : cs) {
            Object v;
            switch (c[0]) {
                case "id": v = p.getId(); break;
                case "del_flag": v = p.getDelFlag(); break;
                default: v = null;
            }
            if (!eq(v, c[2])) {
                return false;
            }
        }
        return true;
    }

    private TNslpPermitBill findBill(Long id) {
        for (TNslpPermitBill b : billStore) {
            if (b.getId().equals(id)) {
                return b;
            }
        }
        return null;
    }

    @BeforeEach
    public void setUp() {
        billStore = new ArrayList<TNslpPermitBill>();
        signStore = new ArrayList<TNslpPermitSign>();
        pointStore = new ArrayList<TNslpPoint>();
        changeStore = new ArrayList<TNslpPointChange>();
        idSeq = 0;

        when(billMapper.insert(any(TNslpPermitBill.class))).thenAnswer(inv -> {
            TNslpPermitBill b = inv.getArgument(0);
            b.setId(Long.valueOf(++idSeq));
            billStore.add(b);
            return 1;
        });
        when(billMapper.selectById(any())).thenAnswer(inv -> findBill(inv.getArgument(0)));
        when(billMapper.selectOne(any())).thenAnswer(inv -> {
            // 锁查/点查：内存账不区分 for update
            List<String[]> cs = clauses(inv.getArgument(0));
            for (TNslpPermitBill b : billStore) {
                if (billMatches(b, cs)) {
                    return b;
                }
            }
            return null;
        });
        when(billMapper.selectList(any())).thenAnswer(inv -> {
            List<String[]> cs = clauses(inv.getArgument(0));
            List<TNslpPermitBill> out = new ArrayList<TNslpPermitBill>();
            for (TNslpPermitBill b : billStore) {
                if (billMatches(b, cs)) {
                    out.add(b);
                }
            }
            return out;
        });
        when(billMapper.selectCount(any())).thenAnswer(inv -> {
            List<String[]> cs = clauses(inv.getArgument(0));
            int n = 0;
            for (TNslpPermitBill b : billStore) {
                if (billMatches(b, cs)) {
                    n++;
                }
            }
            return Integer.valueOf(n);
        });
        when(billMapper.updateById(any(TNslpPermitBill.class))).thenAnswer(inv -> {
            TNslpPermitBill u = inv.getArgument(0);
            TNslpPermitBill b = findBill(u.getId());
            if (b == null) {
                return 0;
            }
            if (u.getNodeNo() != null) b.setNodeNo(u.getNodeNo());
            if (u.getSignMode() != null) b.setSignMode(u.getSignMode());
            if (u.getNeedCount() != null) b.setNeedCount(u.getNeedCount());
            if (u.getSignCount() != null) b.setSignCount(u.getSignCount());
            if (u.getCurRound() != null) b.setCurRound(u.getCurRound());
            if (u.getStatus() != null) b.setStatus(u.getStatus());
            if (u.getUpdateBy() != null) b.setUpdateBy(u.getUpdateBy());
            if (u.getUpdateTime() != null) b.setUpdateTime(u.getUpdateTime());
            return 1;
        });

        when(signMapper.insert(any(TNslpPermitSign.class))).thenAnswer(inv -> {
            TNslpPermitSign s = inv.getArgument(0);
            s.setId(Long.valueOf(++idSeq));
            signStore.add(s);
            return 1;
        });
        when(signMapper.selectList(any())).thenAnswer(inv -> {
            List<String[]> cs = clauses(inv.getArgument(0));
            List<TNslpPermitSign> out = new ArrayList<TNslpPermitSign>();
            for (TNslpPermitSign s : signStore) {
                if (signMatches(s, cs)) {
                    out.add(s);
                }
            }
            return out;
        });
        when(signMapper.selectCount(any())).thenAnswer(inv -> {
            List<String[]> cs = clauses(inv.getArgument(0));
            int n = 0;
            for (TNslpPermitSign s : signStore) {
                if (signMatches(s, cs)) {
                    n++;
                }
            }
            return Integer.valueOf(n);
        });
        when(signMapper.updateById(any(TNslpPermitSign.class))).thenAnswer(inv -> {
            TNslpPermitSign u = inv.getArgument(0);
            for (TNslpPermitSign s : signStore) {
                if (s.getId().equals(u.getId())) {
                    if (u.getVoidFlag() != null) s.setVoidFlag(u.getVoidFlag());
                    return 1;
                }
            }
            return 0;
        });

        when(pointMapper.selectOne(any())).thenAnswer(inv -> {
            List<String[]> cs = clauses(inv.getArgument(0));
            for (TNslpPoint p : pointStore) {
                if (pointMatches(p, cs)) {
                    return p;
                }
            }
            return null;
        });
        when(pointMapper.selectById(any())).thenAnswer(inv -> {
            for (TNslpPoint p : pointStore) {
                if (p.getId().equals(inv.getArgument(0))) {
                    return p;
                }
            }
            return null;
        });
        when(pointMapper.updateById(any(TNslpPoint.class))).thenAnswer(inv -> {
            TNslpPoint u = inv.getArgument(0);
            for (TNslpPoint p : pointStore) {
                if (p.getId().equals(u.getId()) && u.getPermitState() != null) {
                    p.setPermitState(u.getPermitState());
                    p.setUpdateBy(u.getUpdateBy());
                    p.setUpdateTime(u.getUpdateTime());
                    return 1;
                }
            }
            return 0;
        });

        when(changeMapper.insert(any(TNslpPointChange.class))).thenAnswer(inv -> {
            TNslpPointChange c = inv.getArgument(0);
            c.setId(Long.valueOf(++idSeq));
            changeStore.add(c);
            return 1;
        });

        TNslpPoint p = new TNslpPoint();
        p.setId(Long.valueOf(1));
        p.setPointNo("PT0001");
        p.setZoneNo("QN00");
        p.setPermitState(Integer.valueOf(0));
        p.setDelFlag(Integer.valueOf(0));
        pointStore.add(p);
    }

    private TNslpPermitBill openBill() {
        PermitOpenSubmit submit = new PermitOpenSubmit();
        submit.setPointId(Long.valueOf(1));
        submit.setOperator("收件岗");
        return service.open(submit);
    }

    private boolean sign(Long id, String who, String opinion) {
        return sign(id, who, opinion, null);
    }

    private boolean sign(Long id, String who, String opinion, Integer reportNode) {
        PermitSignSubmit s = new PermitSignSubmit();
        s.setId(id);
        s.setSignBy(who);
        s.setOpinion(opinion);
        s.setReportNode(reportNode);
        return service.sign(s);
    }

    private List<TNslpPermitSign> signsOf(Long billId, String action) {
        List<TNslpPermitSign> out = new ArrayList<TNslpPermitSign>();
        for (TNslpPermitSign s : signStore) {
            if (s.getBillId().equals(billId) && action.equals(s.getAction())) {
                out.add(s);
            }
        }
        return out;
    }

    private List<TNslpPermitSign> liveSignsOf(Long billId) {
        List<TNslpPermitSign> out = new ArrayList<TNslpPermitSign>();
        for (TNslpPermitSign s : signStore) {
            if (s.getBillId().equals(billId)
                    && PermitBillRules.ACT_SIGN.equals(s.getAction())
                    && (s.getVoidFlag() == null || s.getVoidFlag().intValue() == 0)) {
                out.add(s);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 立
    // ------------------------------------------------------------------

    @Test
    public void openComesFromPointRegisterOnly() {
        assertThrows(IllegalArgumentException.class, () -> service.open(null));
        PermitOpenSubmit bad = new PermitOpenSubmit();
        bad.setPointId(Long.valueOf(999));
        assertThrows(IllegalArgumentException.class, () -> service.open(bad));

        TNslpPermitBill b = openBill();
        assertEquals("PT0001", b.getPointNo());
        assertEquals("QN00", b.getZoneNo());
        assertEquals(Integer.valueOf(0), b.getNodeNo());
        assertEquals(Integer.valueOf(PermitBillRules.MODE_SOLE), b.getSignMode());
        assertEquals(Integer.valueOf(1), b.getNeedCount());
        assertEquals(Integer.valueOf(0), b.getSignCount());
        assertEquals(Integer.valueOf(PermitBillRules.STATUS_RUNNING), b.getStatus());
        // 立单先压一笔痕
        assertEquals(PermitBillRules.ACT_OPEN, signStore.get(0).getAction());
        assertEquals(1, service.countOpen());

        // 一处点位在签的只许一张
        assertThrows(IllegalStateException.class, this::openBill);
    }

    // ------------------------------------------------------------------
    // 签：三关各数各的
    // ------------------------------------------------------------------

    @Test
    public void happyPathSealsAndGrantsPointInSameAction() {
        TNslpPermitBill b = openBill();

        assertTrue(sign(b.getId(), "甲", "可"));
        assertEquals(Integer.valueOf(1), service.selectTNslpPermitBillById(b.getId()).getNodeNo());
        assertEquals(Integer.valueOf(PermitBillRules.MODE_FIRST_WINS),
                service.selectTNslpPermitBillById(b.getId()).getSignMode());

        // 认定席先落为准：头一个「可」定调，第二个哪怕逐字「不可」，照常收、照常累笔、算齐
        assertTrue(sign(b.getId(), "区局甲", "可"));
        assertTrue(sign(b.getId(), "区局乙", "不可"));
        assertEquals(Integer.valueOf(2), service.selectTNslpPermitBillById(b.getId()).getNodeNo());
        // 两句原话都进库留底，两行都摆得出来
        List<TNslpPermitSign> district = signsOf(b.getId(), PermitBillRules.ACT_SIGN);
        long atNode1 = district.stream().filter(s -> s.getNodeNo().intValue() == 1).count();
        assertEquals(2, atNode1);

        // 存备席两条一并认可才点齐
        assertTrue(sign(b.getId(), "市局甲", "可"));
        TNslpPermitBill waiting = service.selectTNslpPermitBillById(b.getId());
        assertEquals(Integer.valueOf(2), waiting.getNodeNo());
        assertEquals(Integer.valueOf(1), waiting.getSignCount());
        assertTrue(sign(b.getId(), "市局乙", "可"));

        TNslpPermitBill sealed = service.selectTNslpPermitBillById(b.getId());
        assertEquals(Integer.valueOf(PermitBillRules.STATUS_SEALED), sealed.getStatus());
        assertEquals(Integer.valueOf(2), sealed.getNodeNo());
        assertEquals(Integer.valueOf(2), sealed.getSignCount());
        // 在签张数随封存减一
        assertEquals(0, service.countOpen());
        // 册面主档同一回改口，且履历追加同回：由谁落、哪张单、从哪档到哪档
        assertEquals(Integer.valueOf(PermitBillRules.POINT_PERMIT_GRANTED),
                pointStore.get(0).getPermitState());
        assertEquals(1, changeStore.size());
        TNslpPointChange c = changeStore.get(0);
        assertEquals("市局乙", c.getChangeBy());
        assertEquals(b.getId(), c.getBillId());
        assertEquals(0, c.getFromState().intValue());
        assertEquals(1, c.getToState().intValue());
        assertEquals("permit_state", c.getChangeField());

        // 封住后再递一律不接
        assertThrows(IllegalStateException.class, () -> sign(b.getId(), "路人", "可"));
    }

    @Test
    public void citySplitWaitsNeitherAdvancesNorStops() {
        TNslpPermitBill b = reachCity();
        assertTrue(sign(b.getId(), "市局甲", "可"));
        assertTrue(sign(b.getId(), "市局乙", "不可"));
        TNslpPermitBill now = service.selectTNslpPermitBillById(b.getId());
        // 停在存备席等：不前不止
        assertEquals(Integer.valueOf(2), now.getNodeNo());
        assertEquals(Integer.valueOf(PermitBillRules.STATUS_RUNNING), now.getStatus());
        // 两笔都在、都活着；第三笔（不管谁）不收
        assertEquals(2, liveSignsOf(b.getId()).stream().filter(s -> s.getNodeNo() == 2).count());
        assertFalse(sign(b.getId(), "市局丙", "可"));
        assertEquals(2, signsOf(b.getId(), PermitBillRules.ACT_SIGN).stream()
                .filter(s -> s.getNodeNo() == 2).count());
    }

    @Test
    public void vetoStopsImmediatelyAtAnyDrivingNode() {
        // 收件席一句「不可」：当即止住
        TNslpPermitBill b1 = openBill();
        assertTrue(sign(b1.getId(), "甲", "不可"));
        assertEquals(Integer.valueOf(PermitBillRules.STATUS_STOPPED),
                service.selectTNslpPermitBillById(b1.getId()).getStatus());
        assertEquals(0, service.countOpen());
        assertThrows(IllegalStateException.class, () -> sign(b1.getId(), "乙", "可"));

        // 认定席头一句「不可」：先落为准的头一句就是否决，止住在认定席
        TNslpPermitBill b2 = openBill();
        sign(b2.getId(), "甲", "可");
        assertTrue(sign(b2.getId(), "区局甲", "不可"));
        TNslpPermitBill stopped = service.selectTNslpPermitBillById(b2.getId());
        assertEquals(Integer.valueOf(PermitBillRules.STATUS_STOPPED), stopped.getStatus());
        assertEquals(Integer.valueOf(1), stopped.getNodeNo());
        assertThrows(IllegalStateException.class, () -> sign(b2.getId(), "区局乙", "可"));
    }

    @Test
    public void sameApproverTwiceAndReportedNodeAreNotTrusted() {
        TNslpPermitBill b = openBill();
        // 报文关次跟服务端对不上（提前递到认定席）：这笔丢掉，整单还在收件席
        assertFalse(sign(b.getId(), "区局甲", "可", Integer.valueOf(1)));
        assertEquals(Integer.valueOf(0), service.selectTNslpPermitBillById(b.getId()).getNodeNo());

        // 收件席一位落「可」推进认定席；跳关递来的存备席笔不采信，单仍在认定席
        assertTrue(sign(b.getId(), "甲", "可"));
        assertFalse(sign(b.getId(), "市局甲", "可", Integer.valueOf(2)));
        assertEquals(Integer.valueOf(1), service.selectTNslpPermitBillById(b.getId()).getNodeNo());

        // 认定席同一人同轮第二句：不覆盖、不另存、不累笔（幂等），头一句原话留着
        assertTrue(sign(b.getId(), "区局甲", "可"));
        assertFalse(sign(b.getId(), "区局甲", "我改主意，不可"));
        assertEquals("可", signsOf(b.getId(), PermitBillRules.ACT_SIGN).stream()
                .filter(s -> s.getNodeNo() == 1 && "区局甲".equals(s.getSignBy()))
                .findFirst().get().getOpinion());
        // 第二位仍要交，先落为准，第二句哪怕不认可也算齐
        assertTrue(sign(b.getId(), "区局乙", "暂不可"));
        assertEquals(Integer.valueOf(2), service.selectTNslpPermitBillById(b.getId()).getNodeNo());

        // 缺人、缺话不许落
        assertThrows(IllegalArgumentException.class, () -> sign(b.getId(), "  ", "可"));
        assertThrows(IllegalArgumentException.class, () -> sign(b.getId(), "市局甲", "  "));
    }

    // ------------------------------------------------------------------
    // 压回：只退一关，只抹本轮，旧笔留底
    // ------------------------------------------------------------------

    @Test
    public void rollbackOneNodeVoidsOnlyCurrentRound() {
        TNslpPermitBill b = reachCity();
        sign(b.getId(), "市局甲", "可");
        sign(b.getId(), "市局乙", "不可");

        // 压回收件席不允许跳，只退到认定席
        service.rollback(b.getId(), "岗", "分歧，退回重认");
        TNslpPermitBill back = service.selectTNslpPermitBillById(b.getId());
        assertEquals(Integer.valueOf(1), back.getNodeNo());
        assertEquals(Integer.valueOf(2), back.getCurRound());
        // 存备席本轮两句被抹去（行还在，void_flag=1），不计活笔
        List<TNslpPermitSign> cityAll = signsOf(b.getId(), PermitBillRules.ACT_SIGN).stream()
                .filter(s -> s.getNodeNo() == 2).collect(java.util.stream.Collectors.toList());
        assertEquals(2, cityAll.size());
        assertTrue(cityAll.stream().allMatch(s -> s.getVoidFlag() == 1));
        // 更早各关的笔一笔不动
        assertEquals(1, liveSignsOf(b.getId()).stream().filter(s -> s.getNodeNo() == 0).count());
        assertEquals(2, liveSignsOf(b.getId()).stream().filter(s -> s.getNodeNo() == 1).count());

        // 重走认定席：第二轮两句，旧轮原话与本轮分两处都查得着
        assertTrue(sign(b.getId(), "区局甲", "可"));
        assertTrue(sign(b.getId(), "区局乙", "可"));
        TNslpPermitBill atCity2 = service.selectTNslpPermitBillById(b.getId());
        assertEquals(Integer.valueOf(2), atCity2.getNodeNo());
        // 认定席两轮共 4 句都在（2 活 + 2 旧轮活），屏幕逐句列得出
        long districtRows = signsOf(b.getId(), PermitBillRules.ACT_SIGN).stream()
                .filter(s -> s.getNodeNo() == 1).count();
        assertEquals(4, districtRows);

        // 头一关退无可退：重走后单据又到存备席，连压两关回收件席，再压就抛
        service.rollback(b.getId(), "岗", null);
        service.rollback(b.getId(), "岗", null);
        assertEquals(Integer.valueOf(0), service.selectTNslpPermitBillById(b.getId()).getNodeNo());
        assertThrows(IllegalStateException.class, () -> service.rollback(b.getId(), null, null));
    }

    // ------------------------------------------------------------------
    // 捋：追溯与受理簿对账
    // ------------------------------------------------------------------

    @Test
    public void traceAndLedgerReconcile() {
        TNslpPermitBill b = reachCity();
        sign(b.getId(), "市局甲", "可");
        sign(b.getId(), "市局乙", "可");

        PermitTrace t = service.trace(b.getId());
        assertEquals(2, t.getReplayNode());
        assertEquals("市局存备席", t.getReplayNodeText());
        assertEquals(PermitBillRules.STATUS_SEALED, t.getReplayStatus());
        // 各关已到几人取自逐笔累算，与行面同源
        assertEquals(1, t.getNodes().get(0).getSignCount());
        assertEquals(2, t.getNodes().get(1).getSignCount());
        assertEquals(2, t.getNodes().get(2).getSignCount());

        PermitLedger l = service.ledger(b.getId(), null);
        // 落笔总 5 笔；倒着数回头一段，进出轧平
        assertEquals(5, l.getTotalCount());
        assertEquals(5, l.getLiveCount());
        assertEquals(0, l.getVoidedCount());
        assertTrue(l.isNodeBalanced(), "逐段进出该轧平：" + l.getUnbalancedNodes());

        // 压回一趟：抹去 2 笔仍在受理簿上，账上分活笔/抹去笔，逐段仍轧平
        TNslpPermitBill b2 = openBill();
        driveToCity(b2);
        sign(b2.getId(), "市局甲", "可");
        sign(b2.getId(), "市局乙", "不可");
        service.rollback(b2.getId(), "岗", null);
        PermitLedger l2 = service.ledger(b2.getId(), null);
        assertEquals(2, l2.getVoidedCount());
        assertTrue(l2.isNodeBalanced(), l2.getUnbalancedNodes().toString());
        assertEquals(1, l2.getReplayNode());
    }

    // ------------------------------------------------------------------
    // 帮衬
    // ------------------------------------------------------------------

    /** 走到存备席关前（收件 1 笔＋认定 2 笔已落），返回单据 */
    private TNslpPermitBill reachCity() {
        TNslpPermitBill b = openBill();
        driveToCity(b);
        return b;
    }

    private void driveToCity(TNslpPermitBill b) {
        sign(b.getId(), "甲", "可");
        sign(b.getId(), "区局甲", "可");
        sign(b.getId(), "区局乙", "可");
    }
}
