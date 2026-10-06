package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TNslpPoint;
import com.fc.v2.model.custom.LedgerQuery;
import com.fc.v2.model.custom.LedgerView;

/**
 * 声管岗名册 Service接口。
 *
 * 三种摆法处数同一份底数、页大小只取区局配置、可见范围按区层切，都只问这一处。
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITNslpPointLedgerService {

    /** 一种摆法：byKind／byZone／byIntake；底数、分档、分页都由这一处给 */
    LedgerView view(LedgerQuery query, String view);

    /** 三种摆法一次给出：同一年度同一批行，三个 total 必须相等（同一份底数） */
    List<LedgerView> threeViews(LedgerQuery query);

    /**
     * 底数行（视图只呈现这些库里真实存在、归本视图、且落得了笔的行）。
     * 本层当季未建行时为空表，是合法结果；不拿邻层数据凑。
     */
    List<TNslpPoint> baseRows(LedgerQuery query);

    /** 建册、改线两处都指不出人的行：按没办完，不进在册底数，但单列给复核补笔，不删除 */
    List<TNslpPoint> listUnattributed(LedgerQuery query);

    /** 行上冗余区册全称与区册底对不齐的行：以区册为准，单列缺陷，不连带清行 */
    List<TNslpPoint> listZoneNameMismatches(Integer regYear);
}
