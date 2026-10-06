package com.fc.v2.service.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.fc.v2.service.ITNslpPermitBillService;
import com.fc.v2.service.support.PermitBillRules;

/**
 * 夜间连续施工作业许可签批单 Service业务层处理（approval-chain 形状：三关三种数法）。
 *
 * 单只从点位底册带出来；立、落笔、压回、捋四件事只在本处办。三关各照什么算法算齐、
 * 下一段何时开放、压回抹去哪一段本轮的笔、一方落「不可」后整张还动不动得——答案
 * 只认 {@link PermitBillRules} 这一处，本类只落库与装配，不另起算法。
 *
 * 段次、轮次、已到几人一概由服务端从逐笔留痕当场回放点出来：报文写了第几关
 * 落库未必采信，行面 node_no/sign_count 只是回放后写回的回显，任何角色都没有
 * 改它的位置。一次落笔只有 sign 这一个入口，另起单、改旧笔、追补签署三类旁路
 * 一张都不开。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TNslpPermitBillServiceImpl implements ITNslpPermitBillService {

    @Resource
    private TNslpPermitBillMapper nslpPermitBillMapper;

    @Resource
    private TNslpPermitSignMapper nslpPermitSignMapper;

    @Resource
    private TNslpPointMapper nslpPointMapper;

    @Resource
    private TNslpPointChangeMapper nslpPointChangeMapper;

    @Override
    public TNslpPermitBill selectTNslpPermitBillById(Long id) {
        return this.nslpPermitBillMapper.selectById(id);
    }

    @Override
    public List<TNslpPermitBill> selectList(QueryWrapper<TNslpPermitBill> queryWrapper) {
        return this.nslpPermitBillMapper.selectList(queryWrapper);
    }

    // ------------------------------------------------------------------
    // 立：只许从点位底册的记录里带出来，一处点位在签的只许一张
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpPermitBill open(PermitOpenSubmit submit) {
        if (submit == null || submit.getPointId() == null) {
            throw new IllegalArgumentException("立单报文没点到点位底册行，单立不起来");
        }
        TNslpPoint point = this.nslpPointMapper.selectOne(new QueryWrapper<TNslpPoint>()
                .eq("id", submit.getPointId())
                .eq("del_flag", 0)
                .last("for update"));
        if (point == null) {
            throw new IllegalArgumentException("点位底册里没有这一行，单无从带出：" + submit.getPointId());
        }
        // 一处点位、一次夜间连续施工只摊上一张：前一张在签（含压回退关后仍在签的），
        // 第二张立不起来。点齐封住的、止住的不占名额。
        Integer openN = this.nslpPermitBillMapper.selectCount(new QueryWrapper<TNslpPermitBill>()
                .eq("point_no", point.getPointNo())
                .eq("status", PermitBillRules.STATUS_RUNNING)
                .eq("del_flag", 0));
        if (openN != null && openN.intValue() > 0) {
            throw new IllegalStateException("这一处点位已有一张在签的单，第二张立不起来：" + point.getPointNo());
        }

        Date now = new Date();
        TNslpPermitBill r = new TNslpPermitBill();
        r.setBillNo(resolveBillNo(submit.getBillNo(), point.getPointNo()));
        r.setPointId(point.getId());
        r.setPointNo(point.getPointNo());
        r.setZoneNo(point.getZoneNo());
        // 关次与算法只由口径处点：起在收件席，一名即过，应签 1；这些只是回放后的回显
        r.setNodeNo(Integer.valueOf(PermitBillRules.NODE_INTAKE));
        r.setSignMode(Integer.valueOf(PermitBillRules.signMode(PermitBillRules.NODE_INTAKE)));
        r.setNeedCount(Integer.valueOf(PermitBillRules.needCount(PermitBillRules.NODE_INTAKE)));
        r.setSignCount(Integer.valueOf(0));
        r.setCurRound(Integer.valueOf(1));
        r.setStatus(Integer.valueOf(PermitBillRules.STATUS_RUNNING));
        r.setDelFlag(Integer.valueOf(0));
        String operator = notEmpty(submit.getOperator()) ? submit.getOperator().trim() : null;
        if (operator != null) {
            r.setCreateBy(operator);
        }
        r.setCreateTime(now);
        this.nslpPermitBillMapper.insert(r);
        // 立单也压一笔留痕：档由这一串放出来，先有立单、后有落笔
        addSign(r.getId(), PermitBillRules.ACT_OPEN, PermitBillRules.NODE_INTAKE, 1,
                operator, null, true, now);
        return r;
    }

    // ------------------------------------------------------------------
    // 落笔：唯一入口。关次、轮次、已到几人全由服务端从留痕点出来
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean sign(PermitSignSubmit submit) {
        if (submit == null || submit.getId() == null) {
            throw new IllegalArgumentException("落笔报文没指到单，笔落不下来");
        }
        TNslpPermitBill bill = mustGet(submit.getId());
        if (!notEmpty(submit.getSignBy())) {
            throw new IllegalArgumentException("落笔人空着，这笔记不到谁名下");
        }
        if (!notEmpty(submit.getOpinion())) {
            throw new IllegalArgumentException("落笔原话空着，没东西可落");
        }
        String signBy = submit.getSignBy().trim();
        String opinion = submit.getOpinion().trim();

        // 同刻两人各按各的提交：先把主单行锁住，第二笔等头一笔提交后再回放同一串，
        // 两笔各记各的、不许并成一笔，谁是头一句也按各自落单时刻分得清。
        bill = lockGet(submit.getId());
        // 同一次计算：先回放整串，当下关、情形、各关本轮笔数、头一句全由这一遍给出
        List<TNslpPermitSign> signs = listSigns(bill.getId());
        PermitBillRules.ReplayResult replay = replay(signs);
        if (PermitBillRules.isFrozen(replay.getStatus())) {
            // 点齐封住也好、一句「不可」止住也好，此后任何一段再落的笔都不采信
            throw new IllegalStateException("这一张单已"
                    + (replay.getStatus() == PermitBillRules.STATUS_SEALED ? "点齐封住" : "止住")
                    + "，再递进来的笔一律不接：" + bill.getId());
        }
        int node = replay.getNode() == null ? PermitBillRules.NODE_INTAKE : replay.getNode().intValue();
        // 报文写了第几关，落库未必采信：与服务端点出来的关对不上，这笔（越关/跳关）丢掉
        if (submit.getReportNode() != null && submit.getReportNode().intValue() != node) {
            return false;
        }
        int round = replay.getCurrentRound(node);
        int liveCount = replay.getLiveCount(node);
        Boolean firstAgree = firstOpinion(signs, node, round);
        boolean sameApprover = alreadySigned(signs, node, round, signBy);
        boolean incomingAgree = !PermitBillRules.isVetoOpinion(opinion);

        PermitBillRules.Decision decision = PermitBillRules.decide(
                node, liveCount, firstAgree, sameApprover, incomingAgree);
        if (decision == PermitBillRules.Decision.REJECT_DUP
                || decision == PermitBillRules.Decision.REJECT_FULL) {
            // 同一人同关同轮第二句：段上保留头一次那一句，不覆盖、不另存、不累笔
            return false;
        }

        Date now = new Date();
        addSign(bill.getId(), PermitBillRules.ACT_SIGN, node, round,
                signBy, opinion, incomingAgree, now);

        if (decision == PermitBillRules.Decision.ACCEPT_STOP) {
            // 一句作数的「不可」：整张单当即止住，留一笔止住的痕
            addSign(bill.getId(), PermitBillRules.ACT_STOP, node, round, signBy,
                    "「不可」止住", true, now);
        } else if (decision == PermitBillRules.Decision.ACCEPT_COMPLETE) {
            if (node == PermitBillRules.NODE_CITY) {
                // 走满三关、存备席两条一并认可：整张封住
                addSign(bill.getId(), PermitBillRules.ACT_SEAL, node, round, signBy,
                        "三关点齐封住", true, now);
            } else {
                // 上一关记为齐才开放紧邻的下一关；推进也压一笔，目标关新一轮从头累计
                int to = node + 1;
                int toRound = replay.getRounds()[to] + 1;
                addSign(bill.getId(), PermitBillRules.ACT_ADVANCE, to, toRound, signBy,
                        PermitBillRules.nodeName(to) + "开放", true, now);
            }
        }
        // ACCEPT_WAIT / ACCEPT_WAIT_SPLIT：整单留在本关等，不添任何动作笔

        // 行面只回放后写回：落完痕重新放同一条串，主表各数与页面回显出自这一遍
        return writeBack(bill, listSigns(bill.getId()), signBy, now);
    }

    // ------------------------------------------------------------------
    // 压回：只退一关，抹去的也只是离开那一关本轮的笔
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TNslpPermitBill rollback(Long id, String operator, String note) {
        TNslpPermitBill bill = lockGet(id);
        List<TNslpPermitSign> signs = listSigns(bill.getId());
        PermitBillRules.ReplayResult replay = replay(signs);
        int node = replay.getNode() == null ? PermitBillRules.NODE_INTAKE : replay.getNode().intValue();
        // 退到哪一关由口径处定死：只退紧邻的上一关；冻结的、头一关的都收不回去
        if (!PermitBillRules.canRollback(replay.getStatus(), node)) {
            throw new IllegalStateException(
                    PermitBillRules.isFrozen(replay.getStatus())
                            ? "这一张单已封住/止住，压不回去：" + id
                            : "已经站在收件席头一关，没什么可再收：" + id);
        }
        int fromRound = replay.getCurrentRound(node);
        int to = node - 1;
        int toRound = replay.getRounds()[to] + 1;
        Date now = new Date();

        // 抹的只是离开这一关本轮落下的落笔（动作行、上一轮旧笔、更早各关的笔一笔不动）：
        // 行不删，只把本轮每一笔置 void_flag，本轮与上轮原话分两处，日后一句一句都查得着
        List<TNslpPermitSign> live = this.nslpPermitSignMapper.selectList(
                new QueryWrapper<TNslpPermitSign>()
                        .eq("bill_id", bill.getId())
                        .eq("action", PermitBillRules.ACT_SIGN)
                        .eq("node_no", node)
                        .eq("round_no", fromRound)
                        .eq("void_flag", 0)
                        .eq("del_flag", 0));
        for (TNslpPermitSign s : live) {
            s.setVoidFlag(Integer.valueOf(1));
            this.nslpPermitSignMapper.updateById(s);
        }
        String by = notEmpty(operator) ? operator.trim() : null;
        addSign(bill.getId(), PermitBillRules.ACT_ROLLBACK, to, toRound, by,
                notEmpty(note) ? note.trim() : "压回" + PermitBillRules.nodeName(node), true, now);

        writeBack(bill, listSigns(bill.getId()), by, now);
        return this.nslpPermitBillMapper.selectById(bill.getId());
    }

    // ------------------------------------------------------------------
    // 捋：追溯、受理簿对账、在签张数
    // ------------------------------------------------------------------

    @Override
    public PermitTrace trace(Long id) {
        TNslpPermitBill bill = mustGet(id);
        return PermitTrace.of(bill.getId(), bill.getBillNo(), bill.getPointNo(), listSigns(id));
    }

    @Override
    public PermitLedger ledger(Long id, Date day) {
        TNslpPermitBill bill = mustGet(id);
        return PermitLedger.of(bill.getId(), day, listSigns(id));
    }

    @Override
    public int countOpen() {
        // 页面上方那个数只认这一处一次查出来的结果：随立单加一、随点齐/止住减一，
        // 与逐段累算同源（status 只由落笔后的同一遍回放写回）。
        Integer n = this.nslpPermitBillMapper.selectCount(new QueryWrapper<TNslpPermitBill>()
                .eq("status", PermitBillRules.STATUS_RUNNING)
                .eq("del_flag", 0));
        return n == null ? 0 : n.intValue();
    }

    // ------------------------------------------------------------------
    // 内部：一处认定、一串留痕、一回写回
    // ------------------------------------------------------------------

    private TNslpPermitBill mustGet(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("单主键没给，找不到这一张");
        }
        TNslpPermitBill r = this.nslpPermitBillMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag().intValue() == 1)) {
            throw new IllegalArgumentException("主档里没有这一张签批单：" + id);
        }
        return r;
    }

    /**
     * 落账前把主单行锁住：同一张单同刻并发落笔／压回时排队进，后者拿到锁后回放的
     * 是前者已提交的留痕串——两笔仍各记各的落单时刻，但计数与头一句不会错并成一笔。
     */
    private TNslpPermitBill lockGet(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("单主键没给，找不到这一张");
        }
        TNslpPermitBill r = this.nslpPermitBillMapper.selectOne(new QueryWrapper<TNslpPermitBill>()
                .eq("id", id)
                .last("for update"));
        if (r == null || (r.getDelFlag() != null && r.getDelFlag().intValue() == 1)) {
            throw new IllegalArgumentException("主档里没有这一张签批单：" + id);
        }
        return r;
    }

    private List<TNslpPermitSign> listSigns(Long billId) {
        // 按时序压在身后；同刻两笔各记各的先后，先 sign_time 再 seq 再 id，谁也不并
        return this.nslpPermitSignMapper.selectList(new QueryWrapper<TNslpPermitSign>()
                .eq("bill_id", billId)
                .eq("del_flag", 0)
                .orderByAsc("sign_time", "seq", "id"));
    }

    private PermitBillRules.ReplayResult replay(List<TNslpPermitSign> signs) {
        java.util.List<PermitBillRules.Event> events =
                new java.util.ArrayList<PermitBillRules.Event>();
        for (TNslpPermitSign s : signs) {
            events.add(new PermitBillRules.Event(s.getAction(), s.getNodeNo(),
                    s.getRoundNo() == null ? 0 : s.getRoundNo().intValue(),
                    s.getVoidFlag() != null && s.getVoidFlag().intValue() == 1));
        }
        return PermitBillRules.replay(events);
    }

    /** 本关本轮头一句的说法（先落为准那关用）；一笔还没有回 null */
    private Boolean firstOpinion(List<TNslpPermitSign> signs, int node, int round) {
        for (TNslpPermitSign s : signs) {
            if (isLiveSign(s, node, round)) {
                return Boolean.valueOf(s.getAgreeFlag() != null && s.getAgreeFlag().intValue() == 1);
            }
        }
        return null;
    }

    /** 来人本关本轮是不是已经落过一笔 */
    private boolean alreadySigned(List<TNslpPermitSign> signs, int node, int round, String signBy) {
        for (TNslpPermitSign s : signs) {
            if (isLiveSign(s, node, round) && signBy.equals(s.getSignBy())) {
                return true;
            }
        }
        return false;
    }

    private boolean isLiveSign(TNslpPermitSign s, int node, int round) {
        return PermitBillRules.ACT_SIGN.equals(s.getAction())
                && s.getNodeNo() != null && s.getNodeNo().intValue() == node
                && s.getRoundNo() != null && s.getRoundNo().intValue() == round
                && (s.getVoidFlag() == null || s.getVoidFlag().intValue() == 0);
    }

    /**
     * 同一回处理：主档行面各格按同一次回放写回；回放说点齐了，册面那一行同一刻改口，
     * 变更履历同事务追加——两处出自同一次计算。改口走 0→1 条件更新，落两回只成一回，
     * 不重复追加履历。
     */
    private boolean writeBack(TNslpPermitBill bill, List<TNslpPermitSign> signs,
                              String operator, Date now) {
        PermitBillRules.ReplayResult replay = replay(signs);
        int node = replay.getNode() == null ? PermitBillRules.NODE_INTAKE : replay.getNode().intValue();
        bill.setNodeNo(Integer.valueOf(node));
        bill.setSignMode(Integer.valueOf(PermitBillRules.signMode(node)));
        bill.setNeedCount(Integer.valueOf(PermitBillRules.needCount(node)));
        bill.setSignCount(Integer.valueOf(replay.getLiveCount(node)));
        bill.setCurRound(Integer.valueOf(replay.getCurrentRound(node)));
        bill.setStatus(Integer.valueOf(replay.getStatus()));
        bill.setUpdateBy(operator);
        bill.setUpdateTime(now);
        int updated = this.nslpPermitBillMapper.updateById(bill);

        if (replay.getStatus() == PermitBillRules.STATUS_SEALED && bill.getPointId() != null) {
            grantPointPermit(bill, operator, now);
        }
        return updated > 0;
    }

    /**
     * 册面改口＋履历追加同事务。点齐落笔进本方法前主单行已加锁，同一单落两回串行
     * 执行：头一回把档改到「已准许」，后一回进来见到已是新档便不再改口、不重复追加
     * 履历——主档与履历始终一一对应，对得住时指名报得出本单。
     */
    private void grantPointPermit(TNslpPermitBill bill, String operator, Date now) {
        TNslpPoint point = this.nslpPointMapper.selectById(bill.getPointId());
        if (point == null) {
            throw new IllegalStateException("点齐了却找不到所对点位行，册面改不了口：" + bill.getPointId());
        }
        int from = point.getPermitState() == null
                ? PermitBillRules.POINT_PERMIT_IDLE : point.getPermitState().intValue();
        if (from == PermitBillRules.POINT_PERMIT_GRANTED) {
            // 已经准许过：主档不再改口，也不重复追加履历，落两回只成一回
            return;
        }
        point.setPermitState(Integer.valueOf(PermitBillRules.POINT_PERMIT_GRANTED));
        point.setUpdateBy(operator);
        point.setUpdateTime(now);
        this.nslpPointMapper.updateById(point);

        TNslpPointChange change = new TNslpPointChange();
        change.setPointId(point.getId());
        change.setPointNo(point.getPointNo());
        change.setChangeField("permit_state");
        change.setFromState(Integer.valueOf(from));
        change.setToState(Integer.valueOf(PermitBillRules.POINT_PERMIT_GRANTED));
        change.setBillId(bill.getId());
        change.setBillNo(bill.getBillNo());
        change.setChangeBy(operator);
        change.setChangeTime(now);
        change.setDelFlag(Integer.valueOf(0));
        change.setCreateBy(operator);
        change.setCreateTime(now);
        this.nslpPointChangeMapper.insert(change);
    }

    private int addSign(Long billId, String action, int node, int round, String signBy,
                        String opinion, boolean agree, Date at) {
        TNslpPermitSign s = new TNslpPermitSign();
        s.setBillId(billId);
        s.setAction(action);
        s.setNodeNo(Integer.valueOf(node));
        s.setRoundNo(Integer.valueOf(round));
        s.setSignBy(signBy);
        s.setOpinion(opinion);
        s.setAgreeFlag(Integer.valueOf(agree ? 1 : 0));
        s.setVoidFlag(Integer.valueOf(0));
        s.setSignTime(at);
        // 同一刻两条通道各送一笔：seq 由本单同刻已有笔数断先后，两笔各记各的
        s.setSeq(Integer.valueOf(nextSeq(billId, at)));
        s.setDelFlag(Integer.valueOf(0));
        s.setCreateBy(signBy);
        s.setCreateTime(at);
        return this.nslpPermitSignMapper.insert(s);
    }

    private int nextSeq(Long billId, Date at) {
        return this.nslpPermitSignMapper.selectCount(new QueryWrapper<TNslpPermitSign>()
                .eq("bill_id", billId)
                .eq("sign_time", at)
                .eq("del_flag", 0));
    }

    private String resolveBillNo(String raw, String pointNo) {
        if (notEmpty(raw)) {
            return raw.trim();
        }
        // 单号没填：后端按点位码配一个（点位码＋年月日）；同点位第二张在签已被名额挡住
        Calendar c = Calendar.getInstance();
        c.setTime(new Date());
        return "YJ" + pointNo + String.format("%04d%02d%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
