package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.conf.NslpProperties;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.auto.TNslpZoneBook;
import com.fc.v2.model.custom.LedgerQuery;
import com.fc.v2.model.custom.LedgerView;
import com.fc.v2.service.ITNslpPointLedgerService;
import com.fc.v2.service.ITNslpPointService;
import com.fc.v2.service.ITNslpZoneBookService;
import com.fc.v2.service.support.LedgerRules;

/**
 * 声管岗名册 Service业务层处理。
 *
 * 底数只在 {@link #baseRows} 取一遍：三种摆法、页首处数、数据库那一趟都消费这同一批行，
 * 前端不另数。页大小只取区局配置 {@link NslpProperties}；可见范围按区层切，不拿邻层凑；
 * 空表是合法结果。步子分档只问点位服务那一处回放，不在名册再算。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TNslpPointLedgerServiceImpl implements ITNslpPointLedgerService {

    /** 行挂靠断了、点不出区层时的分档键（只如实标不明，不拿邻层类别凑） */
    private static final String KIND_UNKNOWN = "（区层不明）";

    @Resource
    private TNslpPointMapper nslpPointMapper;

    @Resource
    private ITNslpZoneBookService nslpZoneBookService;

    @Resource
    private ITNslpPointService nslpPointService;

    @Resource
    private NslpProperties nslpProperties;

    @Override
    public LedgerView view(LedgerQuery query, String view) {
        List<TNslpPoint> base = baseRows(query);
        Map<Long, Integer> stages = stagesOf(base);
        Map<String, TNslpZoneBook> zoneByNo = zoneByNo();

        Map<String, Integer> groupCounts = new LinkedHashMap<String, Integer>();
        for (TNslpPoint p : base) {
            String key = groupKey(view, p, zoneByNo, stages);
            Integer c = groupCounts.get(key);
            groupCounts.put(key, Integer.valueOf(c == null ? 1 : c.intValue() + 1));
        }

        int pageSize = nslpProperties.effectiveLedgerPageSize();
        int total = base.size();
        int pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int pageNum = query != null && query.getPageNum() != null && query.getPageNum().intValue() > 0
                ? query.getPageNum().intValue() : 1;
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);

        LedgerView v = new LedgerView();
        v.setView(view);
        v.setRegYear(query == null ? null : query.getRegYear());
        v.setTotal(total);
        v.setGroupCounts(groupCounts);
        v.setRows(new ArrayList<TNslpPoint>(base.subList(from, to)));
        v.setPageNum(pages == 0 ? 1 : Math.min(pageNum, pages == 0 ? 1 : pages));
        v.setPageSize(pageSize);
        v.setPages(pages);
        return v;
    }

    @Override
    public List<LedgerView> threeViews(LedgerQuery query) {
        // 同一份底数摆三遍：baseRows 在各自 view 里取的也是同一套筛选，为钉死同源，
        // 这里先取一遍底数，三个视图的 total 直接断言相等（都由 baseRows 同一条件产出）。
        LedgerView byKind = view(query, LedgerRules.VIEW_BY_KIND);
        LedgerView byZone = view(query, LedgerRules.VIEW_BY_ZONE);
        LedgerView byIntake = view(query, LedgerRules.VIEW_BY_INTAKE);
        if (byKind.getTotal() != byZone.getTotal() || byKind.getTotal() != byIntake.getTotal()) {
            // 三处底数出自同一查询，理论不可达；真到了这一步就是有第二条路子在绕，直接抛出
            throw new IllegalStateException("三种摆法处数对不住："
                    + byKind.getTotal() + "/" + byZone.getTotal() + "/" + byIntake.getTotal());
        }
        List<LedgerView> views = new ArrayList<LedgerView>();
        views.add(byKind);
        views.add(byZone);
        views.add(byIntake);
        return views;
    }

    @Override
    public List<TNslpPoint> baseRows(LedgerQuery query) {
        if (query == null) {
            return new ArrayList<TNslpPoint>();
        }
        QueryWrapper<TNslpPoint> w = new QueryWrapper<TNslpPoint>().eq("del_flag", 0);
        if (query.getRegYear() != null) {
            w.eq("reg_year", query.getRegYear());
        }
        List<TNslpPoint> rows = this.nslpPointMapper.selectList(w.orderByAsc("id"));

        Map<String, TNslpZoneBook> zoneByNo = zoneByNo();
        List<Long> ids = new ArrayList<Long>();
        for (TNslpPoint p : rows) {
            ids.add(p.getId());
        }
        Map<Long, Integer> stages = nslpPointService.currentStageMap(ids);

        List<TNslpPoint> kept = new ArrayList<TNslpPoint>();
        for (TNslpPoint p : rows) {
            TNslpZoneBook book = p.getZoneNo() == null ? null : zoneByNo.get(p.getZoneNo());
            String kind = book == null ? null : book.getZoneKind();
            // 看得见的范围按区层切
            if (LedgerQuery.SCOPE_LAYER.equals(query.getScope())) {
                if (!LedgerRules.visibleToLayer(kind, query.getViewerLayer())) {
                    continue;
                }
            } else if (LedgerQuery.SCOPE_BUREAU.equals(query.getScope())) {
                if (!LedgerRules.visibleToBureau(p.getZoneNo(), query.getAssignedZoneNos())) {
                    continue;
                }
            } else {
                // 值守性质没说清，一行也不敞给它看（不默认全可见）
                continue;
            }
            // 三个查问位置：全等与前缀都认
            if (!LedgerRules.matchesExactOrPrefix(p.getPointNo(), query.getPointNo())) {
                continue;
            }
            if (!LedgerRules.matchesExactOrPrefix(p.getZoneNo(), query.getZoneNo())) {
                continue;
            }
            if (!LedgerRules.matchesExactOrPrefix(kind, query.getZoneKind())) {
                continue;
            }
            // 建册、改线两处都指不出人的，按没办完，不进在册底数
            if (!LedgerRules.attributed(p.getCreateBy(), p.getUpdateBy())) {
                continue;
            }
            // 顺手把系统数出的当下步放回行上供页面呈现，但分档只以 stages 为准
            p.setFlowStage(stages.get(p.getId()));
            kept.add(p);
        }
        return kept;
    }

    @Override
    public List<TNslpPoint> listUnattributed(LedgerQuery query) {
        if (query == null) {
            return new ArrayList<TNslpPoint>();
        }
        QueryWrapper<TNslpPoint> w = new QueryWrapper<TNslpPoint>().eq("del_flag", 0);
        if (query.getRegYear() != null) {
            w.eq("reg_year", query.getRegYear());
        }
        List<TNslpPoint> out = new ArrayList<TNslpPoint>();
        for (TNslpPoint p : this.nslpPointMapper.selectList(w.orderByAsc("id"))) {
            if (!LedgerRules.attributed(p.getCreateBy(), p.getUpdateBy())) {
                out.add(p);
            }
        }
        return out;
    }

    @Override
    public List<TNslpPoint> listZoneNameMismatches(Integer regYear) {
        Map<String, TNslpZoneBook> zoneByNo = zoneByNo();
        QueryWrapper<TNslpPoint> w = new QueryWrapper<TNslpPoint>().eq("del_flag", 0);
        if (regYear != null) {
            w.eq("reg_year", regYear);
        }
        List<TNslpPoint> out = new ArrayList<TNslpPoint>();
        for (TNslpPoint p : this.nslpPointMapper.selectList(w.orderByAsc("id"))) {
            TNslpZoneBook book = p.getZoneNo() == null ? null : zoneByNo.get(p.getZoneNo());
            if (book != null
                    && com.fc.v2.service.support.PointArchiveRules.zoneNameMismatch(
                            book, p.getZoneName())) {
                // 两处各留一份说法算缺陷：单列出来，以区册为准纠正，但不连带把行清掉
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private Map<String, TNslpZoneBook> zoneByNo() {
        Map<String, TNslpZoneBook> map = new LinkedHashMap<String, TNslpZoneBook>();
        for (TNslpZoneBook b : nslpZoneBookService.listAll()) {
            if (b.getZoneNo() != null) {
                map.put(b.getZoneNo(), b);
            }
        }
        return map;
    }

    private Map<Long, Integer> stagesOf(List<TNslpPoint> rows) {
        List<Long> ids = new ArrayList<Long>();
        for (TNslpPoint p : rows) {
            ids.add(p.getId());
        }
        return nslpPointService.currentStageMap(ids);
    }

    private String groupKey(String view, TNslpPoint p, Map<String, TNslpZoneBook> zoneByNo,
                            Map<Long, Integer> stages) {
        if (LedgerRules.VIEW_BY_ZONE.equals(view)) {
            return p.getZoneNo() == null ? KIND_UNKNOWN : p.getZoneNo();
        }
        if (LedgerRules.VIEW_BY_INTAKE.equals(view)) {
            Integer stage = stages.get(p.getId());
            return LedgerRules.intakeBucket(stage, p.getStopFlag());
        }
        // 默认按区层类别
        TNslpZoneBook book = p.getZoneNo() == null ? null : zoneByNo.get(p.getZoneNo());
        String kind = book == null ? null : book.getZoneKind();
        return kind == null ? KIND_UNKNOWN : kind;
    }
}
