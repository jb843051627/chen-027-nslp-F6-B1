package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TNslpPointMapper;
import com.fc.v2.mapper.auto.TNslpPointStepMapper;
import com.fc.v2.mapper.auto.TNslpZoneBookMapper;
import com.fc.v2.model.auto.TNslpLimitLine;
import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.auto.TNslpPointStep;
import com.fc.v2.model.auto.TNslpZoneBook;
import com.fc.v2.model.custom.LevelQuery;
import com.fc.v2.model.custom.LimitLineVerdict;
import com.fc.v2.model.custom.PointArchiveSubmit;
import com.fc.v2.service.ITNslpLimitLineService;
import com.fc.v2.service.ITNslpPointService;
import com.fc.v2.service.ITNslpZoneBookService;
import com.fc.v2.service.support.LimitLineRules;
import com.fc.v2.service.support.PointArchiveRules;
import com.fc.v2.service.support.PointFlowRules;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 声环境监测点位底册Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TNslpPointServiceImpl extends ServiceImpl<TNslpPointMapper, TNslpPoint> implements ITNslpPointService {

    @Autowired
    private TNslpZoneBookMapper nslpZoneBookMapper;

    @Autowired
    private ITNslpLimitLineService nslpLimitLineService;

    @javax.annotation.Resource
    private TNslpPointStepMapper nslpPointStepMapper;

    @Autowired
    private ITNslpZoneBookService nslpZoneBookService;

    @Override
    public TNslpPoint selectTNslpPointById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TNslpPoint>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TNslpPoint> selectTNslpPointList(Wrapper<TNslpPoint> queryWrapper) {
        QueryWrapper<TNslpPoint> wrapper = new QueryWrapper<TNslpPoint>();
        com.github.pagehelper.PageHelper.startPage(1, 10);
        wrapper.eq("status", 0);
        return this.baseMapper.selectList(wrapper);
    }

    @Override
    public int insertTNslpPoint(TNslpPoint record) {
        if (record == null) {
            return 0;
        }

        record.setCreateBy(record.getPointNo());
        TNslpZoneBook refArch = nslpZoneBookMapper.selectOne(new QueryWrapper<TNslpZoneBook>()
                .eq("id", record.getZoneId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        record.setZoneNo(refArch.getZoneNo());
        if (StringUtils.isNotEmpty(record.getPointNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TNslpPoint>()
                    .eq("point_no", record.getPointNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        int remIn = record.getNeedNum() == null ? 0 : record.getNeedNum();
        int remOut = record.getGotNum() == null ? 0 : record.getGotNum();
        record.setLackNum(remIn + remOut);

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTNslpPoint(TNslpPoint record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (record.getId() != null && StringUtils.isNotEmpty(record.getPointNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TNslpPoint>()
                    .eq("point_no", record.getPointNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TNslpPoint>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTNslpPointByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTNslpPointById(Long id) {
        return this.baseMapper.deleteById(id);
    }

    @Override
    public LimitLineVerdict pinPointLevel(Long pointId, BigDecimal input, Date at, String objKind) {
        TNslpPoint point = this.baseMapper.selectOne(new QueryWrapper<TNslpPoint>()
                .eq("id", pointId).eq("del_flag", 0));
        if (point == null || input == null || at == null) {
            return LimitLineVerdict.notLevel(at, LimitLineRules.alignOne(input), null);
        }
        LevelQuery q = new LevelQuery(point.getPointNo(), point.getPinnedRuleCode(), input, at);
        q.setObjKind(objKind);
        // 单个也走批量那唯一裁决口：按线代号取值与整表取优、大屏点值与后端回算，从无第二条算法
        LimitLineVerdict verdict = nslpLimitLineService.resolveBatch(java.util.Collections.singletonList(q)).get(0);
        applyVerdict(point, verdict);
        return verdict;
    }

    @Override
    public List<LimitLineVerdict> pinPointLevelBatch(List<LevelQuery> queries) {
        List<LimitLineVerdict> verdicts = nslpLimitLineService.resolveBatch(queries);
        if (queries == null || queries.isEmpty()) {
            return verdicts;
        }
        // 问话里用 pointNo 认点位；一批回话一次算完，再逐行钉线/清空白
        Map<String, TNslpPoint> pointMap = new HashMap<String, TNslpPoint>();
        for (TNslpPoint p : this.baseMapper.selectList(new QueryWrapper<TNslpPoint>().eq("del_flag", 0))) {
            if (p.getPointNo() != null) {
                pointMap.put(p.getPointNo(), p);
            }
        }
        for (int i = 0; i < queries.size() && i < verdicts.size(); i++) {
            TNslpPoint point = pointMap.get(queries.get(i).getPointNo());
            if (point != null) {
                applyVerdict(point, verdicts.get(i));
            }
        }
        return verdicts;
    }

    @Override
    public List<TNslpPoint> listPinnedOrphans(Date at) {
        List<TNslpPoint> orphans = new ArrayList<TNslpPoint>();
        if (at == null) {
            return orphans;
        }
        // at 那一刻在位且配置干净的线代号全集；现行清单里没有的挂码即孤儿
        java.util.Set<String> activeCodes = new java.util.HashSet<String>();
        for (TNslpLimitLine line : nslpLimitLineService.listAvailable(at)) {
            if (line.getRuleCode() != null) {
                activeCodes.add(line.getRuleCode());
            }
        }
        List<TNslpPoint> pinned = this.baseMapper.selectList(new QueryWrapper<TNslpPoint>()
                .eq("del_flag", 0).isNotNull("pinned_rule_code"));
        for (TNslpPoint p : pinned) {
            if (p.getPinnedRuleCode() != null && !p.getPinnedRuleCode().trim().isEmpty()
                    && !activeCodes.contains(p.getPinnedRuleCode())) {
                orphans.add(p);
            }
        }
        return orphans;
    }

    /**
     * 归到层就把线代号与起用那一一日钉进行上两格；不归层两格清成空白。
     * 拿邻线数值凑一个像样答复的事，这里一概不认。
     */
    private void applyVerdict(TNslpPoint point, LimitLineVerdict verdict) {
        UpdateWrapper<TNslpPoint> uw = new UpdateWrapper<TNslpPoint>()
                .eq("id", point.getId()).eq("del_flag", 0);
        if (verdict != null && verdict.isLeveled()) {
            uw.set("pinned_rule_code", verdict.getRuleCode())
              .set("pinned_eff_start", verdict.getEffStart());
            point.setPinnedRuleCode(verdict.getRuleCode());
            point.setPinnedEffStart(verdict.getEffStart());
        } else {
            uw.set("pinned_rule_code", null).set("pinned_eff_start", null);
            point.setPinnedRuleCode(null);
            point.setPinnedEffStart(null);
        }
        uw.set("update_time", new Date());
        this.baseMapper.update(null, uw);
    }

    // ==================================================================
    // 建档 + 三态：旧方法本体不动，新能力另写在这一段承接。
    // 五道筛、数量轧法、步子口径都只问 support 处，本类只落库与装配。
    // ==================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpPoint archive(PointArchiveSubmit submit) {
        if (submit == null) {
            throw new IllegalArgumentException("建档报文没递上来，一行建不起来");
        }
        boolean updating = submit.getId() != null;
        TNslpPoint self = null;
        if (updating) {
            self = mustGetPoint(submit.getId());
            // 到了头（压卡）或已叫停，字都动不得；压卡要改只能另起新行
            requireNotFrozen(submit.getId());
        }

        String pointNo = submit.getPointNo() == null ? null : submit.getPointNo().trim();
        String zoneNo = submit.getZoneNo() == null ? null : submit.getZoneNo().trim();

        // 第一道·点位码：查同年度同区册下同码且仍占名额的「另一条」；更新把自身摘出去，
        // 但改了码/年度/区册撞上别的在走行，照样挡。
        if (PointArchiveRules.codeBlank(pointNo)) {
            throw new IllegalArgumentException("点位码这一栏空着：点位码随报送材料进来，系统不另发第二串"
                    + (updating ? "，更新也指不到是哪一行" : "，建不成"));
        }
        String codeErr = PointArchiveRules.screenCode(false,
                hasOtherLiveSameCode(submit.getRegYear(), zoneNo, pointNo,
                        updating ? self.getId() : null));
        if (codeErr != null) {
            throw new IllegalArgumentException(codeErr);
        }

        // 第二道·挂靠：代号找不到、册已撤出，逐样点名；过得再以区册为准拿全称与主键。
        TNslpZoneBook zone = nslpZoneBookService.getByZoneNo(zoneNo);
        String zoneErr = PointArchiveRules.screenZone(zone);
        if (zoneErr != null) {
            throw new IllegalArgumentException(zoneErr);
        }

        // 第三道·数量：核定/达标逐栏点名；过得才把还差几回由唯一轧法重轧。
        List<String> qtyErrs = PointArchiveRules.quantityErrors(submit.getNeedNum(), submit.getGotNum());
        if (!qtyErrs.isEmpty()) {
            throw new IllegalArgumentException(joinMessages(qtyErrs));
        }
        int need = Integer.parseInt(submit.getNeedNum().trim());
        int got = submit.getGotNum() == null || submit.getGotNum().trim().isEmpty()
                ? 0 : Integer.parseInt(submit.getGotNum().trim());
        int lack = PointArchiveRules.lack(need, got);

        // 第四道·同点在走名额（只对建行）：压卡/叫停/删除的不占，旧行空步照样占。
        if (!updating && PointArchiveRules.screenSlot(hasOtherLivePoint(pointNo, null)) != null) {
            throw new IllegalStateException(PointArchiveRules.screenSlot(true));
        }

        Date now = new Date();
        TNslpPoint row;
        if (updating) {
            // 第五道·落笔人（改线）
            String updaterErr = PointArchiveRules.screenUpdater(submit.getOperator());
            if (updaterErr != null) {
                throw new IllegalArgumentException(updaterErr);
            }
            row = self;
            row.setPointNo(pointNo);
            applyZone(row, zone);
            row.setRegYear(submit.getRegYear());
            row.setNeedNum(need);
            row.setGotNum(got);
            row.setLackNum(lack);
            if (submit.getMaintainUnit() != null) {
                row.setMaintainUnit(submit.getMaintainUnit().trim());
            }
            if (submit.getContent() != null) {
                row.setContent(submit.getContent());
            }
            if (submit.getSrcKind() != null) {
                row.setSrcKind(submit.getSrcKind().trim());
            }
            row.setUpdateBy(submit.getOperator().trim());
            row.setUpdateTime(now);
            this.baseMapper.updateById(row);
            return row;
        }

        // 第五道·落笔人（建档）
        String creatorErr = PointArchiveRules.screenCreator(submit.getOperator());
        if (creatorErr != null) {
            throw new IllegalArgumentException(creatorErr);
        }
        row = new TNslpPoint();
        row.setPointNo(pointNo);
        applyZone(row, zone);
        row.setRegYear(submit.getRegYear());
        row.setSrcKind(submit.getSrcKind() == null ? null : submit.getSrcKind().trim());
        row.setMaintainUnit(submit.getMaintainUnit() == null ? null : submit.getMaintainUnit().trim());
        row.setNeedNum(need);
        row.setGotNum(got);
        row.setLackNum(lack);
        row.setContent(submit.getContent());
        row.setStatus(Integer.valueOf(0));
        // 建好的新行起在「待测」；真正算数的是身后那串留痕，flow_stage 只是回放后写回的结果
        row.setFlowStage(Integer.valueOf(PointFlowRules.STEP_PENDING));
        row.setStopFlag(Integer.valueOf(0));
        row.setDelFlag(Integer.valueOf(0));
        row.setCreateBy(submit.getOperator().trim());
        row.setCreateTime(now);
        this.baseMapper.insert(row);
        addStep(row.getId(), PointFlowRules.STEP_PENDING, PointFlowRules.ACT_START,
                "起册：点位" + pointNo + "，区册" + zoneNo, submit.getOperator().trim(), now);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpPoint correctGot(Long id, Integer got, String operator) {
        TNslpPoint row = mustGetPoint(id);
        requireNotFrozen(id);
        if (got == null) {
            throw new IllegalArgumentException("已核验达标的次数这一栏没填，更正落不下来");
        }
        if (got.intValue() < 0) {
            throw new IllegalArgumentException("已核验达标的次数这一栏不能是负数：" + got);
        }
        if (row.getNeedNum() == null) {
            throw new IllegalArgumentException("核定应测次数这一档空着，已达标次数没法对，先补核定");
        }
        if (got.intValue() > row.getNeedNum().intValue()) {
            throw new IllegalArgumentException("已核验达标的次数（" + got + "）反超核定应测次数（"
                    + row.getNeedNum() + "），不让落库");
        }
        String err = PointArchiveRules.screenUpdater(operator);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        // 第二趟重轧：已达标次数被更正，lack 当场重轧盖写上一回的
        row.setGotNum(got);
        row.setLackNum(Integer.valueOf(PointArchiveRules.lack(row.getNeedNum().intValue(), got.intValue())));
        row.setUpdateBy(operator.trim());
        row.setUpdateTime(new Date());
        this.baseMapper.updateById(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpPoint rezone(Long id, String newZoneNo, String operator) {
        TNslpPoint row = mustGetPoint(id);
        requireNotFrozen(id);
        String code = newZoneNo == null ? null : newZoneNo.trim();
        TNslpZoneBook zone = nslpZoneBookService.getByZoneNo(code);
        String zoneErr = PointArchiveRules.screenZone(zone);
        if (zoneErr != null) {
            throw new IllegalArgumentException(zoneErr);
        }
        String err = PointArchiveRules.screenUpdater(operator);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        applyZone(row, zone);
        if (row.getNeedNum() != null && row.getGotNum() != null) {
            // 第三趟重轧：改挂到别的区册，lack 按同一轧法再算一遍写回
            row.setLackNum(Integer.valueOf(
                    PointArchiveRules.lack(row.getNeedNum().intValue(), row.getGotNum().intValue())));
        }
        row.setUpdateBy(operator.trim());
        row.setUpdateTime(new Date());
        this.baseMapper.updateById(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpPoint archiveFromRetest(String pointNo, String zoneNo, Integer regYear,
                                        Integer need, Integer addGot, String operator) {
        // 复测核收带出的建行与建档岗逐条建行共用同一个进门形状、同一套五道筛与数量算法，
        // 界面上不长第二种样子。核定取底册/报文，本次新增达标空着按 0。
        PointArchiveSubmit submit = new PointArchiveSubmit();
        submit.setPointNo(pointNo);
        submit.setZoneNo(zoneNo);
        submit.setRegYear(regYear);
        submit.setNeedNum(need == null ? null : String.valueOf(need.intValue()));
        submit.setGotNum(addGot == null ? "0" : String.valueOf(addGot.intValue()));
        submit.setSrcKind("复测核收带出");
        submit.setOperator(operator);
        return archive(submit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpPoint advanceFlow(Long id, String operator, String note) {
        TNslpPoint row = mustGetPoint(id);
        PointFlowRules.ReplayResult cur = replay(id);
        Integer stage = cur.getStep();
        if (PointFlowRules.frozen(stage, cur.isStopped() ? 1 : 0)) {
            throw new IllegalStateException("这一行已" + (PointFlowRules.isSealed(stage) ? "压卡" : "叫停")
                    + "，步子不再往前：" + id);
        }
        Integer to = PointFlowRules.forwardTarget(stage);
        if (to == null) {
            throw new IllegalStateException("待测→已测只此一下可推；当下在「"
                    + PointFlowRules.stepName(stage) + "」，没有紧邻的下一步可推：" + id);
        }
        String err = PointArchiveRules.screenUpdater(operator);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        Date now = new Date();
        addStep(id, to.intValue(), PointFlowRules.ACT_FORWARD, note, operator.trim(), now);
        row.setFlowStage(to);
        row.setUpdateBy(operator.trim());
        row.setUpdateTime(now);
        this.baseMapper.updateById(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpPoint rollbackFlow(Long id, String operator, String note) {
        TNslpPoint row = mustGetPoint(id);
        PointFlowRules.ReplayResult cur = replay(id);
        Integer stage = cur.getStep();
        if (PointFlowRules.isSealed(stage)) {
            throw new IllegalStateException("这一行已压卡，退不回来，要改只能另起新的一行：" + id);
        }
        if (!PointFlowRules.canRollback(stage, cur.isStopped() ? 1 : 0)) {
            throw new IllegalStateException("只有已测能退回一步到待测；当下在「"
                    + PointFlowRules.stepName(stage) + "」，退不了：" + id);
        }
        String err = PointArchiveRules.screenUpdater(operator);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        Date now = new Date();
        // 先前那一趟的留痕一笔不抹，只在末尾添一笔退回；不与后补的并成一排
        addStep(id, PointFlowRules.STEP_PENDING, PointFlowRules.ACT_BACK, note, operator.trim(), now);
        row.setFlowStage(Integer.valueOf(PointFlowRules.STEP_PENDING));
        row.setUpdateBy(operator.trim());
        row.setUpdateTime(now);
        this.baseMapper.updateById(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpPoint sealFlow(Long id, String operator, String note) {
        TNslpPoint row = mustGetPoint(id);
        PointFlowRules.ReplayResult cur = replay(id);
        Integer stage = cur.getStep();
        if (!PointFlowRules.canSeal(stage, cur.isStopped() ? 1 : 0)) {
            throw new IllegalStateException("压卡只在已测这一步发起；当下在「"
                    + PointFlowRules.stepName(stage) + "」，压不了：" + id);
        }
        String err = PointArchiveRules.screenUpdater(operator);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        Date now = new Date();
        addStep(id, PointFlowRules.STEP_SEALED, PointFlowRules.ACT_SEAL, note, operator.trim(), now);
        row.setFlowStage(Integer.valueOf(PointFlowRules.STEP_SEALED));
        row.setUpdateBy(operator.trim());
        row.setUpdateTime(now);
        this.baseMapper.updateById(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpPoint stopFlow(Long id, String operator, String note) {
        TNslpPoint row = mustGetPoint(id);
        PointFlowRules.ReplayResult cur = replay(id);
        if (PointFlowRules.isSealed(cur.getStep())) {
            throw new IllegalStateException("这一行已压卡到头，不必再叫停：" + id);
        }
        if (cur.isStopped() || (row.getStopFlag() != null && row.getStopFlag().intValue() == 1)) {
            throw new IllegalStateException("这一行已经叫停过：" + id);
        }
        Integer stage = cur.getStep();
        String err = PointArchiveRules.screenUpdater(operator);
        if (err != null) {
            throw new IllegalArgumentException(err);
        }
        Date now = new Date();
        addStep(id, stage == null ? PointFlowRules.STEP_PENDING : stage.intValue(),
                PointFlowRules.ACT_STOP, note, operator.trim(), now);
        row.setStopFlag(Integer.valueOf(1));
        row.setUpdateBy(operator.trim());
        row.setUpdateTime(now);
        this.baseMapper.updateById(row);
        return row;
    }

    @Override
    public Integer currentStage(Long id) {
        if (id == null) {
            return null;
        }
        return replay(id).getStep();
    }

    @Override
    public java.util.Map<Long, Integer> currentStageMap(java.util.Collection<Long> ids) {
        java.util.Map<Long, Integer> out = new java.util.HashMap<Long, Integer>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        // 一次把这批点位的留痕全取回来，内存内逐行回放；名册分档只消费这一处的结果
        List<TNslpPointStep> all = this.nslpPointStepMapper.selectList(new QueryWrapper<TNslpPointStep>()
                .in("point_id", ids)
                .eq("del_flag", 0)
                .orderByAsc("point_id", "step_time", "seq", "id"));
        java.util.Map<Long, List<PointFlowRules.Event>> byPoint =
                new java.util.HashMap<Long, List<PointFlowRules.Event>>();
        for (TNslpPointStep s : all) {
            List<PointFlowRules.Event> bucket = byPoint.get(s.getPointId());
            if (bucket == null) {
                bucket = new java.util.ArrayList<PointFlowRules.Event>();
                byPoint.put(s.getPointId(), bucket);
            }
            bucket.add(new PointFlowRules.Event(s.getAction(),
                    s.getStepNo() == null ? 0 : s.getStepNo().intValue()));
        }
        for (Long id : ids) {
            List<PointFlowRules.Event> events = byPoint.get(id);
            out.put(id, PointFlowRules.replay(events == null
                    ? java.util.Collections.<PointFlowRules.Event>emptyList() : events).getStep());
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 内部：一道认定、一串留痕
    // ------------------------------------------------------------------

    private TNslpPoint mustGetPoint(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("点位主键没给，找不到这一行");
        }
        TNslpPoint p = this.baseMapper.selectById(id);
        if (p == null || (p.getDelFlag() != null && p.getDelFlag().intValue() == 1)) {
            throw new IllegalArgumentException("点位底册里没有这一行：" + id);
        }
        return p;
    }

    /**
     * 到了头（压卡）或已就地叫停，这一行字都动不得：数量更正、改挂、改线一律先过这道。
     * 压卡的要改只能另起新的一行。
     */
    private void requireNotFrozen(Long id) {
        PointFlowRules.ReplayResult cur = replay(id);
        if (PointFlowRules.frozen(cur.getStep(), cur.isStopped() ? 1 : 0)) {
            String what = PointFlowRules.isSealed(cur.getStep())
                    ? "已压卡，添不进、动不得、撤不掉，要改只能另起新的一行"
                    : "已叫停，字动不得";
            throw new IllegalStateException("这一行" + what + "：" + id);
        }
    }

    /** 挂靠三格一起以区册底为准：主键、代号、冗余全称 */
    private void applyZone(TNslpPoint row, TNslpZoneBook zone) {
        row.setZoneId(zone.getId() == null ? null : zone.getId().intValue());
        row.setZoneNo(zone.getZoneNo());
        // 冗余全称对不齐也只纠正、不挡建行（两处各留一份说法算缺陷，留待名册/复核处点出）
        row.setZoneName(PointArchiveRules.alignZoneName(zone, row.getZoneName()));
    }

    /** 另有一条同年度、同区册、同码且仍占名额的行（excludeId 用于把自身摘出去） */
    private boolean hasOtherLiveSameCode(Integer regYear, String zoneNo, String pointNo, Long excludeId) {
        if (PointArchiveRules.codeBlank(pointNo)) {
            return false;
        }
        QueryWrapper<TNslpPoint> w = new QueryWrapper<TNslpPoint>()
                .eq("del_flag", 0)
                .eq("point_no", pointNo);
        if (regYear != null) {
            w.eq("reg_year", regYear);
        } else {
            w.isNull("reg_year");
        }
        if (zoneNo != null) {
            w.eq("zone_no", zoneNo);
        }
        for (TNslpPoint p : this.baseMapper.selectList(w)) {
            if (excludeId != null && p.getId() != null && p.getId().equals(excludeId)) {
                continue;
            }
            if (PointFlowRules.occupiesSlot(replayStage(p.getId()), p.getStopFlag(), p.getDelFlag())) {
                return true;
            }
        }
        return false;
    }

    /** 同一处点位（只认点位码）是否另有一条仍占名额的在走行；压卡/叫停/删除的不占 */
    private boolean hasOtherLivePoint(String pointNo, Long excludeId) {
        if (PointArchiveRules.codeBlank(pointNo)) {
            return false;
        }
        for (TNslpPoint p : this.baseMapper.selectList(new QueryWrapper<TNslpPoint>()
                .eq("del_flag", 0).eq("point_no", pointNo))) {
            if (excludeId != null && p.getId() != null && p.getId().equals(excludeId)) {
                continue;
            }
            if (PointFlowRules.occupiesSlot(replayStage(p.getId()), p.getStopFlag(), p.getDelFlag())) {
                return true;
            }
        }
        return false;
    }

    /** 当下步只由留痕串回放得出（屏上写的、行上缓存的都不作依据）；无留痕旧行回 null */
    private Integer replayStage(Long pointId) {
        return replay(pointId).getStep();
    }

    private PointFlowRules.ReplayResult replay(Long pointId) {
        List<TNslpPointStep> steps = this.nslpPointStepMapper.selectList(new QueryWrapper<TNslpPointStep>()
                .eq("point_id", pointId)
                .eq("del_flag", 0)
                .orderByAsc("step_time", "seq", "id"));
        List<PointFlowRules.Event> events = new java.util.ArrayList<PointFlowRules.Event>();
        for (TNslpPointStep s : steps) {
            events.add(new PointFlowRules.Event(s.getAction(),
                    s.getStepNo() == null ? 0 : s.getStepNo().intValue()));
        }
        return PointFlowRules.replay(events);
    }

    private void addStep(Long pointId, int step, String action, String note, String operator, Date at) {
        TNslpPointStep rec = new TNslpPointStep();
        rec.setPointId(pointId);
        rec.setStepNo(Integer.valueOf(step));
        rec.setAction(action);
        rec.setNote(note);
        rec.setStepTime(at);
        rec.setSeq(Integer.valueOf(nextStepSeq(pointId, at)));
        rec.setDelFlag(Integer.valueOf(0));
        rec.setCreateBy(operator);
        rec.setCreateTime(at);
        this.nslpPointStepMapper.insert(rec);
    }

    private int nextStepSeq(Long pointId, Date at) {
        return this.nslpPointStepMapper.selectCount(new QueryWrapper<TNslpPointStep>()
                .eq("point_id", pointId)
                .eq("step_time", at)
                .eq("del_flag", 0));
    }

    private static String joinMessages(List<String> messages) {
        StringBuilder sb = new StringBuilder();
        for (String m : messages) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(m);
        }
        return sb.toString();
    }
}
