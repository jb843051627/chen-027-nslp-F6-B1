package com.fc.v2.model.custom;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一次上交的一拨（核收的进门形状）。
 *
 * 一拨一次交齐，不收零散逐行；拨不许拆着交，行不许挪到别拨去。
 * 行数的顶只认抬头那一格（capNum），别处不设第二个来源。
 *
 * @author fuce
 */
@ApiModel(value = "FlowBatchSubmit", description = "核收上交拨（一次交齐）")
public class FlowBatchSubmit implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 监测流水批次码（一拨一码；同一码第二回到达只照面） */
    @ApiModelProperty(value = "监测流水批次码", required = true)
    private String batchNo;

    /** 抬头那一格：本拨容多少行的顶。空着、填负、填得离谱，都按 300 认 */
    @ApiModelProperty(value = "行数顶（抬头那一格）")
    private Integer capNum;

    /** 本拨切法：DELIMITER按分隔符／FIXED按定宽（也认「分隔符／定宽」报法），一拨只许一种 */
    @ApiModelProperty(value = "本拨切法")
    private String parseKind;

    /** 定宽切法的宽度（切法为 FIXED 才用得着） */
    @ApiModelProperty(value = "定宽宽度")
    private Integer fixWidth;

    /** 本拨的若干行，一次交齐 */
    @ApiModelProperty(value = "本拨各行")
    private List<FlowRowSubmit> rows;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getCapNum() {
        return capNum;
    }

    public void setCapNum(Integer capNum) {
        this.capNum = capNum;
    }

    public String getParseKind() {
        return parseKind;
    }

    public void setParseKind(String parseKind) {
        this.parseKind = parseKind;
    }

    public Integer getFixWidth() {
        return fixWidth;
    }

    public void setFixWidth(Integer fixWidth) {
        this.fixWidth = fixWidth;
    }

    public List<FlowRowSubmit> getRows() {
        return rows;
    }

    public void setRows(List<FlowRowSubmit> rows) {
        this.rows = rows;
    }
}
