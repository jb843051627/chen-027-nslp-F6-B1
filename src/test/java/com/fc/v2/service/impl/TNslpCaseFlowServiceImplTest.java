package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
import com.fc.v2.mapper.auto.TNslpCaseFlowMapper;
import com.fc.v2.mapper.auto.TNslpCaseTrailMapper;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.mapper.auto.TNslpZoneBookMapper;
import com.fc.v2.model.auto.TNslpCaseFlow;
import com.fc.v2.model.auto.TNslpCaseTrail;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.auto.TNslpZoneBook;
import com.fc.v2.model.custom.CaseIntake;
import com.fc.v2.model.custom.CaseLedger;
import com.fc.v2.model.custom.CaseSeal;
import com.fc.v2.model.custom.CaseTrace;
import com.fc.v2.service.support.CaseFlowRules;

/**
 * 办件流转测试：立・挪・封・捋四件事照文走一遍。
 *
 * 四个 mapper 用内存账顶替，条件按 QueryWrapper 片段里的列名逐个过滤；
 * 改档与留痕在同一方法调用内接连落账（事务由 Spring 管，单测看落账次序）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TNslpCaseFlowServiceImplTest {

    @Mock
    private TNslpCaseFlowMapper caseMapper;
    @Mock
    private TNslpCaseTrailMapper trailMapper;
    @Mock
    private TNslpPointMapper pointMapper;
    @Mock
    private TNslpZoneBookMapper zoneMapper;
    @InjectMocks
    private TNslpCaseFlowServiceImpl service;

    private static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private List<TNslpCaseFlow> caseStore;
    private List<TNslpCaseTrail> trailStore;
    private List<TNslpPoint> pointStore;
    private List<TNslpZoneBook> zoneStore;
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

    private boolean caseMatches(TNslpCaseFlow r, List<String[]> cs) {
        for (String[] c : cs) {
            Object v;
            switch (c[0]) {
                case "point_no": v = r.getPointNo(); break;
                case "zone_no": v = r.getZoneNo(); break;
                case "status": v = r.getStatus(); break;
                case "void_flag": v = r.getVoidFlag(); break;
                case "del_flag": v = r.getDelFlag(); break;
                case "id": v = r.getId(); break;
                default: v = null;
            }
            if (!eq(v, c[2])) {
                return false;
            }
        }
        return true;
    }

    private boolean trailMatches(TNslpCaseTrail t, List<String[]> cs) {
        for (String[] c : cs) {
            Object v;
            switch (c[0]) {
                case "case_id": v = t.getCaseId(); break;
                case "trail_time": v = t.getTrailTime(); break;
                case "del_flag": v = t.getDelFlag(); break;
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
            Object v = "point_no".equals(c[0]) ? p.getPointNo()
                    : "del_flag".equals(c[0]) ? p.getDelFlag() : null;
            if (!eq(v, c[2])) {
                return false;
            }
        }
        return true;
    }

    private boolean zoneMatches(TNslpZoneBook z, List<String[]> cs) {
        for (String[] c : cs) {
            Object v = "zone_no".equals(c[0]) ? z.getZoneNo()
                    : "del_flag".equals(c[0]) ? z.getDelFlag() : null;
            if (!eq(v, c[2])) {
                return false;
            }
        }
        return true;
    }

    private TNslpCaseFlow findCase(Long id) {
        for (TNslpCaseFlow r : caseStore) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    @BeforeEach
    public void setUp() {
        caseStore = new ArrayList<TNslpCaseFlow>();
        trailStore = new ArrayList<TNslpCaseTrail>();
        pointStore = new ArrayList<TNslpPoint>();
        zoneStore = new ArrayList<TNslpZoneBook>();
        idSeq = 0;

        when(caseMapper.insert(any(TNslpCaseFlow.class))).thenAnswer(inv -> {
            TNslpCaseFlow r = inv.getArgument(0);
            r.setId(Long.valueOf(++idSeq));
            caseStore.add(r);
            return 1;
        });
        when(caseMapper.selectById(any())).thenAnswer(inv -> findCase(inv.getArgument(0)));
        when(caseMapper.selectList(any())).thenAnswer(inv -> {
            List<String[]> cs = clauses(inv.getArgument(0));
            List<TNslpCaseFlow> out = new ArrayList<TNslpCaseFlow>();
            for (TNslpCaseFlow r : caseStore) {
                if (caseMatches(r, cs)) {
                    out.add(r);
                }
            }
            return out;
        });
        when(caseMapper.selectCount(any())).thenAnswer(inv -> {
            List<String[]> cs = clauses(inv.getArgument(0));
            int n = 0;
            for (TNslpCaseFlow r : caseStore) {
                if (caseMatches(r, cs)) {
                    n++;
                }
            }
            return Integer.valueOf(n);
        });
        when(caseMapper.updateById(any(TNslpCaseFlow.class))).thenAnswer(inv -> {
            TNslpCaseFlow u = inv.getArgument(0);
            TNslpCaseFlow r = findCase(u.getId());
            if (r == null) {
                return 0;
            }
            // 模拟 MP 非空字段更新；空串照常覆盖
            if (u.getBizNo() != null) r.setBizNo(u.getBizNo());
            if (u.getPointNo() != null) r.setPointNo(u.getPointNo());
            if (u.getZoneNo() != null) r.setZoneNo(u.getZoneNo());
            if (u.getOccNum() != null) r.setOccNum(u.getOccNum());
            if (u.getCheckReading() != null) r.setCheckReading(u.getCheckReading());
            if (u.getStage() != null) r.setStage(u.getStage());
            if (u.getStatus() != null) r.setStatus(u.getStatus());
            if (u.getContent() != null) r.setContent(u.getContent());
            if (u.getLastAction() != null) r.setLastAction(u.getLastAction());
            if (u.getOrderAccept() != null) r.setOrderAccept(u.getOrderAccept());
            if (u.getWitnessA() != null) r.setWitnessA(u.getWitnessA());
            if (u.getWitnessB() != null) r.setWitnessB(u.getWitnessB());
            if (u.getProofNo() != null) r.setProofNo(u.getProofNo());
            if (u.getNoteMask() != null) r.setNoteMask(u.getNoteMask());
            if (u.getCloseKind() != null) r.setCloseKind(u.getCloseKind());
            if (u.getVoidFlag() != null) r.setVoidFlag(u.getVoidFlag());
            if (u.getDelFlag() != null) r.setDelFlag(u.getDelFlag());
            if (u.getUpdateTime() != null) r.setUpdateTime(u.getUpdateTime());
            return 1;
        });

        when(trailMapper.insert(any(TNslpCaseTrail.class))).thenAnswer(inv -> {
            TNslpCaseTrail t = inv.getArgument(0);
            t.setId(Long.valueOf(++idSeq));
            trailStore.add(t);
            return 1;
        });
        when(trailMapper.selectList(any())).thenAnswer(inv -> {
            List<String[]> cs = clauses(inv.getArgument(0));
            List<TNslpCaseTrail> out = new ArrayList<TNslpCaseTrail>();
            for (TNslpCaseTrail t : trailStore) {
                if (trailMatches(t, cs)) {
                    out.add(t);
                }
            }
            return out;
        });

        when(pointMapper.selectList(any())).thenAnswer(inv -> {
            List<String[]> cs = clauses(inv.getArgument(0));
            List<TNslpPoint> out = new ArrayList<TNslpPoint>();
            for (TNslpPoint p : pointStore) {
                if (pointMatches(p, cs)) {
                    out.add(p);
                }
            }
            return out;
        });

        when(zoneMapper.selectOne(any())).thenAnswer(inv -> {
            List<String[]> cs = clauses(inv.getArgument(0));
            for (TNslpZoneBook z : zoneStore) {
                if (zoneMatches(z, cs)) {
                    return z;
                }
            }
            return null;
        });
        when(zoneMapper.updateById(any(TNslpZoneBook.class))).thenAnswer(inv -> {
            TNslpZoneBook u = inv.getArgument(0);
            for (TNslpZoneBook z : zoneStore) {
                if (z.getId().equals(u.getId())) {
                    if (u.getStatus() != null) z.setStatus(u.getStatus());
                    if (u.getUpdateTime() != null) z.setUpdateTime(u.getUpdateTime());
                    return 1;
                }
            }
            return 0;
        });

        pointStore.add(point(1L, "PT0001", "QN00", 0));
        pointStore.add(point(2L, "PT0002", "QN01", 0));
        zoneStore.add(zone(0L, "QN00", 0));
        zoneStore.add(zone(1L, "QN01", 1));
    }

    private static TNslpPoint point(Long id, String no, String zoneNo, int delFlag) {
        TNslpPoint p = new TNslpPoint();
        p.setId(id);
        p.setPointNo(no);
        p.setZoneNo(zoneNo);
        p.setDelFlag(delFlag);
        return p;
    }

    private static TNslpZoneBook zone(Long id, String no, int status) {
        TNslpZoneBook z = new TNslpZoneBook();
        z.setId(id);
        z.setZoneNo(no);
        z.setStatus(status);
        z.setDelFlag(0);
        return z;
    }

    private static CaseIntake intake(String biz, String pointNo, String zoneNo,
                                     Integer occ, String reading) {
        CaseIntake i = new CaseIntake();
        i.setBizNo(biz);
        i.setPointNo(pointNo);
        i.setZoneNo(zoneNo);
        i.setOccNum(occ);
        i.setCheckReading(reading);
        return i;
    }

    private TNslpCaseFlow firstCase() {
        return caseStore.get(0);
    }

    private List<TNslpCaseTrail> trailsOf(Long id) {
        List<TNslpCaseTrail> out = new ArrayList<TNslpCaseTrail>();
        for (TNslpCaseTrail t : trailStore) {
            if (t.getCaseId().equals(id)) {
                out.add(t);
            }
        }
        return out;
    }

    private int countAction(Long id, String action) {
        int n = 0;
        for (TNslpCaseTrail t : trailsOf(id)) {
            if (action.equals(t.getAction())) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------
    // 立
    // ------------------------------------------------------------------

    @Test
    public void intakeStartsAtReceivedIgnoringReportedStage() {
        CaseIntake i = intake("AJ001", "PT0001", "QN00", 2, "56.15");
        // 报文自带关次 3：只当参考，落库以后端数出来的第 0 关为准
        i.setReportStage(Integer.valueOf(3));
        i.setNote("夜场反映");
        TNslpCaseFlow r = service.intake(i);

        assertEquals(Integer.valueOf(0), r.getStage());
        assertEquals(Integer.valueOf(CaseFlowRules.STATUS_ACTIVE), r.getStatus());
        // 手机端敲进来的数回显成数：折算前先取一位小数逢半进一
        assertEquals("56.2", r.getCheckReading().toPlainString());
        assertEquals("夜场反映", r.getContent());
        assertEquals(Integer.valueOf(2), r.getOccNum());
        // 留痕：受件立条＋头一句按语，两笔各记各的
        assertEquals(CaseFlowRules.ACT_RECEIVE, trailsOf(r.getId()).get(0).getAction());
        assertEquals(CaseFlowRules.ACT_NOTE, trailsOf(r.getId()).get(1).getAction());
        assertEquals(1, service.countOpen());
    }

    @Test
    public void intakeNamesEveryMissingInput() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.intake(intake("AJ", " ", null, null, null)));
        assertTrue(e.getMessage().contains("点位码"));
        assertTrue(e.getMessage().contains("区册代号"));
        assertTrue(e.getMessage().contains("处数"));
        assertTrue(e.getMessage().contains("核查读数"));
        assertThrows(IllegalArgumentException.class, () -> service.intake(null));
        // 起不起来：主档一行没有
        assertEquals(0, caseStore.size());
        assertEquals(0, service.countOpen());
    }

    @Test
    public void oneOpenCasePerPoint() {
        service.intake(intake("AJ1", "PT0001", "QN00", 1, "55"));
        // 前一条还停在中间关（在办的）：第二条立不起来
        assertThrows(IllegalStateException.class,
                () -> service.intake(intake("AJ2", "PT0001", "QN00", 1, "55")));

        // 另一个点位照常立
        TNslpCaseFlow other = service.intake(intake("AJ3", "PT0002", "QN01", 1, "60"));
        assertNotNull(other);
        assertEquals(2, service.countOpen());

        // 撤了名的点不影响立件本身（门槛在进核查那一关看）
        assertEquals(2, caseStore.size());
    }

    @Test
    public void cancelBeforeIntakeTouchesNothing() {
        // 正式立起来之前撤回去：不进主档，不占名额
        assertTrue(service.cancelBeforeIntake("PT0001", "群众改主意了"));
        assertEquals(0, caseStore.size());
        assertEquals(0, trailStore.size());
        // 立起来以后这条道不认
        service.intake(intake("AJ1", "PT0001", "QN00", 1, "55"));
        assertFalse(service.cancelBeforeIntake("PT0001", null));
    }

    // ------------------------------------------------------------------
    // 挪
    // ------------------------------------------------------------------

    @Test
    public void checkGateRequiresPointOnRegister() {
        TNslpCaseFlow r = service.intake(intake("AJ1", "PT0001", "QN00", 1, "55"));
        // 底册撤名：进不了核查
        pointStore.get(0).setDelFlag(1);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.advance(r.getId(), "去核查"));
        assertTrue(e.getMessage().contains("核查"));
        assertTrue(e.getMessage().contains("撤名"));
        // 整条仍留在原地，留痕也没添挪前那一笔
        assertEquals(Integer.valueOf(0), service.selectTNslpCaseFlowById(r.getId()).getStage());
        assertEquals(0, countAction(r.getId(), CaseFlowRules.ACT_FORWARD));

        // 重新挂名：进得去
        pointStore.get(0).setDelFlag(0);
        service.advance(r.getId(), "去核查");
        assertEquals(Integer.valueOf(1), service.selectTNslpCaseFlowById(r.getId()).getStage());
        assertEquals(1, countAction(r.getId(), CaseFlowRules.ACT_FORWARD));
    }

    @Test
    public void reviewGateWaitsForOrderAccept() {
        TNslpCaseFlow started = openTo(1);
        TNslpCaseFlow r = service.advance(started.getId(), "进责令");
        assertEquals(Integer.valueOf(2), r.getStage());
        final Long id = r.getId();

        // 责令关核收没记成「可」：复查收尾开不了工
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.advance(id, "去复查"));
        assertTrue(e.getMessage().contains("可"));
        assertEquals(Integer.valueOf(2), service.selectTNslpCaseFlowById(id).getStage());

        // 记成「否」一样不开工
        service.recordOrderAccept(id, "否");
        assertThrows(IllegalStateException.class, () -> service.advance(id, "去复查"));

        // 记成「可」：开工
        service.recordOrderAccept(id, CaseFlowRules.ORDER_ACCEPT_OK);
        TNslpCaseFlow now = service.advance(id, "去复查");
        assertEquals(Integer.valueOf(3), now.getStage());
    }

    @Test
    public void rollbackOneStepAndFirstStageStops() {
        TNslpCaseFlow r = service.intake(intake("AJ1", "PT0001", "QN00", 1, "55"));
        // 头一关没什么可再收
        assertThrows(IllegalStateException.class, () -> service.rollback(r.getId(), "收回"));
        service.advance(r.getId(), null);
        service.advance(r.getId(), null);
        assertEquals(Integer.valueOf(2), service.selectTNslpCaseFlowById(r.getId()).getStage());
        service.rollback(r.getId(), "退回核查补料");
        assertEquals(Integer.valueOf(1), service.selectTNslpCaseFlowById(r.getId()).getStage());
        assertEquals(1, countAction(r.getId(), CaseFlowRules.ACT_BACK));
    }

    @Test
    public void secondNoteSameStageRejectedButKeptAfterRollback() {
        TNslpCaseFlow r = service.intake(intake("AJ1", "PT0001", "QN00", 1, "55"));
        service.advance(r.getId(), null); // 到核查

        assertTrue(service.note(r.getId(), "核查头一句"));
        assertEquals("核查头一句", service.selectTNslpCaseFlowById(r.getId()).getContent());
        // 同一关第二次塞话：盖不上、不另存
        assertFalse(service.note(r.getId(), "核查第二句"));
        assertEquals("核查头一句", service.selectTNslpCaseFlowById(r.getId()).getContent());
        assertEquals(1, countAction(r.getId(), CaseFlowRules.ACT_NOTE));

        // 收回核查：那句照原先后留在追溯记录末尾；重新挪到核查从头累计
        service.rollback(r.getId(), null);
        service.advance(r.getId(), null);
        assertTrue(service.note(r.getId(), "核查新的头一句"));
        assertEquals("核查新的头一句", service.selectTNslpCaseFlowById(r.getId()).getContent());
        // 追溯里两句都在，谁也没被抹
        List<TNslpCaseTrail> notes = new ArrayList<TNslpCaseTrail>();
        for (TNslpCaseTrail t : trailsOf(r.getId())) {
            if (CaseFlowRules.ACT_NOTE.equals(t.getAction())) {
                notes.add(t);
            }
        }
        assertEquals(2, notes.size());
        assertEquals("核查头一句", notes.get(0).getNote());
        assertEquals("核查新的头一句", notes.get(1).getNote());

        assertThrows(IllegalArgumentException.class, () -> service.note(r.getId(), "  "));
    }

    // ------------------------------------------------------------------
    // 封
    // ------------------------------------------------------------------

    @Test
    public void sealNeedsBothAndFreezes() {
        TNslpCaseFlow r = reachReview();

        CaseSeal bad = new CaseSeal();
        bad.setId(r.getId());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.seal(bad));
        assertTrue(e.getMessage().contains("第一名"));
        assertTrue(e.getMessage().contains("第二名"));
        assertTrue(e.getMessage().contains("复测凭证"));

        CaseSeal same = new CaseSeal();
        same.setId(r.getId());
        same.setWitnessA("张三");
        same.setWitnessB("张三");
        same.setProofNo("PZ1");
        assertTrue(assertThrows(IllegalStateException.class, () -> service.seal(same))
                .getMessage().contains("两岗"));
        // 没封上，还是没封住的件
        assertEquals(1, service.countOpen());

        CaseSeal ok = new CaseSeal();
        ok.setId(r.getId());
        ok.setWitnessA("张三");
        ok.setWitnessB("李四");
        ok.setProofNo("PZ-2026-0001");
        TNslpCaseFlow sealed = service.seal(ok);
        assertEquals(Integer.valueOf(CaseFlowRules.STATUS_TERMINAL), sealed.getStatus());
        assertEquals("PZ-2026-0001", sealed.getProofNo());
        assertEquals(0, service.countOpen());

        // 封住之后：添按语、更正内容（挪）、整条抽回，三种动作一概不接
        assertThrows(IllegalStateException.class, () -> service.note(r.getId(), "再补一句"));
        assertThrows(IllegalStateException.class, () -> service.advance(r.getId(), null));
        assertThrows(IllegalStateException.class, () -> service.rollback(r.getId(), null));
        assertThrows(IllegalStateException.class, () -> service.withdraw(r.getId(), null));
        // 名册里照常露脸
        assertEquals(1, service.selectTNslpCaseFlowList(new QueryWrapper<TNslpCaseFlow>()).size());
    }

    @Test
    public void sealOnlyAtLastStage() {
        TNslpCaseFlow r = service.intake(intake("AJ1", "PT0001", "QN00", 1, "55"));
        CaseSeal ok = new CaseSeal();
        ok.setId(r.getId());
        ok.setWitnessA("甲");
        ok.setWitnessB("乙");
        ok.setProofNo("PZ1");
        assertThrows(IllegalStateException.class, () -> service.seal(ok));
    }

    @Test
    public void withdrawClosesAtCurrentStage() {
        TNslpCaseFlow r = service.intake(intake("AJ1", "PT0001", "QN00", 1, "55"));
        service.advance(r.getId(), null);
        TNslpCaseFlow closed = service.withdraw(r.getId(), "反映人说不扰了");
        assertEquals(Integer.valueOf(CaseFlowRules.STATUS_TERMINAL), closed.getStatus());
        assertEquals(Integer.valueOf(CaseFlowRules.CLOSE_KIND_WITHDRAW), closed.getCloseKind());
        // 停在哪关收在哪关，没再往前挪
        assertEquals(Integer.valueOf(1), closed.getStage());
        assertEquals(0, service.countOpen());
        assertThrows(IllegalStateException.class, () -> service.withdraw(r.getId(), null));
    }

    @Test
    public void mergedZoneClosesAllOpenCases() {
        // QN01 在区册底里已撤出（并走）；名下两条在办件，停在哪关算哪关
        TNslpCaseFlow a = service.intake(intake("AJ-A", "PT0002", "QN01", 1, "55"));
        service.advance(a.getId(), null);
        TNslpCaseFlow b = service.intake(intake("AJ-B", "PT9999", "QN01", 1, "60"));

        int n = service.mergeZone("QN01", "区划调整并往南津");
        assertEquals(2, n);
        assertEquals(Integer.valueOf(CaseFlowRules.STATUS_TERMINAL),
                service.selectTNslpCaseFlowById(a.getId()).getStatus());
        assertEquals(Integer.valueOf(CaseFlowRules.CLOSE_KIND_MERGED),
                service.selectTNslpCaseFlowById(a.getId()).getCloseKind());
        assertEquals(Integer.valueOf(CaseFlowRules.STATUS_TERMINAL),
                service.selectTNslpCaseFlowById(b.getId()).getStatus());
        // 区册底里本来就记着已撤出，本接口不替它改册面
        assertEquals(Integer.valueOf(1), zoneStore.get(1).getStatus());
        assertEquals(0, service.countOpen());
        // 再并一回：没有在办件收口
        assertEquals(0, service.mergeZone("QN01", null));
        assertThrows(IllegalArgumentException.class, () -> service.mergeZone("QNXX", null));
        // 还在册的册（QN00）没并走：不替它收口、不替它改册面
        TNslpCaseFlow c = service.intake(intake("AJ-C", "PT0001", "QN00", 1, "50"));
        assertThrows(IllegalStateException.class, () -> service.mergeZone("QN00", null));
        assertEquals(Integer.valueOf(0), zoneStore.get(0).getStatus());
        assertEquals(Integer.valueOf(CaseFlowRules.STATUS_ACTIVE),
                service.selectTNslpCaseFlowById(c.getId()).getStatus());
    }

    @Test
    public void voidFromLastStageOnly() {
        TNslpCaseFlow r = openTo(2);
        // 作废从最后一关发起：停在责令关作不了废
        assertThrows(IllegalStateException.class, () -> service.voidCase(r.getId(), "填错了"));
        service.recordOrderAccept(r.getId(), "可");
        service.advance(r.getId(), null);
        assertTrue(service.voidCase(r.getId(), "数据填串了点位"));
        assertEquals(Integer.valueOf(1), service.selectTNslpCaseFlowById(r.getId()).getVoidFlag());
        // 作废的件名册照常露脸，只是不给动手的把手；且不占「没封住」名额
        assertEquals(0, service.countOpen());
        assertThrows(IllegalStateException.class, () -> service.note(r.getId(), "晚了"));
        // 再废一回：不多记
        assertFalse(service.voidCase(r.getId(), null));
        assertEquals(1, countAction(r.getId(), CaseFlowRules.ACT_VOID));
    }

    // ------------------------------------------------------------------
    // 捋
    // ------------------------------------------------------------------

    @Test
    public void traceReplaysStageFromTrailOnly() {
        TNslpCaseFlow r = reachReview();
        CaseSeal ok = new CaseSeal();
        ok.setId(r.getId());
        ok.setWitnessA("甲");
        ok.setWitnessB("乙");
        ok.setProofNo("PZ7");
        service.seal(ok);

        CaseTrace t = service.trace(r.getId());
        // 行面 stage 不喂进来：当前档由留痕这一串放出来
        assertEquals(3, t.getReplayStage());
        assertEquals("复查收尾", t.getReplayStageText());
        assertTrue(t.isReplayTerminal());
        // 受件立条 1、挪前 3、封住 1
        assertEquals(5, t.getEntries().size());
        assertEquals(CaseFlowRules.ACT_RECEIVE, t.getEntries().get(0).getAction());
        assertEquals(CaseFlowRules.ACT_SEAL, t.getEntries().get(4).getAction());
    }

    @Test
    public void ledgerBalancesAfterRollbackAndReplay() {
        TNslpCaseFlow r = reachReview();
        // 从复查收尾一关一关收回，再从头捋一遍
        service.rollback(r.getId(), null);
        service.rollback(r.getId(), null);
        service.rollback(r.getId(), null);
        service.advance(r.getId(), null);
        service.advance(r.getId(), null);
        service.recordOrderAccept(r.getId(), "可");
        service.advance(r.getId(), null);

        CaseLedger l = service.ledger(r.getId(), null);
        // 挪前 6 笔、收回 3 笔，捋出来还在末关
        assertEquals(6, l.getForwardCount());
        assertEquals(3, l.getBackCount());
        assertEquals(3, l.getReplayStage());
        assertTrue(l.isStageBalanced(), "逐关进出该轧平：" + l.getUnbalancedStages());
        assertTrue(l.getUnbalancedStages().isEmpty());

        // 截任意一关单独核也自洽：只看这一天的笔数（全发生在今天）
        CaseLedger today = service.ledger(r.getId(), new Date());
        assertEquals(6, today.getForwardCount());
        assertEquals(3, today.getBackCount());
        assertEquals(3, today.getReplayStage());
        assertTrue(today.isStageBalanced());

        // 追一个不存在的件
        assertThrows(IllegalArgumentException.class, () -> service.ledger(999L, null));
    }

    @Test
    public void sameInstantTwoEntriesKeepOrderAndSeq() {
        TNslpCaseFlow r = service.intake(intake("AJ1", "PT0001", "QN00", 1, "55"));
        // 同一刻补两笔留痕：各记各的先后，seq 断先后，不许并成一条也不许吞一条
        Date same = new Date();
        TNslpCaseTrail t1 = new TNslpCaseTrail();
        t1.setCaseId(r.getId());
        t1.setStage(0);
        t1.setAction(CaseFlowRules.ACT_NOTE);
        t1.setNote("通道甲");
        t1.setTrailTime(same);
        t1.setSeq(Integer.valueOf(0));
        t1.setDelFlag(0);
        TNslpCaseTrail t2 = new TNslpCaseTrail();
        t2.setCaseId(r.getId());
        t2.setStage(0);
        t2.setAction(CaseFlowRules.ACT_NOTE);
        t2.setNote("通道乙");
        t2.setTrailTime(same);
        t2.setSeq(Integer.valueOf(1));
        t2.setDelFlag(0);
        trailMapper.insert(t1);
        trailMapper.insert(t2);

        CaseTrace trace = service.trace(r.getId());
        int idxJia = -1;
        int idxYi = -1;
        List<CaseTrace.Entry> entries = trace.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            if ("通道甲".equals(entries.get(i).getNote())) {
                idxJia = i;
            }
            if ("通道乙".equals(entries.get(i).getNote())) {
                idxYi = i;
            }
        }
        assertTrue(idxJia >= 0 && idxYi > idxJia, "同刻两笔各记各的先后，甲在乙前");
        // 受件立条 1 笔＋同刻两笔：三笔各记各的，没并、没吞
        assertEquals(3, trailStore.size());
    }

    // ------------------------------------------------------------------
    // 帮衬：把件开到指定关
    // ------------------------------------------------------------------

    /** 开一条新件并推进到 stage（受件 0 起算）；推进 2→3 前自动记核收「可」 */
    private TNslpCaseFlow openTo(int stage) {
        TNslpCaseFlow r = service.intake(intake("AJ-" + (++idSeq), "PT0001", "QN00", 1, "55"));
        int cur = 0;
        while (cur < stage) {
            if (cur == 2) {
                service.recordOrderAccept(r.getId(), "可");
            }
            r = service.advance(r.getId(), null);
            cur++;
        }
        return r;
    }

    private TNslpCaseFlow reachReview() {
        return openTo(3);
    }
}
