package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TNslpLimitLineMapper;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.model.auto.TNslpLimitLine;
import com.fc.v2.model.custom.LevelQuery;
import com.fc.v2.model.custom.LimitLineVerdict;

/**
 * 限值线 Service 口径测试：询问时刻在位版本（补算旧账不串新版）、两路一致、
 * 撤下线不进现行清单但照主键点得到、空交班日、脏线剔除。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TNslpLimitLineServiceImplTest {

    @Mock
    private TNslpLimitLineMapper limitLineMapper;
    @Mock
    private TNslpPointMapper pointMapper;
    @InjectMocks
    private TNslpLimitLineServiceImpl service;

    private static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static Date t(String s) {
        try {
            return F.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int seq = 0;

    private static TNslpLimitLine line(String code, String th1, String th2, String th3,
                                       String start, String end, Integer priority, Integer status) {
        TNslpLimitLine r = new TNslpLimitLine();
        r.setId((long) (++seq));
        r.setRuleCode(code);
        r.setObjKind("昼段");
        r.setTh1Max(new BigDecimal(th1));
        r.setTh2Max(new BigDecimal(th2));
        r.setTh3Max(new BigDecimal(th3));
        r.setEffStart(t(start));
        r.setEffEnd(end == null ? null : t(end));
        r.setPriority(priority);
        r.setStatus(status);
        r.setDelFlag(0);
        return r;
    }

    private List<TNslpLimitLine> all;

    @BeforeEach
    public void setUp() {
        all = new ArrayList<TNslpLimitLine>();
        // L1 两版：旧版 [2020,2023) 阈值偏松，新版 [2023,null) 偏紧
        TNslpLimitLine oldVer = line("L1", "50", "60", "70", "2020-01-01 00:00:00", "2023-01-01 00:00:00", 1, 0);
        TNslpLimitLine newVer = line("L1", "40", "50", "60", "2023-01-01 00:00:00", null, 1, 0);
        // L2 在位但交班次序更高
        TNslpLimitLine l2 = line("L2", "45", "55", "70", "2022-01-01 00:00:00", null, 9, 0);
        // L3 已撤下
        TNslpLimitLine withdrawn = line("L3", "45", "55", "70", "2019-01-01 00:00:00", "2021-01-01 00:00:00", 1, 1);
        // L4 在位但脏：封顶 130
        TNslpLimitLine dirty = line("L4", "45", "55", "130", "2023-01-01 00:00:00", null, 1, 0);
        all.addAll(Arrays.asList(oldVer, newVer, l2, withdrawn, dirty));

        when(limitLineMapper.selectList(any(QueryWrapper.class))).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            // 取 SQL 片段时 MP 才把参数落袋；按片段里有没有 rule_code 区分两路查询
            boolean byCode = w.getSqlSegment() != null && w.getSqlSegment().contains("rule_code");
            if (!byCode) {
                return new ArrayList<TNslpLimitLine>(all);
            }
            String code = null;
            for (Object v : w.getParamNameValuePairs().values()) {
                if (v instanceof String) {
                    code = (String) v;
                    break;
                }
            }
            List<TNslpLimitLine> hit = new ArrayList<TNslpLimitLine>();
            if (code != null) {
                for (TNslpLimitLine r : all) {
                    if (code.equals(r.getRuleCode())) {
                        hit.add(r);
                    }
                }
            }
            return hit;
        });
        when(limitLineMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TNslpLimitLine r : all) {
                if (r.getId().equals(id)) {
                    return r;
                }
            }
            return null;
        });
    }

    @Test
    public void historicalPointUsesVersionActiveThen() {
        // 2022 年补算：52 在旧版（50/60/70）落第2层；若错拿新版（40/50/60）会落第3层——串档
        LimitLineVerdict old = service.resolve("L1", new BigDecimal("52"), t("2022-06-01 00:00:00"));
        assertEquals(2, old.getLevel());
        // 同一读数 2024 年问，新版在位，52 落第3层
        LimitLineVerdict now = service.resolve("L1", new BigDecimal("52"), t("2024-06-01 00:00:00"));
        assertEquals(3, now.getLevel());
        // 钉住的是当年那版的起用那一一日
        assertEquals(t("2020-01-01 00:00:00"), old.getEffStart());
        assertEquals(t("2023-01-01 00:00:00"), now.getEffStart());
        // 交班刻（旧版尾不含）：旧版已不开口，新版头含恰好在位 → 落新版的第3层
        LimitLineVerdict handover = service.resolve("L1", new BigDecimal("52"), t("2023-01-01 00:00:00"));
        assertEquals(3, handover.getLevel());
        assertEquals(t("2023-01-01 00:00:00"), handover.getEffStart());
    }

    @Test
    public void resolveAndResolveTopGiveSameAnswer() {
        Date at = t("2024-06-01 00:00:00");
        LimitLineVerdict byCode = service.resolve("L2", new BigDecimal("55.0"), at);
        LimitLineVerdict top = service.resolveTop(new BigDecimal("55.0"), at);
        // 整表取优开口者正是高次序的 L2，两答一致
        assertEquals("L2", top.getRuleCode());
        assertEquals(byCode.getLevel(), top.getLevel());
        assertEquals(3, top.getLevel());
        assertEquals("第3层", top.getAnswer());
    }

    @Test
    public void withdrawnExcludedFromAvailableButReachableById() {
        Date at = t("2024-06-01 00:00:00");
        List<TNslpLimitLine> avail = service.listAvailable(at);
        boolean hasWithdrawn = false;
        for (TNslpLimitLine r : avail) {
            if ("L3".equals(r.getRuleCode())) {
                hasWithdrawn = true;
            }
        }
        assertEquals(false, hasWithdrawn);
        // 撤下线不参与回算
        assertEquals(0, service.evaluate("L3", new BigDecimal("55"), at));
        // 但库里仍是一行，照主键点得到
        Long withdrawnId = null;
        for (TNslpLimitLine r : all) {
            if ("L3".equals(r.getRuleCode())) {
                withdrawnId = r.getId();
            }
        }
        assertNotNull(service.selectTNslpLimitLineById(withdrawnId));
        assertEquals(false, service.usable(withdrawnId, at));
    }

    @Test
    public void dirtyLineNeverSpeaksButErrorIsSpecific() {
        Date at = t("2024-06-01 00:00:00");
        LimitLineVerdict v = service.resolve("L4", new BigDecimal("60"), at);
        assertEquals(0, v.getLevel());
        assertEquals("不归层", v.getAnswer());
        assertNotNull(v.getConfigError());
        assertTrue(v.getConfigError().contains("封顶限值"));
        // 脏线不进整表取优与现行清单
        for (TNslpLimitLine r : service.listAvailable(at)) {
            assertEquals(false, "L4".equals(r.getRuleCode()));
        }
    }

    @Test
    public void noLineMeansNotLevelAndBlankPinningFields() {
        LimitLineVerdict v = service.resolve("NOPE", new BigDecimal("55"), t("2024-06-01 00:00:00"));
        assertEquals(0, v.getLevel());
        assertEquals("不归层", v.getAnswer());
        assertNull(v.getRuleCode());
        assertNull(v.getEffStart());
    }

    @Test
    public void batchComputesOnceAndIsConsistent() {
        Date at = t("2024-06-01 00:00:00");
        LevelQuery q1 = new LevelQuery("p1", "L2", new BigDecimal("55.0"), at);
        q1.setObjKind("昼段");
        LevelQuery q2 = new LevelQuery("p2", null, new BigDecimal("55.0"), at);
        q2.setObjKind("昼段");
        List<LimitLineVerdict> vs = service.resolveBatch(Arrays.asList(q1, q2));
        // 按线代号与整表取优同一刻同一读数，两答一致
        assertEquals(vs.get(0).getLevel(), vs.get(1).getLevel());
        assertEquals("L2", vs.get(0).getRuleCode());
        assertEquals("L2", vs.get(1).getRuleCode());
    }

    @Test
    public void nullEffEndRunsForever() {
        // 新版 L2 交班日空，2099 年仍在位
        assertTrue(service.usable(idOf("L2"), t("2099-01-01 00:00:00")));
    }

    private Long idOf(String code) {
        for (TNslpLimitLine r : all) {
            if (code.equals(r.getRuleCode())) {
                return r.getId();
            }
        }
        return null;
    }
}
