package com.fc.v2.service;

import java.util.Date;

import com.fc.v2.model.custom.TaskDispatchResult;

/**
 * 复测与巡查安排单夜间走表 Service接口（dispatch-job 形状：夜里走一趟，无增删改查入口）。
 *
 * 触发把手只有 {@link #runOnce(Date)} 这一个，不留第二个：页面由
 * {@code NslpTaskItemDispatchController#run()} 唤起，夜里由 Quartz 作业
 * {@code nslpDispatchTask.runNightly()} 唤起，两处进的是同一套口径、同一个实现。
 * 早先那一套 scheduling-job 形状的老实现（窗口外也动单、空单越界、已处理重复发、
 * 恰好到点判否、单条异常整批中断）已经整根撤掉，要做变体请在本接口之外另立名目，
 * 不拿这条本体开刀。
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITNslpTaskItemDispatchService {

    /**
     * 走一趟：只认 02:00 到 05:00 那一截，窗外按下只答话、不动单上任何一行。
     * 回话里那三个数（挑中、发出、没成）与逐行落下的记号同一次算出。
     */
    TaskDispatchResult runOnce(Date at);
}
