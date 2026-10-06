package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.mapper.auto.TNslpPointStepMapper;
import com.fc.v2.mapper.auto.TNslpZoneBookMapper;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.auto.TNslpPointStep;
import com.fc.v2.model.auto.TNslpZoneBook;
import com.fc.v2.model.custom.PointArchiveSubmit;
import com.fc.v2.service.ITNslpLimitLineService;
import com.fc.v2.service.ITNslpZoneBookService;
import com.fc.v2.service.support.PointFlowRules;

/**
 * 建档五道筛、数量三趟重轧、同点名额、三态步子回放与压卡冻结、存量旧行兼容：
 * point/step 两本内存账，QueryWrapper 按列名值对过滤。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TNslpPointServiceImplArchiveTest {

    @Mock
    private TNslpPointMapper baseMapper;
    @Mock
    private TNslpZoneBookMapper nslpZoneBookMapper;
    @Mock
    private ITNslpLimitLineService nslpLimitLineService;
    @Mock
    private TNslpPointStepMapper nslpPointStepMapper;
    @Mock
    private ITNslpZoneBookService nslpZoneBookService;

    @InjectMocks
    private TNslpPointServiceImpl service;

    private List<TNslpPoint> pointStore;
    private List<TNslpPointStep> stepStore;
    private Map<String, TNslpZoneBook> zoneMap;
    private long idSeq;
    private long stepSeq;

    private static final Pattern EQ = Pattern.compile(
            "(\\w+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}");
    private static final Pattern ISNULL = Pattern.compile("(\\w+)\\s+IS\\s+NULL");

    @BeforeEach
    public void setUp() {
        pointStore = new ArrayList<TNslpPoint>();
        stepStore = new ArrayList<TNslpPointStep>();
        zoneMap = new HashMap<String, TNslpZoneBook>();
        idSeq = 100;
        stepSeq = 0;
        zoneMap.put("QN00", zone(0L, "QN00", "云桥区临江片区全称", "甲层", 0));
        zoneMap.put("QN01", zone(1L, "QN01", "南津区北岸旧册", "乙层", 1));
        zoneMap.put("QN02", zone(2L, "QN02", "丙层工业片区", "丙层", 0));

        when(nslpZoneBookService.getByZoneNo(any())).thenAnswer(inv -> {
            String z = inv.getArgument(0);
            return z == null ? null : zoneMap.get(z.trim());
        });

        when(baseMapper.insert(any(TNslpPoint.class))).thenAnswer(inv -> {
            TNslpPoint p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(Long.valueOf(++idSeq));
            }
            if (p.getCreateTime() == null) {
                p.setCreateTime(new Date());
            }
            pointStore.add(p);
            return 1;
        });
        when(baseMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TNslpPoint p : pointStore) {
                if (p.getId().equals(id)) {
                    return p;
                }
            }
            return null;
        });
        when(baseMapper.selectList(any())).thenAnswer(inv -> filterPoints(inv.getArgument(0)));
        when(baseMapper.updateById(any(TNslpPoint.class))).thenAnswer(inv -> {
            TNslpPoint u = inv.getArgument(0);
            for (int i = 0; i < pointStore.size(); i++) {
                if (pointStore.get(i).getId().equals(u.getId())) {
                    pointStore.set(i, mergePoint(pointStore.get(i), u));
                    return 1;
                }
            }
            return 0;
        });

        when(nslpPointStepMapper.insert(any(TNslpPointStep.class))).thenAnswer(inv -> {
            TNslpPointStep s = inv.getArgument(0);
            s.setId(Long.valueOf(++stepSeq));
            if (s.getCreateTime() == null) {
                s.setCreateTime(new Date());
            }
            stepStore.add(s);
            return 1;
        });
        // 留痕查问按 point_id（= 或 IN）与 del_flag 过滤，再按 time/seq/id 排好
        when(nslpPointStepMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            String seg = w.getSqlSegment() == null ? "" : w.getSqlSegment();
            Map<String, Object> pm = w.getParamNameValuePairs();
            java.util.Set<Long> idFilter = parsePointIdFilter(seg, pm);
            Integer delFilter = parseDelFilter(seg, pm);
            List<TNslpPointStep> out = new ArrayList<TNslpPointStep>();
            for (TNslpPointStep s : stepStore) {
                if (idFilter != null && !idFilter.contains(s.getPointId())) {
                    continue;
                }
                if (delFilter != null && !delFilter.equals(s.getDelFlag())) {
                    continue;
                }
                out.add(s);
            }
            out.sort((a, b) -> {
                int c = a.getPointId().compareTo(b.getPointId());
                if (c != 0) {
                    return c;
                }
                c = a.getStepTime().compareTo(b.getStepTime());
                if (c != 0) {
                    return c;
                }
                c = Integer.compare(a.getSeq() == null ? 0 : a.getSeq(),
                        b.getSeq() == null ? 0 : b.getSeq());
                return c != 0 ? c : a.getId().compareTo(b.getId());
            });
            return out;
        });
        when(nslpPointStepMapper.selectCount(any())).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            w.getSqlSegment();
            Map<String, Object> pm = w.getParamNameValuePairs();
            int n = 0;
            for (TNslpPointStep s : stepStore) {
                if (stepMatches(s, w.getSqlSegment(), pm)) {
                    n++;
                }
            }
            return Integer.valueOf(n);
        });
    }

    // ------------------------------------------------------------------
    // 建档：数量轧法、五道筛
    // ------------------------------------------------------------------

    @Test
    public void archiveComputesLackAndAlignsZoneAndStartsAtPending() {
        TNslpPoint p = service.archive(submit(null, "PT0001", "QN00", 2026, "30", "12", "建档岗甲"));
        assertEquals(Integer.valueOf(18), p.getLackNum());
        assertEquals(Integer.valueOf(0), p.getFlowStage()); // 起在待测
        assertEquals("云桥区临江片区全称", p.getZoneName()); // 冗余全称以区册为准
        assertEquals(Integer.valueOf(0), p.getZoneId());
        assertEquals("建档岗甲", p.getCreateBy());
        // 起册留痕已压一笔；当下步由留痕回放为待测
        assertEquals(Integer.valueOf(0), service.currentStage(p.getId()));
        assertEquals(1, stepsOf(p.getId()).size());
        assertEquals(PointFlowRules.ACT_START, stepsOf(p.getId()).get(0).getAction());
    }

    @Test
    public void lackExamples() {
        assertEquals(Integer.valueOf(18),
                service.archive(submit(null, "A", "QN00", 2026, "30", "12", "u")).getLackNum());
        assertEquals(Integer.valueOf(13),
                service.archive(submit(null, "B", "QN00", 2026, "20", "7", "u")).getLackNum());
        assertEquals(Integer.valueOf(0),
                service.archive(submit(null, "C", "QN00", 2026, "9", "9", "u")).getLackNum());
    }

    @Test
    public void codeScreens() {
        service.archive(submit(null, "PT1", "QN00", 2026, "10", "1", "u"));
        // 空码
        assertThrows(IllegalArgumentException.class,
                () -> service.archive(submit(null, " ", "QN00", 2026, "10", "1", "u")));
        // 同年度同区册同码仍在册 → 拒
        IllegalArgumentException dup = assertThrows(IllegalArgumentException.class,
                () -> service.archive(submit(null, "PT1", "QN00", 2026, "10", "1", "u")));
        assertTrue(dup.getMessage().contains("拒掉"));
        // 同码但另一年度：过了同年度同册那道，仍被「同点只容一行在走」第四道挡住
        assertThrows(IllegalStateException.class,
                () -> service.archive(submit(null, "PT1", "QN00", 2027, "10", "1", "u")));

        // 更新对象正是那一行自己 → 按更新办，不当新增挡（同码那道把自身摘出去）
        TNslpPoint first = pointStore.get(0);
        PointArchiveSubmit upd = submit(first.getId(), "PT1", "QN00", 2026, "12", "2", "u2");
        TNslpPoint updated = service.archive(upd);
        assertEquals(Integer.valueOf(10), updated.getLackNum());
        assertEquals("u2", updated.getUpdateBy());
    }

    @Test
    public void zoneScreens() {
        // 代号找不到
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.archive(submit(null, "Z1", "QN99", 2026, "10", "1", "u")))
                .getMessage().contains("区册代号这一栏对不上"));
        // 已撤出的册不收新行
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.archive(submit(null, "Z2", "QN01", 2026, "10", "1", "u")))
                .getMessage().contains("不再收新行"));
    }

    @Test
    public void quantityScreensNameColumns() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.archive(submit(null, "Q1", "QN00", 2026, null, "1", "u")))
                .getMessage().contains("核定应测次数"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.archive(submit(null, "Q2", "QN00", 2026, "3.5", "1", "u")))
                .getMessage().contains("整数"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.archive(submit(null, "Q3", "QN00", 2026, "-1", "0", "u")))
                .getMessage().contains("负数"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.archive(submit(null, "Q4", "QN00", 2026, "5", "9", "u")))
                .getMessage().contains("反超"));
    }

    @Test
    public void fifthScreenOperatorRequired() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.archive(submit(null, "O1", "QN00", 2026, "5", "1", "  ")))
                .getMessage().contains("落笔人"));
    }

    // ------------------------------------------------------------------
    // 名额：两家单位同日各报 / 换人重报都不许叠两行；压卡、叫停腾名额
    // ------------------------------------------------------------------

    @Test
    public void oneLiveRowPerPointUntilSealedOrStopped() {
        TNslpPoint first = service.archive(
                submit(null, "SAME", "QN00", 2026, "10", "1", "甲维保"));
        // 另一家维护单位同日再报（同年同册同码）：第一道就拒
        IllegalArgumentException twoUnits = assertThrows(IllegalArgumentException.class,
                () -> service.archive(submit(null, "SAME", "QN00", 2026, "10", "1", "乙维保")));
        assertTrue(twoUnits.getMessage().contains("拒掉"));
        // 换人重报、改报别的在册区册：过了同册同码那道，仍被同点在走名额挡住
        IllegalStateException moveUnit = assertThrows(IllegalStateException.class,
                () -> service.archive(submit(null, "SAME", "QN02", 2026, "10", "1", "甲维保")));
        assertTrue(moveUnit.getMessage().contains("第二行开不出来"));

        // 走到已测再压卡
        service.advanceFlow(first.getId(), "u", null);
        service.sealFlow(first.getId(), "u", null);
        // 压卡后名额腾出，同点位立得新行
        TNslpPoint second = service.archive(submit(null, "SAME", "QN00", 2026, "10", "0", "u"));
        assertNotNull(second);
        assertFalse(second.getId().equals(first.getId()));
    }

    @Test
    public void stopFreesSlotButRowRemainsAndFrozen() {
        TNslpPoint p = service.archive(submit(null, "ST", "QN00", 2026, "10", "1", "u"));
        service.stopFlow(p.getId(), "u", null);
        // 叫停后同点位开得出新行
        TNslpPoint p2 = service.archive(submit(null, "ST", "QN00", 2026, "10", "0", "u"));
        assertNotNull(p2);
        // 叫停的行仍在册、没删
        assertNotNull(service.currentStage(p.getId()) == null ? null : service.currentStage(p.getId()));
        // 叫停后推不动、退不了
        assertThrows(IllegalStateException.class, () -> service.advanceFlow(p.getId(), "u", null));
        assertThrows(IllegalStateException.class, () -> service.rollbackFlow(p.getId(), "u", null));
    }

    // ------------------------------------------------------------------
    // 数量三趟重轧
    // ------------------------------------------------------------------

    @Test
    public void lackRecomputedOnCorrectAndRezone() {
        TNslpPoint p = service.archive(submit(null, "R", "QN00", 2026, "30", "12", "u"));
        assertEquals(Integer.valueOf(18), p.getLackNum());
        // 第二趟：已达标被更正为 20 → 10
        TNslpPoint c = service.correctGot(p.getId(), 20, "u");
        assertEquals(Integer.valueOf(10), c.getLackNum());
        // 更正反超核定挡回
        assertThrows(IllegalArgumentException.class, () -> service.correctGot(p.getId(), 31, "u"));
        // 第三趟：改挂到别的区册，同法再轧（仍 10）
        TNslpPoint z = service.rezone(p.getId(), "QN02", "u");
        assertEquals("QN02", z.getZoneNo());
        assertEquals("丙层工业片区", z.getZoneName());
        assertEquals(Integer.valueOf(10), z.getLackNum());
        // 改挂到已撤出的册挡回
        assertThrows(IllegalArgumentException.class, () -> service.rezone(p.getId(), "QN01", "u"));
    }

    // ------------------------------------------------------------------
    // 三态步子
    // ------------------------------------------------------------------

    @Test
    public void flowAdvanceRollbackKeepsTrailThenSealFreezes() {
        TNslpPoint p = service.archive(submit(null, "F", "QN00", 2026, "10", "1", "u"));
        assertEquals(Integer.valueOf(0), service.currentStage(p.getId()));

        service.advanceFlow(p.getId(), "u", "测完一回");
        assertEquals(Integer.valueOf(1), service.currentStage(p.getId()));
        // 待测只能从已测退；此时在已测，退得回
        service.rollbackFlow(p.getId(), "u", "重来");
        assertEquals(Integer.valueOf(0), service.currentStage(p.getId()));
        // 先前那趟推进记录照旧留着（起册/推进/退回 三笔），不与后补并排
        List<TNslpPointStep> steps = stepsOf(p.getId());
        assertEquals(3, steps.size());
        long forwards = steps.stream().filter(s -> PointFlowRules.ACT_FORWARD.equals(s.getAction())).count();
        assertEquals(1L, forwards);

        // 再推进、压卡
        service.advanceFlow(p.getId(), "u", null);
        service.sealFlow(p.getId(), "u", null);
        assertEquals(Integer.valueOf(2), service.currentStage(p.getId()));

        // 压卡后添不进、动不得、撤不掉：推进/退回/改数量/改挂/改线全挡
        assertThrows(IllegalStateException.class, () -> service.advanceFlow(p.getId(), "u", null));
        assertThrows(IllegalStateException.class, () -> service.rollbackFlow(p.getId(), "u", null));
        assertThrows(IllegalStateException.class, () -> service.correctGot(p.getId(), 5, "u"));
        assertThrows(IllegalStateException.class, () -> service.rezone(p.getId(), "QN02", "u"));
        assertThrows(IllegalStateException.class,
                () -> service.archive(submit(p.getId(), "F", "QN00", 2026, "11", "1", "u")));
    }

    @Test
    public void sealOnlyFromMeasured() {
        TNslpPoint p = service.archive(submit(null, "F2", "QN00", 2026, "10", "1", "u"));
        // 待测压不了
        assertThrows(IllegalStateException.class, () -> service.sealFlow(p.getId(), "u", null));
    }

    // ------------------------------------------------------------------
    // 存量旧行：步数/码/核验串空，也容得下，不当已压卡，且占名额
    // ------------------------------------------------------------------

    @Test
    public void legacyRowToleratedAndOccupiesSlot() {
        TNslpPoint legacy = new TNslpPoint();
        legacy.setId(Long.valueOf(++idSeq));
        legacy.setPointNo("LEG");
        legacy.setZoneNo("QN00");
        legacy.setRegYear(2026);
        legacy.setNeedNum(null);
        legacy.setGotNum(null);
        legacy.setLackNum(null);
        legacy.setFlowStage(null);       // 步数空
        legacy.setStopFlag(0);
        legacy.setDelFlag(0);
        pointStore.add(legacy);

        // 一看不报错：当下步回 null（不是压卡）
        assertNull(service.currentStage(legacy.getId()));
        // 在走的旧行占名额（空步也被 occupiesSlot 认作在走）：同码同年同册新行在第一道就被拒，
        // 说明系统没把它当已压卡而凭空放出名额
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.archive(submit(null, "LEG", "QN00", 2026, "10", "1", "u")))
                .getMessage().contains("拒掉"));
    }

    @Test
    public void retestPathSharesSameAlgorithm() {
        TNslpPoint p = service.archiveFromRetest("RT", "QN00", 2026, 30, 12, "复测岗");
        assertEquals(Integer.valueOf(18), p.getLackNum());
        assertEquals("复测核收带出", p.getSrcKind());
    }

    // ------------------------------------------------------------------
    // 内存账工具
    // ------------------------------------------------------------------

    private List<TNslpPointStep> stepsOf(Long pointId) {
        List<TNslpPointStep> out = new ArrayList<TNslpPointStep>();
        for (TNslpPointStep s : stepStore) {
            if (s.getPointId().equals(pointId)) {
                out.add(s);
            }
        }
        return out;
    }

    private static PointArchiveSubmit submit(Long id, String code, String zone, int year,
                                             String need, String got, String op) {
        PointArchiveSubmit s = new PointArchiveSubmit();
        s.setId(id);
        s.setPointNo(code);
        s.setZoneNo(zone);
        s.setRegYear(Integer.valueOf(year));
        s.setNeedNum(need);
        s.setGotNum(got);
        s.setOperator(op);
        return s;
    }

    private static TNslpZoneBook zone(Long id, String no, String name, String kind, int status) {
        TNslpZoneBook z = new TNslpZoneBook();
        z.setId(id);
        z.setZoneNo(no);
        z.setZoneName(name);
        z.setZoneKind(kind);
        z.setStatus(Integer.valueOf(status));
        z.setDelFlag(0);
        return z;
    }

    private List<TNslpPoint> filterPoints(QueryWrapper<TNslpPoint> w) {
        String seg = w.getSqlSegment() == null ? "" : w.getSqlSegment();
        Map<String, Object> pm = w.getParamNameValuePairs();
        List<TNslpPoint> out = new ArrayList<TNslpPoint>();
        for (TNslpPoint p : pointStore) {
            if (pointMatches(p, seg, pm)) {
                out.add(p);
            }
        }
        return out;
    }

    private boolean pointMatches(TNslpPoint p, String seg, Map<String, Object> pm) {
        Matcher eq = EQ.matcher(seg);
        while (eq.find()) {
            String col = eq.group(1);
            Object val = pm.get(eq.group(2));
            Object f = pointField(p, col);
            if (!valueEq(f, val)) {
                return false;
            }
        }
        Matcher isn = ISNULL.matcher(seg);
        while (isn.find()) {
            if (pointField(p, isn.group(1)) != null) {
                return false;
            }
        }
        return true;
    }

    private boolean stepMatches(TNslpPointStep s, String seg, Map<String, Object> pm) {
        Matcher eq = EQ.matcher(seg);
        while (eq.find()) {
            String col = eq.group(1);
            Object val = pm.get(eq.group(2));
            Object f;
            if ("point_id".equals(col)) {
                f = s.getPointId();
            } else if ("step_time".equals(col)) {
                f = s.getStepTime();
            } else if ("del_flag".equals(col)) {
                f = s.getDelFlag();
            } else {
                f = null;
            }
            if (!valueEq(f, val)) {
                return false;
            }
        }
        return true;
    }

    private static java.util.regex.Pattern IN_PID =
            java.util.regex.Pattern.compile("point_id\\s+IN\\s*\\(([^)]*)\\)");

    @SuppressWarnings("unchecked")
    private static java.util.Set<Long> parsePointIdFilter(String seg, Map<String, Object> pm) {
        java.util.Set<Long> ids = new java.util.HashSet<Long>();
        boolean has = false;
        Matcher eqm = EQ.matcher(seg);
        while (eqm.find()) {
            if ("point_id".equals(eqm.group(1))) {
                Object v = pm.get(eqm.group(2));
                if (v instanceof Long) {
                    ids.add((Long) v);
                    has = true;
                }
            }
        }
        Matcher inm = IN_PID.matcher(seg);
        while (inm.find()) {
            Matcher pm2 = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(inm.group(1));
            while (pm2.find()) {
                Object v = pm.get(pm2.group(1));
                if (v instanceof Long) {
                    ids.add((Long) v);
                    has = true;
                }
            }
        }
        return has ? ids : null;
    }

    private static Integer parseDelFilter(String seg, Map<String, Object> pm) {
        Matcher eqm = EQ.matcher(seg);
        while (eqm.find()) {
            if ("del_flag".equals(eqm.group(1))) {
                Object v = pm.get(eqm.group(2));
                if (v instanceof Number) {
                    return Integer.valueOf(((Number) v).intValue());
                }
            }
        }
        return null;
    }

    private static Object pointField(TNslpPoint p, String col) {
        if ("del_flag".equals(col)) {
            return p.getDelFlag();
        }
        if ("point_no".equals(col)) {
            return p.getPointNo();
        }
        if ("reg_year".equals(col)) {
            return p.getRegYear();
        }
        if ("zone_no".equals(col)) {
            return p.getZoneNo();
        }
        return null;
    }

    private static boolean valueEq(Object f, Object val) {
        if (f == null || val == null) {
            return f == val;
        }
        return f.equals(val) || String.valueOf(f).equals(String.valueOf(val));
    }

    private static TNslpPoint mergePoint(TNslpPoint old, TNslpPoint u) {
        if (u.getPointNo() != null) {
            old.setPointNo(u.getPointNo());
        }
        if (u.getZoneNo() != null) {
            old.setZoneNo(u.getZoneNo());
        }
        if (u.getZoneName() != null) {
            old.setZoneName(u.getZoneName());
        }
        if (u.getZoneId() != null) {
            old.setZoneId(u.getZoneId());
        }
        if (u.getRegYear() != null) {
            old.setRegYear(u.getRegYear());
        }
        if (u.getNeedNum() != null) {
            old.setNeedNum(u.getNeedNum());
        }
        if (u.getGotNum() != null) {
            old.setGotNum(u.getGotNum());
        }
        if (u.getLackNum() != null) {
            old.setLackNum(u.getLackNum());
        }
        if (u.getFlowStage() != null) {
            old.setFlowStage(u.getFlowStage());
        }
        if (u.getStopFlag() != null) {
            old.setStopFlag(u.getStopFlag());
        }
        if (u.getStatus() != null) {
            old.setStatus(u.getStatus());
        }
        if (u.getMaintainUnit() != null) {
            old.setMaintainUnit(u.getMaintainUnit());
        }
        if (u.getUpdateBy() != null) {
            old.setUpdateBy(u.getUpdateBy());
        }
        if (u.getUpdateTime() != null) {
            old.setUpdateTime(u.getUpdateTime());
        }
        return old;
    }
}
