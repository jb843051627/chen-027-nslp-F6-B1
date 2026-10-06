package com.fc.v2.model.custom;

import java.math.BigDecimal;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一拨里头的一行（上交形状）。
 *
 * 行次、去处都不在这张形状上：行次由系统在本拨内排，去处由核收环节定，
 * 不留手填的格子。被退回的行过几天重报，用 refBatchNo＋refRowNo 指着当初那一行，
 * 行面上仍然显示当初那一个行次，不重新排队。
 *
 * @author fuce
 */
@ApiModel(value = "FlowRowSubmit", description = "核收上交行（一拨里的一行）")
public class FlowRowSubmit implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 报上来的点位码（是谁只认这个码，拿题名近似去凑不算） */
    @ApiModelProperty(value = "点位码")
    private String itemCode;

    /** 本行读数次数（与达标次数必须一起到；只到一个不代填、不当 0 收下） */
    @ApiModelProperty(value = "本行读数次数")
    private BigDecimal qty;

    /** 本行达标次数 */
    @ApiModelProperty(value = "本行达标次数")
    private BigDecimal chkNum;

    /** 来路（自动站直采／监测车补报两条来路） */
    @ApiModelProperty(value = "来路")
    private String putKind;

    /** 报文里那串读数（按本拨那一种切法切） */
    @ApiModelProperty(value = "报文读数串")
    private String payload;

    /** 报文里带的点位题名（只拿来与底册比对，行面上以底册带出的为准） */
    @ApiModelProperty(value = "报文点位题名")
    private String reportPointName;

    /** 报文里带的区册代号（只拿来与底册比对，行面上以底册带出的为准） */
    @ApiModelProperty(value = "报文区册代号")
    private String reportZoneNo;

    /** 重报所对的当初批次码（退回重报才带，可空） */
    @ApiModelProperty(value = "重报所对的当初批次码")
    private String refBatchNo;

    /** 重报所对的当初行次（退回重报才带，可空） */
    @ApiModelProperty(value = "重报所对的当初行次")
    private Integer refRowNo;

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getChkNum() {
        return chkNum;
    }

    public void setChkNum(BigDecimal chkNum) {
        this.chkNum = chkNum;
    }

    public String getPutKind() {
        return putKind;
    }

    public void setPutKind(String putKind) {
        this.putKind = putKind;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getReportPointName() {
        return reportPointName;
    }

    public void setReportPointName(String reportPointName) {
        this.reportPointName = reportPointName;
    }

    public String getReportZoneNo() {
        return reportZoneNo;
    }

    public void setReportZoneNo(String reportZoneNo) {
        this.reportZoneNo = reportZoneNo;
    }

    public String getRefBatchNo() {
        return refBatchNo;
    }

    public void setRefBatchNo(String refBatchNo) {
        this.refBatchNo = refBatchNo;
    }

    public Integer getRefRowNo() {
        return refRowNo;
    }

    public void setRefRowNo(Integer refRowNo) {
        this.refRowNo = refRowNo;
    }
}
