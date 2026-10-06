package com.fc.v2.model.auto;

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
 * 办件流转留痕对象 t_nslp_case_trail
 *
 * 每一关那句按语连同提交那一刻，按时序压在办件身后；挪前、收回、封住、
 * 撤回、并走、作废也各留一笔。日后追经过只取这一串。
 *
 * @author fuce
 * @date 2026-10-05
 */
@TableName("t_nslp_case_trail")
@ApiModel(value = "TNslpCaseTrail", description = "办件流转留痕")
public class TNslpCaseTrail implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所随办件 */
    @TableField("case_id")
    @ApiModelProperty(value = "所随办件")
    private Long caseId;

    /** 落笔那一刻办件所处关次 0..3 */
    @TableField("stage")
    @ApiModelProperty(value = "落笔那一刻所处关次")
    private Integer stage;

    /** 动作 受件立条/挪前/收回/按语/封住/反映人撤回/区册并走/作废 */
    @TableField("action")
    @ApiModelProperty(value = "动作")
    private String action;

    /** 这一笔留下的话（按语原句或动作按语） */
    @TableField("note")
    @ApiModelProperty(value = "这一笔留下的话")
    private String note;

    /** 提交那一刻（同刻两笔各记各的先后） */
    @TableField("trail_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "提交那一刻")
    private Date trailTime;

    /** 同刻先后次序（trail_time 并列时以本列断先后） */
    @TableField("seq")
    @ApiModelProperty(value = "同刻先后次序")
    private Integer seq;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField("create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Date getTrailTime() {
        return trailTime;
    }

    public void setTrailTime(Date trailTime) {
        this.trailTime = trailTime;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
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
