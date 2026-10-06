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
 * 点位行变更履历对象 t_nslp_point_change
 *
 * 册面主档改口时，在点位行身后追加一条：这一笔由谁落、依据哪一张单、
 * 从哪一档改到哪一档、发生在哪一刻。履历跟主档的改动出自同一回处理、
 * 基于同一次计算；两处对不住时要能指名报出对应单据。
 *
 * @author fuce
 * @date 2026-10-06
 */
@TableName("t_nslp_point_change")
@ApiModel(value = "TNslpPointChange", description = "点位行变更履历")
public class TNslpPointChange implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 被改口的点位行 t_nslp_point.id */
    @TableField("point_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "被改口的点位行")
    private Long pointId;

    /** 点位码（改口时钉住） */
    @TableField("point_no")
    @ApiModelProperty(value = "点位码")
    private String pointNo;

    /** 改的是哪一档（permit_state 许可档） */
    @TableField("change_field")
    @ApiModelProperty(value = "改的是哪一档")
    private String changeField;

    /** 改前档 */
    @TableField("from_state")
    @ApiModelProperty(value = "改前档")
    private Integer fromState;

    /** 改后档 */
    @TableField("to_state")
    @ApiModelProperty(value = "改后档")
    private Integer toState;

    /** 依据哪一张签批单 t_nslp_permit_bill.id */
    @TableField("bill_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "依据哪一张签批单")
    private Long billId;

    /** 依据的签批单单号（对不住时报得出对应单据） */
    @TableField("bill_no")
    @ApiModelProperty(value = "依据的签批单单号")
    private String billNo;

    /** 这一笔由谁落 */
    @TableField("change_by")
    @ApiModelProperty(value = "这一笔由谁落")
    private String changeBy;

    /** 改口那一刻（与主档改动同一刻） */
    @TableField("change_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "改口那一刻")
    private Date changeTime;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getChangeField() {
        return changeField;
    }

    public void setChangeField(String changeField) {
        this.changeField = changeField;
    }

    public Integer getFromState() {
        return fromState;
    }

    public void setFromState(Integer fromState) {
        this.fromState = fromState;
    }

    public Integer getToState() {
        return toState;
    }

    public void setToState(Integer toState) {
        this.toState = toState;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public String getChangeBy() {
        return changeBy;
    }

    public void setChangeBy(String changeBy) {
        this.changeBy = changeBy;
    }

    public Date getChangeTime() {
        return changeTime;
    }

    public void setChangeTime(Date changeTime) {
        this.changeTime = changeTime;
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
}
