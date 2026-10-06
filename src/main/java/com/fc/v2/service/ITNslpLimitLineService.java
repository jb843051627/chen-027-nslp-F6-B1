package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TNslpLimitLine;
import com.fc.v2.model.custom.LimitLineVerdict;
import com.fc.v2.model.custom.LevelQuery;

/**
 * 功能区昼夜间限值线 Service接口（rule-eval 形状：规则求值，无增删改）
 *
 * 旧的按线代号取值位置（{@link #evaluate(String, BigDecimal, Date)} 等五个方法）
 * 形参与返回一概不动；按区册、按季度取线等新口径由新方法承接，不碰旧签名。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITNslpLimitLineService {

    /** 按主键查询规则（撤下线在库里仍是一行，照主键仍点得到） */
    TNslpLimitLine selectTNslpLimitLineById(Long id);

    /**
     * 单规则求值：按 ruleCode 在 at 时刻生效的版本判层（1..4）。
     * 无生效版本 / 输入越界 / 参数缺失 / 脏配置一律返回 0（回话「不归层」）。
     */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** 多规则求值：at 时刻可用规则中交班次序最高者的档位；无可用规则返回 0 */
    int evaluateTop(BigDecimal input, Date at);

    /** at 时刻可用规则（在位三件事齐全、配置干净；交班次序降序、ruleCode 字面靠后优先） */
    List<TNslpLimitLine> listAvailable(Date at);

    /** 单条规则在 at 时刻是否可用（批量导入/报表等旁路复用，口径与定位/列表一致） */
    boolean usable(Long id, Date at);

    /** at 时刻可用规则条数（列表页角标 / 看板现行清单） */
    int countAvailable(Date at);

    /**
     * 按线代号取值（新位置，承接旧 {@link #evaluate} 的同一口径）：
     * 返回完整回话（层、线、起用那一一日、对齐后读数、脏配置逐格提示）。
     */
    LimitLineVerdict resolve(String ruleCode, BigDecimal input, Date at);

    /**
     * 整表取优：at 时刻在位线中交班次序最高（并列看线代号字面靠后）者开口。
     * 同一刻同一处读数，与 {@link #resolve(String, BigDecimal, Date)} 问开口线同码，
     * 两答必须一致——实现上两条路选到同一条线后走同一个贴层出口。
     */
    LimitLineVerdict resolveTop(BigDecimal input, Date at);

    /**
     * 一批回算一次算完：库只照一面（同一快照），大屏昼段夜段折线、折线上点出来的数、
     * 后端逐条回算三处都拿这同一批回话，不许各算一遍。
     * query 带 ruleCode 走按线代号取值，不带走整表取优。
     */
    List<LimitLineVerdict> resolveBatch(List<LevelQuery> queries);

    /**
     * 按区册、按季度取线（新方法承接，不碰旧签名）：
     * 该区册点位上钉着的各线代号，在该年该季度内有过在位时刻的版本（头含尾不含相交即取），
     * 补算往年点位用这一份，不许拿此刻最新版去套旧账。
     */
    List<TNslpLimitLine> listByZoneQuarter(String zoneNo, int year, int quarter);

    /** 某条线的脏配置逐格提示；干净或查无此行返回 null */
    String configError(Long id);
}
