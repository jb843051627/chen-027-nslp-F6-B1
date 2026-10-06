package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 立件报文（手机端上来的那一张）。
 *
 * 手机端只管把字敲进去、把数回显出来；走到哪一关一律由后端数出来。
 * reportStage 即便带了关次，也只当参考，不写库、不定档。
 *
 * @author fuce
 */
@ApiModel(value = "CaseIntake", description = "办件立件报文（四样输入一样不能少）")
public class CaseIntake implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 办件件次；没填由后端按点位码配一个 */
    @ApiModelProperty(value = "办件件次（没填由后端配）")
    private String bizNo;

    /** 点位码 */
    @ApiModelProperty(value = "点位码")
    private String pointNo;

    /** 所属区册代号 */
    @ApiModelProperty(value = "所属区册代号")
    private String zoneNo;

    /** 处数 */
    @ApiModelProperty(value = "处数")
    private Integer occNum;

    /** 准备折算的那一个核查读数（敲进来是字，落库回显成数） */
    @ApiModelProperty(value = "准备折算的那一个核查读数")
    private String checkReading;

    /** 报文自带的关次：只作参考，关次一律以后端数出来的为准 */
    @ApiModelProperty(value = "报文自带关次（只作参考）")
    private Integer reportStage;

    /** 受件关头一句按语（可空） */
    @ApiModelProperty(value = "受件关头一句按语")
    private String note;

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getPointNo() {
        return pointNo;
    }

    public void setPointNo(String pointNo) {
        this.pointNo = pointNo;
    }

    public String getZoneNo() {
        return zoneNo;
    }

    public void setZoneNo(String zoneNo) {
        this.zoneNo = zoneNo;
    }

    public Integer getOccNum() {
        return occNum;
    }

    public void setOccNum(Integer occNum) {
        this.occNum = occNum;
    }

    public String getCheckReading() {
        return checkReading;
    }

    public void setCheckReading(String checkReading) {
        this.checkReading = checkReading;
    }

    public Integer getReportStage() {
        return reportStage;
    }

    public void setReportStage(Integer reportStage) {
        this.reportStage = reportStage;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
