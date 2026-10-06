package com.fc.v2.service.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.fc.v2.service.ITNslpCaseFlowService;
import com.fc.v2.service.support.CaseFlowRules;
import com.fc.v2.service.support.LimitLineRules;

/**
 * 噪声投诉与超标处置办件流转单 Service业务层处理（state-machine 形状：单据流转）。
 *
 * 一处点位一条，从受件起、到复查收尾止，四件事只在本处办——
 * 立（四样输入点名、档由后端数）、挪（紧邻一关、门槛在前、按语一句）、
 * 封（末关两件齐、封口只看不动、一处点位只一条没封住）、捋（留痕按时序、
 * 档数由那一串放出来、受理簿一笔一笔对）。口径全在 {@link CaseFlowRules}，
 * 本类只落库与装配，不另起算法。
 *
 * 去年两处偏差（跟着报文序号多挪两关、一步没挪）的根子：关次不以后端为准。
 * 如今 advance 只认紧邻的下一关，越关那一下不生效，整条仍留在原地。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TNslpCaseFlowServiceImpl implements ITNslpCaseFlowService {

    @javax.annotation.Resource
    private TNslpCaseFlowMapper nslpCaseFlowMapper;

    @javax.annotation.Resource
    private TNslpCaseTrailMapper nslpCaseTrailMapper;

    @javax.annotation.Resource
    private TNslpPointMapper nslpPointMapper;

    @javax.annotation.Resource
    private TNslpZoneBookMapper nslpZoneBookMapper;

    @Override
    public TNslpCaseFlow selectTNslpCaseFlowById(Long id) {
        return this.nslpCaseFlowMapper.selectById(id);
    }

    @Override
    public List<TNslpCaseFlow> selectTNslpCaseFlowList(QueryWrapper<TNslpCaseFlow> queryWrapper) {
        return this.nslpCaseFlowMapper.selectList(queryWrapper);
    }

    // ------------------------------------------------------------------
    // 立
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpCaseFlow intake(CaseIntake intake) {
        if (intake == null) {
            throw new IllegalArgumentException("立件报文没递上来，一条办件立不起来");
        }
        // 四样输入一样一样点名：光甩一句「信息不全」不认
        List<String> missing = CaseFlowRules.intakeMissing(intake.getPointNo(), intake.getZoneNo(),
                intake.getOccNum(), intake.getCheckReading());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(join(missing));
        }
        String pointNo = intake.getPointNo().trim();
        String zoneNo = intake.getZoneNo().trim();

        // 一处点位任何一刻只许一条没封住的件：前一条在办也好、中途歇下也好，
        // 第二条都立不起来。封住的、撤回并走收口的、作废的都不占名额。
        if (countOpenByPoint(pointNo) > 0) {
            throw new IllegalStateException("这一处点位已有一条没封住的件，第二条立不起来：" + pointNo);
        }

        TNslpCaseFlow r = new TNslpCaseFlow();
        r.setBizNo(resolveBizNo(intake.getBizNo(), pointNo));
        r.setPointNo(pointNo);
        r.setZoneNo(zoneNo);
        r.setOccNum(intake.getOccNum());
        // 手机端把字敲进来、把数回显出来；折算前先过限值线那道一位小数逢半进一
        r.setCheckReading(LimitLineRules.alignOne(CaseFlowRules.toReading(intake.getCheckReading())));
        // 走到哪一关一律由后端数出来：报文自带的那一个数只当参考，不写库
        r.setStage(Integer.valueOf(CaseFlowRules.STAGE_RECEIVED));
        r.setStatus(Integer.valueOf(CaseFlowRules.STATUS_ACTIVE));
        r.setNoteMask(Integer.valueOf(0));
        r.setVoidFlag(Integer.valueOf(0));
        r.setDelFlag(Integer.valueOf(0));
        this.nslpCaseFlowMapper.insert(r);

        Date now = new Date();
        addTrail(r.getId(), CaseFlowRules.STAGE_RECEIVED, CaseFlowRules.ACT_RECEIVE,
                "受件立条：点位" + pointNo + "，区册" + zoneNo, now);
        // 受件关头一句按语：有话就落，同关再来第二句盖不上去
        if (notEmpty(intake.getNote())) {
            r.setContent(intake.getNote().trim());
            r.setNoteMask(Integer.valueOf(
                    CaseFlowRules.withNote(0, CaseFlowRules.STAGE_RECEIVED)));
            addTrail(r.getId(), CaseFlowRules.STAGE_RECEIVED, CaseFlowRules.ACT_NOTE,
                    intake.getNote().trim(), now);
        }
        r.setLastAction(CaseFlowRules.ACT_RECEIVE);
        r.setUpdateTime(now);
        this.nslpCaseFlowMapper.updateById(r);
        return r;
    }

    @Override
    public boolean cancelBeforeIntake(String pointNo, String reason) {
        // 撤销只可能出现在一条件正式立起来之前那一小会儿：主档里本就没有行，
        // 不进主档、不占名额。主档里已经有这一点位的件，那不是这条道上的事。
        if (notEmpty(pointNo) && countOpenByPoint(pointNo.trim()) == 0) {
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 挪
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpCaseFlow advance(Long id, String remark) {
        TNslpCaseFlow r = mustGet(id);
        if (CaseFlowRules.isFrozen(r.getStatus(), r.getVoidFlag())) {
            throw new IllegalStateException("这一条已经封住（或已收口/作废），只能看不能动：" + id);
        }
        int from = stageOf(r);
        if (from >= CaseFlowRules.STAGE_REVIEW_CLOSE) {
            throw new IllegalStateException("已经站在复查收尾末关，封关请走封关那一步：" + id);
        }
        // 一次只许跨紧邻的下一关：越中间关直接送再下一关，不生效，整条留在原地。
        // 本方法只推一档，正是紧邻那一下；门槛摆在各关前头。
        boolean pointOn = from != CaseFlowRules.STAGE_RECEIVED || pointStillOnRegister(r.getPointNo());
        String guard = CaseFlowRules.guardForward(from, pointOn, r.getOrderAccept());
        if (guard != null) {
            throw new IllegalStateException(guard + "（件次" + safeBiz(r) + "）");
        }
        int to = from + 1;
        Date now = new Date();
        r.setStage(Integer.valueOf(to));
        r.setStatus(Integer.valueOf(CaseFlowRules.STATUS_ACTIVE));
        r.setContent("");
        r.setLastAction(CaseFlowRules.ACT_FORWARD);
        r.setUpdateTime(now);
        this.nslpCaseFlowMapper.updateById(r);
        // 改档这一步与留痕那一步同成一个事务：挪前的话随这笔压到身后
        addTrail(r.getId(), to, CaseFlowRules.ACT_FORWARD, remark, now);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpCaseFlow rollback(Long id, String remark) {
        TNslpCaseFlow r = mustGet(id);
        if (CaseFlowRules.isFrozen(r.getStatus(), r.getVoidFlag())) {
            throw new IllegalStateException("这一条已经封住（或已收口/作废），只能看不能动：" + id);
        }
        int from = stageOf(r);
        // 已经站在第一关的，没什么可再收
        if (!CaseFlowRules.canRollback(from)) {
            throw new IllegalStateException("已经站在受件头一关，没什么可再收：" + id);
        }
        int to = from - 1;
        Date now = new Date();
        // 被收回这一关先前留下的那句按语，照原先后留在追溯记录末尾（不抹），
        // 只把本关那一位腾掉：重新挪到这一关时从头累计，新的头一句才落得进。
        r.setStage(Integer.valueOf(to));
        r.setContent("");
        r.setNoteMask(Integer.valueOf(
                CaseFlowRules.withoutNote(maskOf(r), from)));
        r.setLastAction(CaseFlowRules.ACT_BACK);
        r.setUpdateTime(now);
        this.nslpCaseFlowMapper.updateById(r);
        addTrail(r.getId(), to, CaseFlowRules.ACT_BACK, remark, now);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean note(Long id, String note) {
        TNslpCaseFlow r = mustGet(id);
        if (CaseFlowRules.isFrozen(r.getStatus(), r.getVoidFlag())) {
            // 封住之后添按语一概不接
            throw new IllegalStateException("这一条已经封住，按语一句也接不进去：" + id);
        }
        if (!notEmpty(note)) {
            throw new IllegalArgumentException("按语空着，没东西可落");
        }
        int stage = stageOf(r);
        if (CaseFlowRules.hasNote(maskOf(r), stage)) {
            // 同一关第二次塞话：本行留头一句，第二句既盖不上去、也不另存一份副本
            return false;
        }
        Date now = new Date();
        String text = note.trim();
        r.setContent(text);
        r.setNoteMask(Integer.valueOf(CaseFlowRules.withNote(maskOf(r), stage)));
        r.setLastAction(CaseFlowRules.ACT_NOTE);
        r.setUpdateTime(now);
        this.nslpCaseFlowMapper.updateById(r);
        addTrail(r.getId(), stage, CaseFlowRules.ACT_NOTE, text, now);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recordOrderAccept(Long id, String acceptText) {
        TNslpCaseFlow r = mustGet(id);
        if (CaseFlowRules.isFrozen(r.getStatus(), r.getVoidFlag())) {
            throw new IllegalStateException("这一条已经封住，只能看不能动：" + id);
        }
        if (stageOf(r) != CaseFlowRules.STAGE_ORDER) {
            throw new IllegalStateException("责令关的核收只在责令关落，当前停在"
                    + CaseFlowRules.stageName(r.getStage()) + "：" + id);
        }
        Date now = new Date();
        r.setOrderAccept(notEmpty(acceptText) ? acceptText.trim() : CaseFlowRules.ORDER_ACCEPT_OK);
        r.setUpdateTime(now);
        return this.nslpCaseFlowMapper.updateById(r) > 0;
    }

    // ------------------------------------------------------------------
    // 封
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpCaseFlow seal(CaseSeal seal) {
        if (seal == null || seal.getId() == null) {
            throw new IllegalArgumentException("封关报文没指到件，封不上");
        }
        TNslpCaseFlow r = mustGet(seal.getId());
        if (CaseFlowRules.isFrozen(r.getStatus(), r.getVoidFlag())) {
            throw new IllegalStateException("这一条已经封住，只能看不能动：" + seal.getId());
        }
        if (stageOf(r) != CaseFlowRules.STAGE_REVIEW_CLOSE) {
            throw new IllegalStateException("封关只在复查收尾末关，当前停在"
                    + CaseFlowRules.stageName(r.getStage()) + "，封不上：" + seal.getId());
        }
        // 两样齐了才算封上；缺哪一样、两笔记成同一个，都一句一句点名
        List<String> missing = CaseFlowRules.sealMissing(
                seal.getWitnessA(), seal.getWitnessB(), seal.getProofNo());
        if (!missing.isEmpty()) {
            throw new IllegalStateException(join(missing) + "（件次" + safeBiz(r) + "）");
        }
        Date now = new Date();
        r.setWitnessA(seal.getWitnessA().trim());
        r.setWitnessB(seal.getWitnessB().trim());
        r.setProofNo(seal.getProofNo().trim());
        r.setStatus(Integer.valueOf(CaseFlowRules.STATUS_TERMINAL));
        r.setCloseKind(CaseFlowRules.CLOSE_KIND_SEALED);
        r.setLastAction(CaseFlowRules.ACT_SEAL);
        r.setUpdateTime(now);
        this.nslpCaseFlowMapper.updateById(r);
        addTrail(r.getId(), CaseFlowRules.STAGE_REVIEW_CLOSE, CaseFlowRules.ACT_SEAL,
                "复查收尾封住：复测凭证" + seal.getProofNo().trim(), now);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpCaseFlow withdraw(Long id, String reason) {
        // 递件人在受件之后自己把反映撤了回去：视同收口，不必再往前挪
        return closeBy(id, CaseFlowRules.CLOSE_KIND_WITHDRAW, CaseFlowRules.ACT_WITHDRAW,
                "反映人撤回去", reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int mergeZone(String zoneNo, String reason) {
        if (!notEmpty(zoneNo)) {
            throw new IllegalArgumentException("区册代号没填，并走收口落不到册上");
        }
        String zone = zoneNo.trim();
        TNslpZoneBook book = this.nslpZoneBookMapper.selectOne(new QueryWrapper<TNslpZoneBook>()
                .eq("zone_no", zone)
                .eq("del_flag", 0)
                .last("limit 1"));
        if (book == null) {
            throw new IllegalArgumentException("区册底里查无此册：" + zone);
        }
        // 「从区册底里并走」这件事以区册底册记成已撤出（情形 1）为准；
        // 还在册的册没并走过，本接口不替它改册面，也不收口。
        if (book.getStatus() == null || book.getStatus().intValue() != 1) {
            throw new IllegalStateException("区册" + zone + "在区册底里还在册，没并走，名下的件不收口");
        }
        // 这一处所属的区册并走：该册名下在办的件（不管停在哪一关）一并视同收口
        List<TNslpCaseFlow> open = this.nslpCaseFlowMapper.selectList(new QueryWrapper<TNslpCaseFlow>()
                .eq("zone_no", zone)
                .eq("status", CaseFlowRules.STATUS_ACTIVE)
                .eq("del_flag", 0));
        int n = 0;
        for (TNslpCaseFlow r : open) {
            closeRecord(r, CaseFlowRules.CLOSE_KIND_MERGED, CaseFlowRules.ACT_MERGE,
                    "区册" + zone + "从区册底里并走", reason, new Date());
            n++;
        }
        return n;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean voidCase(Long id, String reason) {
        TNslpCaseFlow r = mustGet(id);
        // 作废从最后一关发起：不在复查收尾关的（在办的、歇在中途的）作不了废
        if (stageOf(r) != CaseFlowRules.STAGE_REVIEW_CLOSE) {
            throw new IllegalStateException("作废只从复查收尾末关发起，当前停在"
                    + CaseFlowRules.stageName(r.getStage()) + "，作不了废：" + id);
        }
        if (r.getVoidFlag() != null && r.getVoidFlag().intValue() == 1) {
            return false;
        }
        Date now = new Date();
        r.setVoidFlag(Integer.valueOf(1));
        r.setLastAction(CaseFlowRules.ACT_VOID);
        r.setUpdateTime(now);
        this.nslpCaseFlowMapper.updateById(r);
        addTrail(r.getId(), stageOf(r), CaseFlowRules.ACT_VOID, reason, now);
        return true;
    }

    // ------------------------------------------------------------------
    // 捋
    // ------------------------------------------------------------------

    @Override
    public CaseTrace trace(Long id) {
        TNslpCaseFlow r = mustGet(id);
        return CaseTrace.of(r.getId(), r.getBizNo(), listTrails(id));
    }

    @Override
    public CaseLedger ledger(Long id, Date day) {
        TNslpCaseFlow r = mustGet(id);
        return CaseLedger.of(r.getId(), day, listTrails(id));
    }

    @Override
    public int countOpen() {
        // 页面上头那个数只认这一处一次查出来的结果；前端缓存里的、另算的都不算。
        // 作废的件虽还露在名册上，但已经不是「没封住」的件，不进这个数。
        Integer n = this.nslpCaseFlowMapper.selectCount(new QueryWrapper<TNslpCaseFlow>()
                .eq("status", CaseFlowRules.STATUS_ACTIVE)
                .eq("void_flag", 0)
                .eq("del_flag", 0));
        return n == null ? 0 : n.intValue();
    }

    // ------------------------------------------------------------------
    // 内部：一处认定、一面留痕
    // ------------------------------------------------------------------

    private TNslpCaseFlow closeBy(Long id, int kind, String action, String what, String reason) {
        TNslpCaseFlow r = mustGet(id);
        if (CaseFlowRules.isFrozen(r.getStatus(), r.getVoidFlag())) {
            throw new IllegalStateException("这一条已经封住（或已收口/作废），只能看不能动：" + id);
        }
        closeRecord(r, kind, action, what, reason, new Date());
        return r;
    }

    private void closeRecord(TNslpCaseFlow r, int kind, String action, String what,
                             String reason, Date now) {
        r.setStatus(Integer.valueOf(CaseFlowRules.STATUS_TERMINAL));
        r.setCloseKind(Integer.valueOf(kind));
        r.setLastAction(action);
        r.setUpdateTime(now);
        this.nslpCaseFlowMapper.updateById(r);
        String note = what + (notEmpty(reason) ? "：" + reason.trim() : "");
        addTrail(r.getId(), stageOf(r), action, note, now);
    }

    private TNslpCaseFlow mustGet(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("件主键没给，找不到这一条");
        }
        TNslpCaseFlow r = this.nslpCaseFlowMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag().intValue() == 1)) {
            throw new IllegalArgumentException("主档里没有这一条办件：" + id);
        }
        return r;
    }

    private int stageOf(TNslpCaseFlow r) {
        return r.getStage() == null ? CaseFlowRules.STAGE_RECEIVED : r.getStage().intValue();
    }

    private int maskOf(TNslpCaseFlow r) {
        return r.getNoteMask() == null ? 0 : r.getNoteMask().intValue();
    }

    private String safeBiz(TNslpCaseFlow r) {
        return r.getBizNo() == null ? String.valueOf(r.getId()) : r.getBizNo();
    }

    private int countOpenByPoint(String pointNo) {
        // 作废的、封住的、撤回并走收口的都不占「没封住」名额
        Integer n = this.nslpCaseFlowMapper.selectCount(new QueryWrapper<TNslpCaseFlow>()
                .eq("point_no", pointNo)
                .eq("status", CaseFlowRules.STATUS_ACTIVE)
                .eq("void_flag", 0)
                .eq("del_flag", 0));
        return n == null ? 0 : n.intValue();
    }

    /** 进核查门槛：这一处点位在底册上还挂着名没有（撤了名的进不去） */
    private boolean pointStillOnRegister(String pointNo) {
        if (!notEmpty(pointNo)) {
            return false;
        }
        List<TNslpPoint> points = this.nslpPointMapper.selectList(new QueryWrapper<TNslpPoint>()
                .eq("point_no", pointNo.trim())
                .eq("del_flag", 0));
        for (TNslpPoint p : points) {
            if (CaseFlowRules.pointOnRegister(p)) {
                return true;
            }
        }
        return false;
    }

    private List<TNslpCaseTrail> listTrails(Long caseId) {
        // 按时序压在身后；同一刻两笔各记各的先后，先 trail_time 再 seq，谁也不并、谁也不吞
        return this.nslpCaseTrailMapper.selectList(new QueryWrapper<TNslpCaseTrail>()
                .eq("case_id", caseId)
                .eq("del_flag", 0)
                .orderByAsc("trail_time", "seq", "id"));
    }

    private int addTrail(Long caseId, int stage, String action, String note, Date at) {
        TNslpCaseTrail t = new TNslpCaseTrail();
        t.setCaseId(caseId);
        t.setStage(Integer.valueOf(stage));
        t.setAction(action);
        t.setNote(note);
        t.setTrailTime(at);
        // 同一刻两条通道各送一笔：seq 由本件留痕里同刻已有笔数断先后，两笔各记各的
        t.setSeq(Integer.valueOf(nextSeq(caseId, at)));
        t.setDelFlag(Integer.valueOf(0));
        return this.nslpCaseTrailMapper.insert(t);
    }

    private int nextSeq(Long caseId, Date at) {
        List<TNslpCaseTrail> same = this.nslpCaseTrailMapper.selectList(
                new QueryWrapper<TNslpCaseTrail>()
                        .eq("case_id", caseId)
                        .eq("trail_time", at)
                        .eq("del_flag", 0));
        return same.size();
    }

    private String resolveBizNo(String raw, String pointNo) {
        if (notEmpty(raw)) {
            return raw.trim();
        }
        // 件次没填：后端按点位码配一个（点位码＋日序），同一日同点第二次立件已被名额挡住
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        return "AJ" + pointNo + String.format("%04d%02d%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static String join(List<String> messages) {
        // 保持逐条点名：缺哪一样点哪一样的名，用「；」连成一句抛到值守岗眼前
        StringBuilder sb = new StringBuilder();
        for (String m : messages) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(m);
        }
        return sb.toString();
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
