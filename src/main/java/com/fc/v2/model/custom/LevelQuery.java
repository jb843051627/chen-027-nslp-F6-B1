package com.fc.v2.model.custom;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一次判层问话：问哪条线（可空，空则整表取优）、拿什么折算读数、问的是哪一刻。
 *
 * @author fuce
 */
@ApiModel(value = "LevelQuery", description = "判层问话")
public class LevelQuery implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 点位码（大屏点哪个点带哪个，仅用于回话对账，可空） */
    @ApiModelProperty(value = "点位码")
    private String pointNo;

    /** 线代号；带值走按线代号取值，留空走整表取优 */
    @ApiModelProperty(value = "线代号，空为整表取优")
    private String ruleCode;

    /** 段别（昼段/夜段）：大屏两截折线各问各的，只在 obj_kind 相同的线里挑；空为不限段 */
    @ApiModelProperty(value = "段别：昼段/夜段，空为不限")
    private String objKind;

    /** 折算读数（原始声级按省里公式扣掉背景与本底后的数，量程 30..120） */
    @ApiModelProperty(value = "折算读数")
    private BigDecimal input;

    /** 询问时刻；补算往年点位传那一年当时的时刻 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "询问时刻")
    private Date at;

    public LevelQuery() {
    }

    public LevelQuery(String ruleCode, BigDecimal input, Date at) {
        this.ruleCode = ruleCode;
        this.input = input;
        this.at = at;
    }

    public LevelQuery(String pointNo, String ruleCode, BigDecimal input, Date at) {
        this.pointNo = pointNo;
        this.ruleCode = ruleCode;
        this.input = input;
        this.at = at;
    }

    public String getObjKind() {
        return objKind;
    }

    public void setObjKind(String objKind) {
        this.objKind = objKind;
    }

    public String getPointNo() {
        return pointNo;
    }

    public void setPointNo(String pointNo) {
        this.pointNo = pointNo;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public BigDecimal getInput() {
        return input;
    }

    public void setInput(BigDecimal input) {
        this.input = input;
    }

    public Date getAt() {
        return at;
    }

    public void setAt(Date at) {
        this.at = at;
    }
}
