package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TNslpTaskItemMapper;
import com.fc.v2.model.auto.TNslpTaskItem;
import com.fc.v2.model.custom.TaskDispatchResult;
import com.fc.v2.service.ITNslpTaskItemDispatchService;
import com.fc.v2.service.ITNslpTaskItemService;
import com.fc.v2.service.support.TaskDispatchRules;

/**
 * 复测与巡查条目 Service业务层处理（scheduling-job 形状：周期执行）。
 *
 * 早先这一类自带一套挑法，与夜里走表那一套（{@link ITNslpTaskItemDispatchService}）
 * 各算各的，闹出一串对不上：别的时段触发也照动数据、已发出的行回头又参与、恰好到期的
 * 结论两样、空单一头撞在取第一条上、只点数不落库导致状态永不翻新。如今本类不再另起算法——
 * 窗口、届期、提前、只挑搁着的、办成与没成的记号，一概只问 {@link TaskDispatchRules}
 * 与夜间走表那一处，两处同一条路径、同一套口径。
 *
 * 存量条目由老系统搬来时情形格常空着（列默认 NULL），与显式落「搁着」一样当没办过，
 * 新逻辑照收；走成一回后情形落成已发出，第二回再不参与（幂等由走表那处的条件更新担）。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TNslpTaskItemServiceImpl implements ITNslpTaskItemService {

    @javax.annotation.Resource
    private TNslpTaskItemMapper nslpTaskItemMapper;

    @javax.annotation.Resource
    private ITNslpTaskItemDispatchService nslpTaskItemDispatchService;

    @Override
    public TNslpTaskItem selectTNslpTaskItemById(Long id) {
        return this.nslpTaskItemMapper.selectById(id);
    }

    /**
     * 该时刻可处理的条目：只在夜间走表那一截（02:00–05:00）内答话，窗外一行不给；
     * 只收还搁着的（情形格空着的存量行也算），届不届期、许提前几日全照
     * {@link TaskDispatchRules#pickable} 这一条取法，与夜间走表挑单同源。
     * 空单是合法结果，回空表，绝不伸手去取第一条。
     */
    @Override
    public List<TNslpTaskItem> listDue(Date at) {
        if (at == null || !TaskDispatchRules.isOpen(at)) {
            return new ArrayList<TNslpTaskItem>();
        }
        List<TNslpTaskItem> waiting = this.nslpTaskItemMapper.selectList(
                new QueryWrapper<TNslpTaskItem>()
                        .eq("del_flag", 0)
                        .and(w -> w.eq("status", TaskDispatchRules.STATUS_WAIT)
                                .or().isNull("status")));
        List<TNslpTaskItem> due = new ArrayList<TNslpTaskItem>();
        for (TNslpTaskItem r : waiting) {
            if (r != null && TaskDispatchRules.pickable(r.getStatus(), r.getDelFlag(),
                    r.getDueAt(), r.getAmount(), at)) {
                due.add(r);
            }
        }
        // 先后次序也只认口径：最届期的在前，同刻按主键，回回排得一样
        Collections.sort(due, new Comparator<TNslpTaskItem>() {
            @Override
            public int compare(TNslpTaskItem a, TNslpTaskItem b) {
                return TaskDispatchRules.comparePickOrder(
                        a.getDueAt(), a.getId(), b.getDueAt(), b.getId());
            }
        });
        return due;
    }

    /**
     * 执行一次：直接走夜间走表那唯一一趟，窗口、届期、幂等、逐行容错都在那处担着，
     * 本处不另做一遍。回话只取「发出几行」作成功条数；某一行没成只标本行，其余照常办完，
     * 一条站不住不会把整趟中断，也不会把已经发出的行翻回去。
     */
    @Override
    public int runOnce(Date at) {
        TaskDispatchResult result = this.nslpTaskItemDispatchService.runOnce(at);
        return result.getSent();
    }
}
