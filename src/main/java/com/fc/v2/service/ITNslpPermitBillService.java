package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TNslpPermitBill;
import com.fc.v2.model.custom.PermitLedger;
import com.fc.v2.model.custom.PermitOpenSubmit;
import com.fc.v2.model.custom.PermitSignSubmit;
import com.fc.v2.model.custom.PermitTrace;

/**
 * 夜间连续施工作业许可签批单 Service接口。
 *
 * 只有四个入口：从点位底册立单、落笔（认可／不认可都走这一个口）、压回一关、
 * 查（回看／受理簿对账／在签张数）。不另起单、不改旧笔、不追补签署。
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITNslpPermitBillService {

    /** 按主键回查单据 */
    TNslpPermitBill selectTNslpPermitBillById(Long id);

    /** 台账列单 */
    List<TNslpPermitBill> selectList(QueryWrapper<TNslpPermitBill> queryWrapper);

    /** 从点位底册记录立单：一处点位、一次夜间连续施工只摊上一张在签的单 */
    TNslpPermitBill open(PermitOpenSubmit submit);

    /**
     * 一次落笔的唯一入口。当前关、轮次、已到几人全由服务端从留痕点出来。
     * 收下并落账回 true；同一人同关同轮第二句等不采信的情形回 false（不落一笔）。
     */
    boolean sign(PermitSignSubmit submit);

    /** 压回：只退一关，抹去的也只是离开那一关本轮的笔，更早各关旧笔不动 */
    TNslpPermitBill rollback(Long id, String operator, String note);

    /** 追溯：逐笔连同落单时刻按时序，当前关与已到几人由这一串放出来 */
    PermitTrace trace(Long id);

    /** 受理簿对账：倒着数回头一关，笔数一笔一笔合得上，逐段也自洽 */
    PermitLedger ledger(Long id, Date day);

    /** 在签张数：随立单加一、随封存（点齐／止住）减一，与逐段累算同源 */
    int countOpen();
}
