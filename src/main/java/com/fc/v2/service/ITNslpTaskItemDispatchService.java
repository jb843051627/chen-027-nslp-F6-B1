package com.fc.v2.service;

import java.util.Date;

import com.fc.v2.model.custom.TaskDispatchResult;

/**
 * 复测与巡查安排单夜间走表 Service接口（dispatch-job 形状：夜里走一趟，无增删改查入口）。
 *
 * 触发把手只有 {@link #runOnce(Date)} 这一个，不留第二个。原先那一套
 * （{@link ITNslpTaskItemService}）原地不动；要「一趟只办一个区册」之类的变体，
 * 请在本接口之外另立名目去担，不拿这条本体开刀。
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
