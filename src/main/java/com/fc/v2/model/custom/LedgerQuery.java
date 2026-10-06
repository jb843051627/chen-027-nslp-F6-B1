package com.fc.v2.model.custom;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 名册查问的进门形状。
 *
 * 三个查问位置：点位码、区册代号、区层类别，前缀匹配与全等匹配都认（给了值就按
 * 「全等或以它打头」收）。看得见的范围按区层切，由值守性质与本层/划归区册定。
 *
 * @author fuce
 * @date 2026-10-06
 */
@ApiModel(value = "LedgerQuery", description = "名册查问")
public class LedgerQuery implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 值守性质：本层值守岗 */
    public static final String SCOPE_LAYER = "LAYER";
    /** 值守性质：区局岗（四层都看得见，但限划归它的区册） */
    public static final String SCOPE_BUREAU = "BUREAU";

    /** 年度（只摆这一年度入的行；空则不按年度切） */
    @ApiModelProperty(value = "年度")
    private Integer regYear;

    /** 点位码查问值（全等或前缀） */
    @ApiModelProperty(value = "点位码（全等/前缀）")
    private String pointNo;

    /** 区册代号查问值（全等或前缀） */
    @ApiModelProperty(value = "区册代号（全等/前缀）")
    private String zoneNo;

    /** 区层类别查问值（全等或前缀：甲层/乙层/丙层/丁层） */
    @ApiModelProperty(value = "区层类别（全等/前缀）")
    private String zoneKind;

    /** 值守性质 LAYER／BUREAU */
    @ApiModelProperty(value = "值守性质 LAYER/BUREAU", required = true)
    private String scope;

    /** 本层值守岗的那一层（区层类别）；scope=LAYER 才用 */
    @ApiModelProperty(value = "本层类别")
    private String viewerLayer;

    /** 区局岗划归它的区册代号全集；scope=BUREAU 用，空着一层也看不见（不拿邻层凑） */
    @ApiModelProperty(value = "划归区册代号全集")
    private List<String> assignedZoneNos;

    /** 第几页（从 1 起） */
    @ApiModelProperty(value = "页码（从1起）")
    private Integer pageNum;

    public Integer getRegYear() {
        return regYear;
    }

    public void setRegYear(Integer regYear) {
        this.regYear = regYear;
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

    public String getZoneKind() {
        return zoneKind;
    }

    public void setZoneKind(String zoneKind) {
        this.zoneKind = zoneKind;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getViewerLayer() {
        return viewerLayer;
    }

    public void setViewerLayer(String viewerLayer) {
        this.viewerLayer = viewerLayer;
    }

    public List<String> getAssignedZoneNos() {
        return assignedZoneNos;
    }

    public void setAssignedZoneNos(List<String> assignedZoneNos) {
        this.assignedZoneNos = assignedZoneNos;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }
}
