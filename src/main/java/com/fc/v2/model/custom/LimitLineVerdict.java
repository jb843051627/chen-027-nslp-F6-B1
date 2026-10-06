package com.fc.v2.model.custom;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fc.v2.model.auto.TNslpLimitLine;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一次限值线判层的回话。
 *
 * 大屏昼段夜段折线、折线上点出来的数、后端逐条回算，三处出自同一次计算：
 * 都拿本对象说话，不许各算一遍。按线代号取值与整表取优两个位置问同一刻同一处点位，
 * 两答必须也是同一个本对象。
 *
 * @author fuce
 */
@ApiModel(value = "LimitLineVerdict", description = "限值线判层回话（一处计算，多处复用）")
public class LimitLineVerdict implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 贴上层时钉住的线；不归层为 null */
    @ApiModelProperty(value = "开口的那一条线（不归层为空）")
    private TNslpLimitLine line;

    /** 开口线的线代号，不归层时为 null（供点位行清空白两格用） */
    @ApiModelProperty(value = "线代号，不归层为空")
    private String ruleCode;

    /** 起用那一一日，不归层时为 null（日后追得出当年照的是哪一版线） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "起用那一一日，不归层为空")
    private Date effStart;

    /** 判得的层 1..4；贴不上 / 无在位线 / 越界 / 脏配置一律 0 */
    @ApiModelProperty(value = "层 1..4，不归层为 0")
    private int level;

    /** 回话文字：1..4 出「第N层」，其余只有「不归层」三个字 */
    @ApiModelProperty(value = "回话：第1层..第4层 / 不归层")
    private String answer;

    /** 对齐之后真正拿去贴层的折算读数（一位小数逢半进一） */
    @ApiModelProperty(value = "对齐后的折算读数")
    private BigDecimal alignedReading;

    /** 脏配置/缺档的逐格提示；线干净为 null。注意：不写「数值不对」这种糊涂话 */
    @ApiModelProperty(value = "脏配置逐格提示，干净为空")
    private String configError;

    /** 询问时刻 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "询问时刻")
    private Date at;

    /** 段别（昼段/夜段），随线带出，供大屏两截折线取用；可空 */
    @ApiModelProperty(value = "段别：昼段/夜段")
    private String objKind;

    public static LimitLineVerdict notLevel(Date at, BigDecimal alignedReading, String configError) {
        LimitLineVerdict v = new LimitLineVerdict();
        v.level = 0;
        v.answer = "不归层";
        v.at = at;
        v.alignedReading = alignedReading;
        v.configError = configError;
        return v;
    }

    public static LimitLineVerdict of(TNslpLimitLine line, int level, Date at, BigDecimal alignedReading) {
        LimitLineVerdict v = new LimitLineVerdict();
        v.line = line;
        v.ruleCode = line == null ? null : line.getRuleCode();
        v.effStart = line == null ? null : line.getEffStart();
        v.level = level;
        v.answer = com.fc.v2.service.support.LimitLineRules.answerOf(level);
        v.at = at;
        v.alignedReading = alignedReading;
        v.objKind = line == null ? null : line.getObjKind();
        return v;
    }

    public boolean isLeveled() {
        return level >= 1 && level <= 4;
    }

    public TNslpLimitLine getLine() {
        return line;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Date getEffStart() {
        return effStart;
    }

    public int getLevel() {
        return level;
    }

    public String getAnswer() {
        return answer;
    }

    public BigDecimal getAlignedReading() {
        return alignedReading;
    }

    public String getConfigError() {
        return configError;
    }

    public Date getAt() {
        return at;
    }

    public String getObjKind() {
        return objKind;
    }
}
