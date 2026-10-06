package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import com.fc.v2.common.conf.NslpProperties;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.auto.TNslpZoneBook;
import com.fc.v2.model.custom.LedgerQuery;
import com.fc.v2.model.custom.LedgerView;
import com.fc.v2.service.ITNslpPointService;
import com.fc.v2.service.ITNslpZoneBookService;
import com.fc.v2.service.support.LedgerRules;

/**
 * 名册：三视图同源处数、分页取配置、全等/前缀、分层可见、空表合法、缺笔不入底数、
 * 入了册的行都查得着。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TNslpPointLedgerServiceImplTest {

    @Mock
    private TNslpPointMapper nslpPointMapper;
    @Mock
    private ITNslpZoneBookService nslpZoneBookService;
    @Mock
    private ITNslpPointService nslpPointService;

    @Mock
    private NslpProperties nslpProperties;

    @InjectMocks
    private TNslpPointLedgerServiceImpl service;

    private List<TNslpPoint> store;

    private static final Pattern EQ = Pattern.compile(
            "(\\w+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}");

    @BeforeEach
    public void setUp() {
        store = new ArrayList<TNslpPoint>();

        // 每页 2 条，好验分页（页大小只取区局配置那一处）
        when(nslpProperties.effectiveLedgerPageSize()).thenReturn(2);

        when(nslpZoneBookService.listAll()).thenAnswer(inv -> zones());

        when(nslpPointMapper.selectList(any())).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            String seg = w.getSqlSegment() == null ? "" : w.getSqlSegment();
            Map<String, Object> pm = w.getParamNameValuePairs();
            List<TNslpPoint> out = new ArrayList<TNslpPoint>();
            for (TNslpPoint p : store) {
                if (matches(p, seg, pm)) {
                    out.add(p);
                }
            }
            return out;
        });
        // 步子回放：直接以行上 flow_stage 作答（测试里由我们摆），null 照旧 null
        when(nslpPointService.currentStageMap(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            Map<Long, Integer> m = new HashMap<Long, Integer>();
            for (TNslpPoint p : store) {
                if (ids.contains(p.getId())) {
                    m.put(p.getId(), p.getFlowStage());
                }
            }
            return m;
        });
    }

    private List<TNslpZoneBook> zones() {
        return Arrays.asList(
                zone("QN00", "甲层", 0),
                zone("QN01", "乙层", 1),
                zone("QN02", "丙层", 0));
    }

    private static TNslpZoneBook zone(String no, String kind, int status) {
        TNslpZoneBook z = new TNslpZoneBook();
        z.setZoneNo(no);
        z.setZoneName(no + "全称");
        z.setZoneKind(kind);
        z.setStatus(status);
        z.setDelFlag(0);
        return z;
    }

    private TNslpPoint row(long id, String code, String zone, int year, int stage, int stop,
                           String createBy, String updateBy) {
        TNslpPoint p = new TNslpPoint();
        p.setId(id);
        p.setPointNo(code);
        p.setZoneNo(zone);
        p.setZoneName(zone + "全称");
        p.setRegYear(year);
        p.setFlowStage(stage < 0 ? null : stage);
        p.setStopFlag(stop);
        p.setDelFlag(0);
        p.setCreateBy(createBy);
        p.setUpdateBy(updateBy);
        store.add(p);
        return p;
    }

    private LedgerQuery q(String scope, String layer, List<String> assigned, Integer year) {
        LedgerQuery q = new LedgerQuery();
        q.setScope(scope);
        q.setViewerLayer(layer);
        q.setAssignedZoneNos(assigned);
        q.setRegYear(year);
        q.setPageNum(1);
        return q;
    }

    @Test
    public void threeViewsShareSameCountAndGroupsSum() {
        // 甲层 3 行：在走2（待测/已测）、压卡1；乙层1行；丙层1行
        row(1, "PT01", "QN00", 2026, 0, 0, "u", null);
        row(2, "PT02", "QN00", 2026, 1, 0, "u", null);
        row(3, "PT03", "QN00", 2026, 2, 0, "u", null);
        row(4, "PT04", "QN01", 2026, 0, 0, "u", null);
        row(5, "PT05", "QN02", 2026, 0, 1, "u", null); // 叫停

        LedgerQuery q = q(LedgerQuery.SCOPE_BUREAU, null, Arrays.asList("QN00", "QN01", "QN02"), 2026);
        List<LedgerView> views = service.threeViews(q);
        int total = views.get(0).getTotal();
        assertEquals(5, total);
        for (LedgerView v : views) {
            assertEquals(5, v.getTotal());
            int sum = 0;
            for (int c : v.getGroupCounts().values()) {
                sum += c;
            }
            assertEquals(5, sum);
        }
        // 按入册情形：在走2（不含叫停）、已压卡1、已叫停1，再加乙层在走1 → 在走3
        LedgerView byIntake = views.get(2);
        assertEquals(Integer.valueOf(3), byIntake.getGroupCounts().get(LedgerRules.BUCKET_LIVE));
        assertEquals(Integer.valueOf(1), byIntake.getGroupCounts().get(LedgerRules.BUCKET_SEALED));
        assertEquals(Integer.valueOf(1), byIntake.getGroupCounts().get(LedgerRules.BUCKET_STOPPED));
        // 按区层：甲层3、乙层1、丙层1
        LedgerView byKind = views.get(0);
        assertEquals(Integer.valueOf(3), byKind.getGroupCounts().get("甲层"));
        assertEquals(Integer.valueOf(1), byKind.getGroupCounts().get("乙层"));
        assertEquals(Integer.valueOf(1), byKind.getGroupCounts().get("丙层"));
    }

    @Test
    public void layerSeesOnlyOwnKindAndBureauOnlyAssigned() {
        row(1, "PT01", "QN00", 2026, 0, 0, "u", null);
        row(2, "PT02", "QN02", 2026, 0, 0, "u", null);

        // 本层甲层只见甲层 1 行，不拿邻层凑
        LedgerView layer = service.view(q(LedgerQuery.SCOPE_LAYER, "甲层", null, 2026),
                LedgerRules.VIEW_BY_KIND);
        assertEquals(1, layer.getTotal());

        // 区局四层都看得见，但限划归区册：只划 QN02 只见 1 行
        LedgerView bureau = service.view(q(LedgerQuery.SCOPE_BUREAU, null,
                Arrays.asList("QN02"), 2026), LedgerRules.VIEW_BY_KIND);
        assertEquals(1, bureau.getTotal());

        // 一个区册都没划：留空，不凑
        LedgerView none = service.view(q(LedgerQuery.SCOPE_BUREAU, null,
                new ArrayList<String>(), 2026), LedgerRules.VIEW_BY_KIND);
        assertEquals(0, none.getTotal());
        assertTrue(none.getRows().isEmpty());
    }

    @Test
    public void exactAndPrefixSearch() {
        row(1, "PT001", "QN00", 2026, 0, 0, "u", null);
        row(2, "PT002", "QN00", 2026, 0, 0, "u", null);
        row(3, "XX999", "QN00", 2026, 0, 0, "u", null);
        LedgerQuery q = q(LedgerQuery.SCOPE_BUREAU, null, Arrays.asList("QN00"), 2026);
        q.setPointNo("PT"); // 前缀
        assertEquals(2, service.view(q, LedgerRules.VIEW_BY_ZONE).getTotal());
        q.setPointNo("PT001"); // 全等
        assertEquals(1, service.view(q, LedgerRules.VIEW_BY_ZONE).getTotal());
    }

    @Test
    public void emptyIsLegalAndPagingFromConfig() {
        LedgerQuery q = q(LedgerQuery.SCOPE_LAYER, "甲层", null, 2026);
        LedgerView v = service.view(q, LedgerRules.VIEW_BY_KIND);
        assertEquals(0, v.getTotal());
        assertEquals(0, v.getPages());
        assertTrue(v.getRows().isEmpty());

        // 本年度一行没建是空结果；拿邻层凑视为缺陷——甲层查问不返回乙层行
        row(1, "PT01", "QN01", 2026, 0, 0, "u", null); // 乙层
        assertEquals(0, service.view(q, LedgerRules.VIEW_BY_KIND).getTotal());
    }

    @Test
    public void everyCreatedRowFindableAndPaging() {
        for (int i = 1; i <= 5; i++) {
            row(i, "PT0" + i, "QN00", 2026, 0, 0, "u", null);
        }
        LedgerQuery q = q(LedgerQuery.SCOPE_BUREAU, null, Arrays.asList("QN00"), 2026);
        q.setPageNum(1);
        LedgerView p1 = service.view(q, LedgerRules.VIEW_BY_ZONE);
        assertEquals(5, p1.getTotal());
        assertEquals(2, p1.getRows().size()); // 配置每页2条
        assertEquals(3, p1.getPages());
        q.setPageNum(3);
        assertEquals(1, service.view(q, LedgerRules.VIEW_BY_ZONE).getRows().size());
    }

    @Test
    public void unattributedRowsExcludedButListed() {
        row(1, "PT01", "QN00", 2026, 0, 0, "u", null);
        row(2, "PT02", "QN00", 2026, 0, 0, "  ", null); // 两处都指不出人
        LedgerQuery q = q(LedgerQuery.SCOPE_BUREAU, null, Arrays.asList("QN00"), 2026);
        assertEquals(1, service.view(q, LedgerRules.VIEW_BY_ZONE).getTotal());
        // 行没被清除，仍在库，单列给复核补笔
        assertEquals(1, service.listUnattributed(q).size());
        assertEquals(2, store.size());
    }

    @Test
    public void zoneNameMismatchListedNotCleared() {
        TNslpPoint p = row(1, "PT01", "QN00", 2026, 0, 0, "u", null);
        p.setZoneName("旧的错误全称"); // 与区册底不一致
        List<TNslpPoint> bad = service.listZoneNameMismatches(2026);
        assertEquals(1, bad.size());
        // 不连带清除
        assertEquals(1, store.size());
    }

    private static boolean matches(TNslpPoint p, String seg, Map<String, Object> pm) {
        Matcher m = EQ.matcher(seg);
        while (m.find()) {
            String col = m.group(1);
            Object val = pm.get(m.group(2));
            Object f;
            if ("del_flag".equals(col)) {
                f = p.getDelFlag();
            } else if ("reg_year".equals(col)) {
                f = p.getRegYear();
            } else {
                f = null;
            }
            if (f == null || !f.equals(val)) {
                return false;
            }
        }
        return true;
    }
}
