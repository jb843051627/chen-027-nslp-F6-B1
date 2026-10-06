package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TNslpLimitLineMapper;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.model.auto.TNslpLimitLine;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.custom.LevelQuery;
import com.fc.v2.model.custom.LimitLineVerdict;
import com.fc.v2.service.ITNslpLimitLineService;
import com.fc.v2.service.support.LimitLineRules;

/**
 * 功能区昼夜间限值线 Service业务层处理（rule-eval 形状）。
 *
 * 口径全部收在 {@link LimitLineRules}：大屏折线、点值、后端逐条回算走同一份算法。
 * 在位判定：起用那一一日头含、交班那一一日尾不含、情形记 0；交班日留空按一直管下去认。
 * 补算往年点位用询问时刻在位的版本，不拿此刻最新版套旧账。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TNslpLimitLineServiceImpl implements ITNslpLimitLineService {

    @javax.annotation.Resource
    private TNslpLimitLineMapper nslpLimitLineMapper;

    @javax.annotation.Resource
    private TNslpPointMapper nslpPointMapper;

    @Override
    public TNslpLimitLine selectTNslpLimitLineById(Long id) {
        // 已撤下的线不参与回算、不进现行清单，但库里仍是一行，照主键仍然点得到
        return this.nslpLimitLineMapper.selectById(id);
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        // 形参与返回一概不动；内部改走唯一回话出口，保证与整表取优两答一致
        return resolve(ruleCode, input, at).getLevel();
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        return resolveTop(input, at).getLevel();
    }

    @Override
    public List<TNslpLimitLine> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TNslpLimitLine>();
        }
        List<TNslpLimitLine> active = new ArrayList<TNslpLimitLine>();
        for (TNslpLimitLine line : snapshot()) {
            if (LimitLineRules.checkLine(line) == null && LimitLineRules.activeAt(line, at)) {
                active.add(line);
            }
        }
        Collections.sort(active, LimitLineRules.winnerOrder());
        return active;
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TNslpLimitLine line = this.nslpLimitLineMapper.selectById(id);
        return line != null
                && LimitLineRules.checkLine(line) == null
                && LimitLineRules.activeAt(line, at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    @Override
    public LimitLineVerdict resolve(String ruleCode, BigDecimal input, Date at) {
        BigDecimal aligned = LimitLineRules.alignOne(input);
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return LimitLineVerdict.notLevel(at, aligned, null);
        }
        // 按线代号只在该码自己的版本行里挑询问时刻在位的那一版（补算旧账不串档）
        TNslpLimitLine winner = null;
        String dirty = null;
        for (TNslpLimitLine line : snapshotByCode(ruleCode)) {
            if (!LimitLineRules.activeAt(line, at)) {
                continue;
            }
            String err = LimitLineRules.checkLine(line);
            if (err != null) {
                // 在位但脏：剔掉，不参与开口，只把逐格毛病递出去
                if (dirty == null) {
                    dirty = err;
                }
                continue;
            }
            if (winner == null || LimitLineRules.winnerOrder().compare(line, winner) < 0) {
                winner = line;
            }
        }
        if (winner == null) {
            return LimitLineVerdict.notLevel(at, aligned, dirty);
        }
        return LimitLineVerdict.of(winner, LimitLineRules.levelOf(winner, input), at, aligned);
    }

    @Override
    public LimitLineVerdict resolveTop(BigDecimal input, Date at) {
        BigDecimal aligned = LimitLineRules.alignOne(input);
        if (input == null || at == null) {
            return LimitLineVerdict.notLevel(at, aligned, null);
        }
        // 整表取优：在位且干净的线里按交班次序、线代号字面先后选开口者。
        // 选中之后与按线代号取值走同一个贴层出口（含同一份量程与精度口径），两答必一致。
        List<TNslpLimitLine> active = listAvailable(at);
        if (active.isEmpty()) {
            return LimitLineVerdict.notLevel(at, aligned, null);
        }
        TNslpLimitLine winner = active.get(0);
        return LimitLineVerdict.of(winner, LimitLineRules.levelOf(winner, input), at, aligned);
    }

    @Override
    public List<LimitLineVerdict> resolveBatch(List<LevelQuery> queries) {
        List<LimitLineVerdict> out = new ArrayList<LimitLineVerdict>();
        if (queries == null || queries.isEmpty()) {
            return out;
        }
        // 库只照这一面：整批一次取数、内存内分时刻挑版本。
        // 大屏两截折线、折线上的点值、后端逐条回算都消费这同一批发出去的回话。
        List<TNslpLimitLine> all = snapshot();
        Map<String, List<TNslpLimitLine>> byCode = indexByCode(all);
        for (LevelQuery q : queries) {
            out.add(decide(q, all, byCode));
        }
        return out;
    }

    @Override
    public List<TNslpLimitLine> listByZoneQuarter(String zoneNo, int year, int quarter) {
        if (zoneNo == null || zoneNo.trim().isEmpty() || quarter < 1 || quarter > 4) {
            return new ArrayList<TNslpLimitLine>();
        }
        // 区册点位上钉着的线代号（归层时钉进区册那一行的线代号）
        List<TNslpPoint> points = this.nslpPointMapper.selectList(new QueryWrapper<TNslpPoint>()
                .eq("del_flag", 0).eq("zone_no", zoneNo).isNotNull("pinned_rule_code"));
        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<String>();
        for (TNslpPoint p : points) {
            if (p.getPinnedRuleCode() != null && !p.getPinnedRuleCode().trim().isEmpty()) {
                codes.add(p.getPinnedRuleCode());
            }
        }
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, (quarter - 1) * 3, 1, 0, 0, 0);
        Date qStart = c.getTime();
        c.add(Calendar.MONTH, 3);
        Date qEnd = c.getTime();

        List<TNslpLimitLine> hit = new ArrayList<TNslpLimitLine>();
        for (TNslpLimitLine line : snapshot()) {
            if (!codes.contains(line.getRuleCode())) {
                continue;
            }
            if (LimitLineRules.checkLine(line) != null || line.getStatus() == null
                    || line.getStatus() != 0 || line.getEffStart() == null) {
                continue;
            }
            // 在位窗 [eff_start, eff_end) 与该季度 [qStart, qEnd) 有交集即取；
            // eff_end 留空为一直管下去，只要起用日在季度结束之前就算管得着
            if (line.getEffStart().compareTo(qEnd) >= 0) {
                continue;
            }
            if (line.getEffEnd() != null && line.getEffEnd().compareTo(qStart) <= 0) {
                continue;
            }
            hit.add(line);
        }
        Collections.sort(hit, new java.util.Comparator<TNslpLimitLine>() {
            @Override
            public int compare(TNslpLimitLine a, TNslpLimitLine b) {
                return a.getEffStart().compareTo(b.getEffStart());
            }
        });
        return hit;
    }

    @Override
    public String configError(Long id) {
        if (id == null) {
            return null;
        }
        TNslpLimitLine line = this.nslpLimitLineMapper.selectById(id);
        return line == null ? null : LimitLineRules.checkLine(line);
    }

    // ------------------------------------------------------------------
    // 内部：一面快照 + 同一次计算
    // ------------------------------------------------------------------

    /** 全表一面：撤下线也在其中（照主键点得到），是否在位/干净由口径处判 */
    private List<TNslpLimitLine> snapshot() {
        return this.nslpLimitLineMapper.selectList(
                new QueryWrapper<TNslpLimitLine>().eq("del_flag", 0));
    }

    private List<TNslpLimitLine> snapshotByCode(String ruleCode) {
        return this.nslpLimitLineMapper.selectList(new QueryWrapper<TNslpLimitLine>()
                .eq("del_flag", 0).eq("rule_code", ruleCode));
    }

    private Map<String, List<TNslpLimitLine>> indexByCode(List<TNslpLimitLine> all) {
        Map<String, List<TNslpLimitLine>> map = new HashMap<String, List<TNslpLimitLine>>();
        for (TNslpLimitLine line : all) {
            if (line.getRuleCode() == null) {
                continue;
            }
            List<TNslpLimitLine> bucket = map.get(line.getRuleCode());
            if (bucket == null) {
                bucket = new ArrayList<TNslpLimitLine>();
                map.put(line.getRuleCode(), bucket);
            }
            bucket.add(line);
        }
        return map;
    }

    /**
     * 批内单条问话的唯一裁决口：带 ruleCode 走按线代号取值，不带走整表取优。
     * 与 {@link #resolve}/{@link #resolveTop} 用同一套挑版本与贴层口径。
     */
    private LimitLineVerdict decide(LevelQuery q, List<TNslpLimitLine> all,
                                   Map<String, List<TNslpLimitLine>> byCode) {
        BigDecimal input = q.getInput();
        Date at = q.getAt();
        BigDecimal aligned = LimitLineRules.alignOne(input);
        if (input == null || at == null) {
            return LimitLineVerdict.notLevel(at, aligned, null);
        }
        List<TNslpLimitLine> candidates;
        String code = q.getRuleCode();
        String kind = q.getObjKind();
        if (code != null && !code.trim().isEmpty()) {
            List<TNslpLimitLine> bucket = byCode.get(code);
            candidates = bucket == null ? new ArrayList<TNslpLimitLine>() : bucket;
        } else {
            candidates = all;
        }
        TNslpLimitLine winner = null;
        String dirty = null;
        for (TNslpLimitLine line : candidates) {
            if (kind != null && !kind.trim().isEmpty()
                    && (line.getObjKind() == null || !kind.equals(line.getObjKind()))) {
                // 昼段折线只问昼段的线，夜段只问夜段，不许串段
                continue;
            }
            if (!LimitLineRules.activeAt(line, at)) {
                continue;
            }
            String err = LimitLineRules.checkLine(line);
            if (err != null) {
                if (code != null && !code.trim().isEmpty() && dirty == null) {
                    dirty = err;
                }
                continue;
            }
            if (winner == null || LimitLineRules.winnerOrder().compare(line, winner) < 0) {
                winner = line;
            }
        }
        if (winner == null) {
            return LimitLineVerdict.notLevel(at, aligned, dirty);
        }
        return LimitLineVerdict.of(winner, LimitLineRules.levelOf(winner, input), at, aligned);
    }
}
