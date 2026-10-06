package com.fc.v2.model.custom;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 一条点位建行／改线的进门形状。
 *
 * 建档岗一条一条建、复测业务核收之后带出建行，两条来路都用这同一个形状、走同一套
 * 五道筛与数量算法，界面上不长第二种样子。数量两栏用原字串接进来，是不是整数、
 * 空没空，由口径处逐栏点名（绑定成 Integer 就把「不是整数」挡在服务外头了）。
 *
 * @author fuce
 * @date 2026-10-06
 */
@ApiModel(value = "PointArchiveSubmit", description = "点位建行／改线提交")
public class PointArchiveSubmit implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键：改线时指到那一行自己（同码唯一那道筛据此放过自身）；建行留空 */
    @ApiModelProperty(value = "主键（改线时给，建行留空）")
    private Long id;

    /** 点位码（随报送材料进来，系统不另发第二串，也不核对构成规矩） */
    @ApiModelProperty(value = "点位码", required = true)
    private String pointNo;

    /** 区册代号（挂靠只认全等） */
    @ApiModelProperty(value = "区册代号", required = true)
    private String zoneNo;

    /** 年度（同年度同区册同码只许一条） */
    @ApiModelProperty(value = "年度", required = true)
    private Integer regYear;

    /** 核定应测次数（原字串，须是整数） */
    @ApiModelProperty(value = "核定应测次数（整数）", required = true)
    private String needNum;

    /** 已核验达标的次数（原字串，空着按 0 认） */
    @ApiModelProperty(value = "已核验达标的次数（整数，空按0）")
    private String gotNum;

    /** 递材料的维护单位 */
    @ApiModelProperty(value = "维护单位")
    private String maintainUnit;

    /** 点位题名与依附声源（空着，文书那一道会点名为缺项） */
    @ApiModelProperty(value = "点位题名与依附声源")
    private String content;

    /** 来路（建档岗／复测带出） */
    @ApiModelProperty(value = "来路")
    private String srcKind;

    /** 落笔人（谁建的册／谁改的行，复核点得出人） */
    @ApiModelProperty(value = "落笔人", required = true)
    private String operator;

    /** 备注 */
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getRegYear() {
        return regYear;
    }

    public void setRegYear(Integer regYear) {
        this.regYear = regYear;
    }

    public String getNeedNum() {
        return needNum;
    }

    public void setNeedNum(String needNum) {
        this.needNum = needNum;
    }

    public String getGotNum() {
        return gotNum;
    }

    public void setGotNum(String gotNum) {
        this.gotNum = gotNum;
    }

    public String getMaintainUnit() {
        return maintainUnit;
    }

    public void setMaintainUnit(String maintainUnit) {
        this.maintainUnit = maintainUnit;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSrcKind() {
        return srcKind;
    }

    public void setSrcKind(String srcKind) {
        this.srcKind = srcKind;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
