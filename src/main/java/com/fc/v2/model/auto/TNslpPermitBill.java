package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 夜间连续施工作业许可签批单对象 t_nslp_permit_bill
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_nslp_permit_bill")
@ApiModel(value = "TNslpPermitBill", description = "夜间连续施工作业许可签批单")
public class TNslpPermitBill implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 夜间连续施工作业许可签批单 */
    @TableField("bill_no")
    @ApiModelProperty(value = "夜间连续施工作业许可签批单")
    private String billNo;

    /** 带出本单的点位底册行 t_nslp_point.id（单只许从点位底册记录里带出来） */
    @TableField("point_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "带出本单的点位底册行")
    private Long pointId;

    /** 点位码（以点位底册带出的为准） */
    @TableField("point_no")
    @ApiModelProperty(value = "点位码（以点位底册带出的为准）")
    private String pointNo;

    /** 区册代号（以点位底册带出的为准） */
    @TableField("zone_no")
    @ApiModelProperty(value = "区册代号（以点位底册带出的为准）")
    private String zoneNo;

    /** 当前所在关 0..2（窗口受理/区局核定/市局备案），凭据在签批留痕那一串，本行只回放后写回 */
    @TableField("node_no")
    @ApiModelProperty(value = "当前所在关 0..2（窗口受理/区局核定/市局备案）")
    private Integer nodeNo;

    /** 本关算齐办法 0一名即过 1两条一并认可 2先落为准（三关各用各的，只此一处点出） */
    @TableField("sign_mode")
    @ApiModelProperty(value = "本关算齐办法 0一名即过 1两条一并认可 2先落为准")
    private Integer signMode;

    /** 本关应签人数 */
    @TableField("need_count")
    @ApiModelProperty(value = "本关应签人数")
    private Integer needCount;

    /** 本关已到人数（只作逐笔累加后的回显，任何角色都没有改它的位置） */
    @TableField("sign_count")
    @ApiModelProperty(value = "本关已到人数")
    private Integer signCount;

    /** 当下关本轮轮次（首次到达为1，压回重走一轮加一） */
    @TableField("cur_round")
    @ApiModelProperty(value = "当下关本轮轮次")
    private Integer curRound;

    /** 签批情形 0在签 1已点齐 2止住（一方落不可即止住；压回退关仍是在签） */
    @TableField("status")
    @ApiModelProperty(value = "签批情形 0在签 1已点齐 2止住")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
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

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getSignMode() {
        return signMode;
    }

    public void setSignMode(Integer signMode) {
        this.signMode = signMode;
    }

    public Integer getNeedCount() {
        return needCount;
    }

    public void setNeedCount(Integer needCount) {
        this.needCount = needCount;
    }

    public Integer getSignCount() {
        return signCount;
    }

    public void setSignCount(Integer signCount) {
        this.signCount = signCount;
    }

    public Integer getCurRound() {
        return curRound;
    }

    public void setCurRound(Integer curRound) {
        this.curRound = curRound;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
