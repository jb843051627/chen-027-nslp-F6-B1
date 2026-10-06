package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TNslpFlowRow;
import com.fc.v2.model.custom.FlowBatchSubmit;
import com.fc.v2.model.custom.FlowReceipt;
import com.fc.v2.model.custom.FlowReconcile;

/**
 * 监测流水核收 Service接口（batch-process 形状：整批提交，无单条增删改）。
 *
 * 核收只做四个动作——点行数、看数据、定去处、出回执，四个动作一口气走完，
 * 中间不插人手。本拨进门后每一行的去向、同一批次码再次到达时的处置、拨首
 * 那三个数的出处，一概只认本接口给出的说法；自行落库与自行改写去向这两条
 * 路都不开在面上。历史册层的转入与取出同样只由这一处作答。
 *
 * @author fuce
 * @date 2026-10-04
 */
public interface ITNslpFlowRowService {

    /** 按主键回查单行 */
    TNslpFlowRow selectTNslpFlowRowById(Long id);

    /**
     * 整批上交：四个动作一口气走完，末了出一张回执。
     *
     * 点行数：顶只认抬头那一格（空/负/离谱按 300 认），点出来的行数连同写定的顶
     * 先落本拨流水；超顶整批未收，一行不落。看数据：两数必须一起到，点位只认
     * 点位码，报文按本拨那一种切法切，切不动的行单独标出不按住整拨。定去处：
     * 收下／退回，退回写明吃了哪一条。出回执：三个数从这一趟定完去处的行里
     * 一行一行数出来。
     *
     * 同一批次码第二回到达只照面：不把第一遍已经落地的行翻回未勾，也不另起
     * 一拨，回执照第一遍落地的流水原样给出（duplicateArrival 挂起）。
     */
    FlowReceipt submitBatch(FlowBatchSubmit submit);

    /**
     * 回执唯一出处。页面上那份只是转述，两处出自同一次计算；页面抢先摆出来
     * 的那一份不算，对不住时以这一处为准。批次码没落地过回 null。
     */
    FlowReceipt getReceipt(String batchNo);

    /** 本拨现行名单（按行次升序；历史册层里的不再列） */
    List<TNslpFlowRow> listRows(String batchNo);

    /** 本拨退回行明细（按行次升序）：哪一行吃了哪一条理由，一句一句问得出 */
    List<TNslpFlowRow> listErrors(String batchNo);

    /** 历史册层单独查（batchNo 留空查全部册层）；存量里先前入过账的一条不减 */
    List<TNslpFlowRow> listHistory(String batchNo);

    /**
     * 抽行复核：拨抬头存着的当初点数、流水里数出来的行数、收下退回两数互相
     * 印证；印不上的一行一行点名（写法不明的、已核收的拨里还空悬未勾的）。
     * 批次码没落地过回 null。
     */
    FlowReconcile reconcile(String batchNo);

    /**
     * 老规矩：去处一直空悬在未勾那一地的行，连着满两个年度转入历史册层，
     * 现行名单不再列它。只动未勾满两年的行——新拨的行进门就定了去处，
     * 走不到它跟前。返回这一趟转入的行数。
     */
    int transferToHistory(Date now);

    /** 历史册层取出（转回现行名单）；不在历史册层的行不动，返回 0 */
    int restoreFromHistory(Long id);
}
