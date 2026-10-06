package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TNslpCaseFlow;
import com.fc.v2.model.custom.CaseIntake;
import com.fc.v2.model.custom.CaseLedger;
import com.fc.v2.model.custom.CaseSeal;
import com.fc.v2.model.custom.CaseTrace;

/**
 * 噪声投诉与超标处置办件流转单 Service接口（state-machine 形状：单据流转）。
 *
 * 一处点位一条，从受件起、到复查收尾止；立・挪・封・捋四件事只此一处办。
 * 关次由后端数，报文自带的只当参考；改档与留痕同一事务。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITNslpCaseFlowService {

    /** 按主键查询单据 */
    TNslpCaseFlow selectTNslpCaseFlowById(Long id);

    /** 列表查询（流转台账；封住、作废的照常露脸，只是没有可动手的把手） */
    List<TNslpCaseFlow> selectTNslpCaseFlowList(QueryWrapper<TNslpCaseFlow> queryWrapper);

    /**
     * 立·受件立条。四样输入一样不能少，少一样起不起来，缺哪一样点哪一样的名。
     * 一处点位已有没封住的件（在办的、中途歇下的都算），第二条立不起来。
     */
    TNslpCaseFlow intake(CaseIntake intake);

    /**
     * 立·正式立起来之前那一小会儿撤回去：不进主档，也不占「没封住」名额。
     * 已经立进主档的件不走这条道（那是反映人撤回 {@link #withdraw}）。
     */
    boolean cancelBeforeIntake(String pointNo, String reason);

    /** 挪·往前跨紧邻的下一关；越关不生效、门槛不满足不生效，整句返回，被拒返回 null */
    TNslpCaseFlow advance(Long id, String remark);

    /** 挪·收回一关；头一关没什么可再收。被收回关那句按语已留在追溯记录末尾 */
    TNslpCaseFlow rollback(Long id, String remark);

    /** 挪·本关头一句按语：同一关第二句盖不上去也不另存，返回 false；封住后不接 */
    boolean note(Long id, String note);

    /** 责令关核收落笔；记成「可」，复查收尾才开得了工 */
    boolean recordOrderAccept(Long id, String acceptText);

    /** 封·复查收尾封关：两名见证岗各落一笔＋复测凭证一串，两样齐了才封上 */
    TNslpCaseFlow seal(CaseSeal seal);

    /** 封·反映人受件之后撤回去：视同收口，不必再往前挪 */
    TNslpCaseFlow withdraw(Long id, String reason);

    /** 封·这一处所属区册从区册底里并走了：视同收口；本点位在册在办件一并收口 */
    int mergeZone(String zoneNo, String reason);

    /** 作废只从最后一关（复查收尾）发起；在办的件作不了废 */
    boolean voidCase(Long id, String reason);

    /** 捋·一条办件身后按时序压着的那一串（当前档由这一串当场放出来） */
    CaseTrace trace(Long id);

    /** 捋·受理簿对账：收回再从头捋，笔数跟受理簿一笔一笔合，逐关也自洽 */
    CaseLedger ledger(Long id, Date day);

    /** 没封住的件数：起一条加一、封一条减一；页面那个数只认这一处查出来的 */
    int countOpen();
}
