package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TNslpTaskItemMapper;
import com.fc.v2.model.auto.TNslpTaskItem;
import com.fc.v2.model.custom.TaskDispatchResult;
import com.fc.v2.service.ITNslpTaskItemDispatchService;
import com.fc.v2.service.support.TaskDispatchRules;

/**
 * 复测与巡查安排单夜间走表 Service业务层处理（dispatch-job 形状：夜里走一趟）。
 *
 * 复测通知与现场巡查同在 t_nslp_task_item 一张单上，一行一事，不另开第二张。
 * 哪些行算届期、那一截开不开、定额装满以哪一头作准、记号落成什么样、趟末三个数
 * 怎么放出答话——一概只问 {@link TaskDispatchRules} 这一处，本类只落库与装配，
 * 不另起算法；早先进库的老条目与今年新建立的条目走同一条路径、同一套挑法。
 *
 * 本类不挂事务：一行的两处记号（情形改已发出＋记要补上发出那一刻与发给谁）由
 * 同一句 UPDATE 落下；一行站不住只标本行，其余各行照常办完，不许把整趟中断，
 * 也不许因为一处报错就把已经发出的行翻回原样。每句更新都带「仍搁着」的条件，
 * 同一趟并着跑两回，同一行只发得成一回（幂等）。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TNslpTaskItemDispatchServiceImpl implements ITNslpTaskItemDispatchService {

    private static final Logger log = LoggerFactory.getLogger(TNslpTaskItemDispatchServiceImpl.class);

    @Resource
    private TNslpTaskItemMapper nslpTaskItemMapper;

    /**
     * 收手线看这只钟：走表途中每办一行之前抬头看一回，过了 05:00 立刻收手。
     * 默认墙钟；单测拨到窗内或窗外某一刻。
     */
    private Supplier<Date> wallClock = new Supplier<Date>() {
        @Override
        public Date get() {
            return new Date();
        }
    };

    /** 单测拨表口（同包可见），生产不动它 */
    void setWallClock(Supplier<Date> clock) {
        this.wallClock = clock;
    }

    @Override
    public TaskDispatchResult runOnce(Date at) {
        Date press = at != null ? at : new Date();
        // 那一截开不开只认口径：窗外按下只答话，单上一行不动（连按几回，答话一字不差）
        if (!TaskDispatchRules.isOpen(press)) {
            return TaskDispatchResult.of(0, 0, 0);
        }

        // 只挑还搁着的行：记为已发出的、记为发不成的，都不要再碰
        List<TNslpTaskItem> waiting = this.nslpTaskItemMapper.selectList(
                new QueryWrapper<TNslpTaskItem>()
                        .eq("status", TaskDispatchRules.STATUS_WAIT)
                        .eq("del_flag", 0));
        // 届不届期只照口径那一条取法；某行归哪段只认本行写的，别去邻行借
        List<TNslpTaskItem> eligible = new ArrayList<TNslpTaskItem>();
        for (TNslpTaskItem r : waiting) {
            if (r != null && TaskDispatchRules.pickable(r.getStatus(), r.getDelFlag(),
                    r.getDueAt(), r.getAmount(), press)) {
                eligible.add(r);
            }
        }
        // 定额装满那档以哪一头作准只认口径：最届期的在前，同刻按主键，回回排得一样
        eligible.sort(new Comparator<TNslpTaskItem>() {
            @Override
            public int compare(TNslpTaskItem a, TNslpTaskItem b) {
                return TaskDispatchRules.comparePickOrder(
                        a.getDueAt(), a.getId(), b.getDueAt(), b.getId());
            }
        });

        // 三个数与逐行落下的记号同一次算出：就在这一遍里累计，不另起一轮逐行数
        int picked = 0;
        int sent = 0;
        int failed = 0;
        for (TNslpTaskItem row : eligible) {
            if (picked >= TaskDispatchRules.RUN_CAP) {
                // 定额装满即止：够不着的行什么也不标，照旧搁着，下一趟接着挑
                break;
            }
            if (!TaskDispatchRules.isOpen(this.wallClock.get())) {
                // 过了 05:00 立刻收手：剩下的行照旧搁着，下一趟接着挑
                break;
            }
            picked++;
            try {
                if (sendOne(row, press)) {
                    sent++;
                }
                // 并着的另一趟先落了记号（条件更新没咬着）：本趟不算发出也不算没成
            } catch (Exception e) {
                // 一行站不住：本行标一句失败原由，其余各行照常办完，整趟不中断
                markFailed(row, press, e);
                failed++;
            }
        }
        // 答话只引用这一趟给出的三个数；页面上那一屏只是转述
        return TaskDispatchResult.of(picked, sent, failed);
    }

    /**
     * 办成一行：两处记号同一句话落下——情形从搁着改成已发出，记要里补上这一回的
     * 发出那一刻与发给谁。只改前一处的，下一趟又会把它挑出来再发一回，不许再现。
     * 更新带「仍搁着」条件：同一行并着来两回，只成一回。
     *
     * @return true＝本趟发成了；false＝被并着的另一趟先落了记号
     */
    private boolean sendOne(TNslpTaskItem row, Date at) {
        String recipient = TaskDispatchRules.recipientOf(row.getCreateBy());
        if (recipient == null) {
            throw new IllegalStateException("行上没落着人名，不知发给谁");
        }
        UpdateWrapper<TNslpTaskItem> uw = new UpdateWrapper<TNslpTaskItem>();
        uw.eq("id", row.getId())
                .eq("status", TaskDispatchRules.STATUS_WAIT)
                .eq("del_flag", 0);
        uw.set("status", TaskDispatchRules.STATUS_DONE)
                .set("content", TaskDispatchRules.appendSendNote(row.getContent(), at, recipient))
                .set("update_by", TaskDispatchRules.ACTOR)
                .set("update_time", at);
        return this.nslpTaskItemMapper.update(null, uw) > 0;
    }

    /**
     * 标发不成：本行标一句失败原由，同样带「仍搁着」条件——若被并着的另一趟
     * 先发成了，不拿失败原由去盖人家的已发出。标这一句本身也落了空的，
     * 行照旧搁着，留待下一趟，不因此把整趟中断。
     */
    private void markFailed(TNslpTaskItem row, Date at, Exception e) {
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        try {
            UpdateWrapper<TNslpTaskItem> uw = new UpdateWrapper<TNslpTaskItem>();
            uw.eq("id", row.getId())
                    .eq("status", TaskDispatchRules.STATUS_WAIT)
                    .eq("del_flag", 0);
            uw.set("status", TaskDispatchRules.STATUS_FAIL)
                    .set("remark", TaskDispatchRules.failNote(reason))
                    .set("update_by", TaskDispatchRules.ACTOR)
                    .set("update_time", at);
            this.nslpTaskItemMapper.update(null, uw);
        } catch (Exception ex) {
            log.warn("安排单这一行标发不成也落了空，行照旧搁着留待下趟：id={}", row.getId(), ex);
        }
    }
}
