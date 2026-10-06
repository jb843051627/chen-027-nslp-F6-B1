package com.fc.v2.common.quartz.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fc.v2.model.custom.TaskDispatchResult;
import com.fc.v2.service.ITNslpTaskItemDispatchService;

/**
 * 复测与巡查安排单夜间走表的定时作业（Quartz 唯一挂点）。
 *
 * 调度只按固定钟点唤起：cron 落在 02:00–05:00 那一截之内（建议每天 02:00 一趟，
 * 必要时隔半刻一趟补足定额）。真正动不动手仍只认
 * {@link com.fc.v2.service.support.TaskDispatchRules#isOpen(java.util.Date)} 这一条
 * 口径——其余时段（补触发、手工在调度管理里点了「执行一次」、错配的 cron）唤起，
 * 走表服务只答话、不动单上任何一行，作业这里也不另开口子。
 *
 * 走表口径全在 dispatch 服务一处，本类只负责把它挂给 Quartz，不另起算法。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Component("nslpDispatchTask")
public class NslpDispatchTask {

    private static final Logger log = LoggerFactory.getLogger(NslpDispatchTask.class);

    @Autowired
    private ITNslpTaskItemDispatchService nslpTaskItemDispatchService;

    /**
     * 夜里走一趟。无参：Quartz 按 {@code nslpDispatchTask.runNightly()} 反射唤起，
     * 时刻由走表服务按墙钟自认，不从调度串里带时刻参数（带进来的时刻也不作凭）。
     */
    public void runNightly() {
        TaskDispatchResult result = nslpTaskItemDispatchService.runOnce(null);
        log.info("安排单夜间走表：挑中{}行、发出{}行、没成{}行｜{}",
                result.getPicked(), result.getSent(), result.getFailed(), result.getReply());
    }
}
